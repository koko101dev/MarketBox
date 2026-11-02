package com.koko.marketbox.event;

import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.Config;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.packet.SyncLeaderboardPacket;
import com.koko.marketbox.networking.packet.SyncAdminStatusPacket;
import com.koko.marketbox.networking.packet.SyncDealPricesPacket;
import com.koko.marketbox.networking.packet.SyncMarketListingsPacket;
import com.koko.marketbox.networking.packet.SyncPlayerBalancePacket;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家相关事件：登录补算、离线结算、利息结算、排行榜变化感知。
 * 死亡重生时的数据继承由 Attachment 的 copyOnDeath 保证，无需再监听克隆事件。
 */
public final class PlayerEvents {
    private static final Map<UUID, Double> LAST_SYNCED_TOTAL = new ConcurrentHashMap<>();
    /** 正在查看排行榜界面的玩家：只给他们推送榜单，避免全服广播 */
    private static final java.util.Set<UUID> LEADERBOARD_SUBSCRIBERS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private PlayerEvents() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> handleJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            com.koko.marketbox.networking.PacketRateLimiter.forget(handler.getPlayer().getUUID());
            LAST_SYNCED_TOTAL.remove(handler.getPlayer().getUUID());
            LEADERBOARD_SUBSCRIBERS.remove(handler.getPlayer().getUUID());
            ServerEvents.markLeaderboardDirty();
        });

        // Fabric 无 PlayerTickEvent，统一在服务端 tick 中遍历在线玩家
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(player);
            }
        });
    }

    private static void handleJoin(ServerPlayer player) {
        // 诊断用：确认玩家加入时从存档中恢复到了多少余额
        if (com.koko.marketbox.MarketBox.LOGGER.isDebugEnabled()) {
            com.koko.marketbox.MarketBox.LOGGER.debug("[MarketBox] {} joined, balance={} cents",
                    player.getName().getString(), PlayerBalanceAttachment.get(player).getBalanceCents());
        }

        // 同步管理员权限，客户端据此决定设置界面显示哪些项
        ModMessages.sendToPlayer(player, new SyncAdminStatusPacket(player.hasPermissions(2)));

        // 管理员额外收到上架黑名单，用于黑名单界面
        if (player.hasPermissions(2)) {
            ModMessages.sendToPlayer(player,
                    new com.koko.marketbox.networking.packet.SyncBlacklistPacket(Config.getListingBlacklist()));
        }

        checkAndApplyInterest(player);

        MarketManager manager = MarketManager.get(player.serverLevel());
        double pendingPayment = manager.claimPendingPayment(player.getUUID());
        if (pendingPayment > 0) {
            PlayerBalanceAttachment.get(player)
                    .addBalance(pendingPayment, "gui.marketbox.transaction.market_result");
            player.sendSystemMessage(Component.translatable("gui.marketbox.market.pending_payment_received"));
        }

        if (!manager.getReturnedItems(player.getUUID()).isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.marketbox.market.returned_items_notification"));
        }

        syncPlayerBalance(player);
        ServerEvents.markLeaderboardDirty();

        sendFullMarketSync(player, manager);
        ModMessages.sendToPlayer(player, new SyncDealPricesPacket(manager.getDealPrices()));
        // 玩家加入时同步最新价格表
    }

    private static void tickPlayer(ServerPlayer player) {
        // 每秒检查一次利息
        if (player.tickCount % 20 == 0) {
            if (checkAndApplyInterest(player)) {
                syncPlayerBalance(player);
                ServerEvents.markLeaderboardDirty();
            }
        }

        // 每 2 秒比对一次总资产，变化才标记排行榜
        if (player.tickCount % 40 == 0) {
            PlayerBalance balance = PlayerBalanceAttachment.get(player);
            double currentTotal = balance.getBalance() + MoneyUtils.getInventoryMoneyTotal(player);
            Double lastTotal = LAST_SYNCED_TOTAL.get(player.getUUID());

            if (lastTotal == null || Math.abs(currentTotal - lastTotal) > 0.01) {
                LAST_SYNCED_TOTAL.put(player.getUUID(), currentTotal);
                ServerEvents.markLeaderboardDirty();
            }
        }
    }

    private static boolean checkAndApplyInterest(ServerPlayer player) {
        PlayerBalance balance = PlayerBalanceAttachment.get(player);
        double interestAdded = balance.applyInterest(Config.getInterestRate(), Config.getInterestIntervalSeconds());
        com.koko.marketbox.util.EconomyStats.addInterest(interestAdded);
        if (interestAdded > 0) {
            player.sendSystemMessage(Component.translatable("gui.marketbox.interest_applied",
                    String.format("%.2f", interestAdded)));
            return true;
        }
        return false;
    }

    public static void syncPlayerBalance(ServerPlayer player) {
        PlayerBalance balance = PlayerBalanceAttachment.get(player);
        MarketManager manager = MarketManager.get(player.serverLevel());
        List<ItemStack> returnedItems = manager.getReturnedItems(player.getUUID());

        ModMessages.sendToPlayer(player, new SyncPlayerBalancePacket(
                balance.getBalance(),
                balance.getTotalInterest(),
                balance.getLastInterestTime(),
                Config.getInterestRate(),
                Config.getInterestIntervalSeconds(),
                balance.getTotalIncome(),
                balance.getTotalExpense(),
                manager.getPendingPayment(player.getUUID()),
                Config.getMarketFeePercent(),
                manager.getTreasuryBalance(),
                Config.isFeeToTreasury(),
                returnedItems,
                balance.getTransactions(),
                balance.getIncomeByCategory(),
                balance.getExpenseByCategory(),
                Config.getListingExpiryMinutes()
        ));
    }

    /**
     * 标记玩家数据需要落盘。
     * 不直接 saveAll：几百人的服务器上每笔交易写全部玩家的 .dat 会造成明显卡顿，
     * 实际落盘由 ServerEvents 的 tick 合并（最多每 10 秒一次）。
     */
    public static void persistPlayer(ServerPlayer player) {
        ServerEvents.markPlayerDataDirty();
    }

    /** 单包最多携带的挂单条数，避免超过网络包 1MB 上限 */
    private static final int LISTING_CHUNK_SIZE = 200;

    /** 进服时同步完整市场：按块发送，客户端收齐后整体替换 */
    public static void sendFullMarketSync(ServerPlayer player, MarketManager manager) {
        List<MarketListing> all = manager.getListings();
        if (all.isEmpty()) {
            ModMessages.sendToPlayer(player, new SyncMarketListingsPacket(List.of()));
            return;
        }
        int pages = (all.size() + LISTING_CHUNK_SIZE - 1) / LISTING_CHUNK_SIZE;
        for (int i = 0; i < pages; i++) {
            int from = i * LISTING_CHUNK_SIZE;
            int to = Math.min(all.size(), from + LISTING_CHUNK_SIZE);
            ModMessages.sendToPlayer(player, new SyncMarketListingsPacket(
                    new java.util.ArrayList<>(all.subList(from, to)), i, pages));
        }
    }

    public static void subscribeLeaderboard(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LEADERBOARD_SUBSCRIBERS.add(player.getUUID());
        // 立刻推一次，打开界面不用等下一轮
        syncLeaderboard(List.of(player));
    }

    public static void unsubscribeLeaderboard(ServerPlayer player) {
        if (player != null) {
            LEADERBOARD_SUBSCRIBERS.remove(player.getUUID());
        }
    }

    public static void syncLeaderboard(List<ServerPlayer> players) {
        List<PlayerEntry> entries = new ArrayList<>();
        for (ServerPlayer player : players) {
            PlayerBalance balance = PlayerBalanceAttachment.get(player);
            double totalValue = balance.getBalance() + MoneyUtils.getInventoryMoneyTotal(player);
            entries.add(new PlayerEntry(
                    player.getName().getString(),
                    player.getUUID(),
                    totalValue,
                    balance.getBalance(),
                    balance.getTotalIncome(),
                    balance.getTotalExpense()));
            LAST_SYNCED_TOTAL.put(player.getUUID(), totalValue);
        }
        entries.sort((a, b) -> Double.compare(b.balance(), a.balance()));

        SyncLeaderboardPacket packet = new SyncLeaderboardPacket(entries);
        for (ServerPlayer player : players) {
            // 只推给正在看排行榜的玩家；关闭界面后不再消耗带宽
            if (LEADERBOARD_SUBSCRIBERS.contains(player.getUUID())) {
                ModMessages.sendToPlayer(player, packet);
            }
        }
    }
}
