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
    /** 上次同步给客户端的权限等级，用于检测 /op、/deop 之类的变更 */
    private static final Map<UUID, Integer> LAST_PERMISSION = new ConcurrentHashMap<>();

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
            LAST_PERMISSION.remove(handler.getPlayer().getUUID());
            ServerEvents.markLeaderboardDirty();
        });

        // Fabric 无 PlayerTickEvent，统一在服务端 tick 中遍历在线玩家
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(player);
            }
        });
    }

    /** 把当前权限等级（及管理员的黑名单）推给客户端 */
    public static void syncAdminStatus(ServerPlayer player) {
        int level = currentPermissionLevel(player);
        LAST_PERMISSION.put(player.getUUID(), level);
        ModMessages.sendToPlayer(player, new SyncAdminStatusPacket(level));
        if (level >= 2) {
            ModMessages.sendToPlayer(player,
                    new com.koko.marketbox.networking.packet.SyncBlacklistPacket(Config.getListingBlacklist()));
        }
    }

    /**
     * 定期检查权限是否变化。
     * 单人游戏 /op、服务器控制台 op 都不会触发任何事件，
     * 不同步的话客户端界面要等重连才更新。
     */
    public static void checkPermissionChanges(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int current = currentPermissionLevel(player);
            Integer last = LAST_PERMISSION.get(player.getUUID());
            if (last == null || last != current) {
                syncAdminStatus(player);
            }
        }
    }

    /** 读取玩家实际权限等级，用于客户端按级别显示设置项 */
    private static int currentPermissionLevel(ServerPlayer player) {
        // 逐级探测：hasPermissions(4) 为真时游戏内就是服主级
        for (int level = 4; level >= 1; level--) {
            if (player.hasPermissions(level)) {
                return level;
            }
        }
        return 0;
    }

    private static void handleJoin(ServerPlayer player) {
        // 诊断用：确认玩家加入时从存档中恢复到了多少余额
        if (com.koko.marketbox.MarketBox.LOGGER.isDebugEnabled()) {
            com.koko.marketbox.MarketBox.LOGGER.debug("[MarketBox] {} joined, balance={} cents",
                    player.getName().getString(), PlayerBalanceAttachment.get(player).getBalanceCents());
        }

        // 有到期的定期时提醒一次，避免玩家忘记领取
        try {
            PlayerBalance joinBalance = PlayerBalanceAttachment.get(player);
            long now = System.currentTimeMillis();
            int matured = 0;
            long totalCents = 0;
            for (com.koko.marketbox.capabilities.FixedDeposit d : joinBalance.getFixedDeposits()) {
                if (d.isMatured(now)) {
                    matured++;
                    totalCents += d.totalCents();
                }
            }
            if (matured > 0) {
                final int count = matured;
                final String amount = com.koko.marketbox.util.MoneyUtils.formatValue(totalCents / 100.0);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "gui.marketbox.fixed.matured_notice", count, amount));
            }
        } catch (Exception e) {
            com.koko.marketbox.MarketBox.LOGGER.warn("Failed to check matured deposits for {}", player.getUUID(), e);
        }

        // 同步管理员权限，客户端据此决定设置界面显示哪些项
        syncAdminStatus(player);

        checkAndApplyInterest(player);

        MarketManager manager = MarketManager.get(player.serverLevel());
        double pendingPayment = manager.claimPendingPayment(player.getUUID());
        if (pendingPayment > 0) {
            PlayerBalanceAttachment.get(player)
                    .addBalance(pendingPayment, "gui.marketbox.transaction.market_result");
            player.sendSystemMessage(Component.translatable("gui.marketbox.market.pending_payment_received"));
            // 领取同时改了玩家余额和市场数据，必须立刻标脏：
            // 否则崩在两次落盘之间会重复领取（或反过来丢失）
            persistPlayer(player);
        }

        if (!manager.getReturnedItems(player.getUUID()).isEmpty()) {
            player.sendSystemMessage(Component.translatable("gui.marketbox.market.returned_items_notification"));
        }

        syncPlayerBalance(player);
        ServerEvents.markLeaderboardDirty();

        sendFullMarketSync(player, manager);
        ModMessages.sendToPlayer(player, new SyncDealPricesPacket(manager.getDealPrices()));
        // 转账、管理员选人、管理员操作都直接读客户端缓存的榜单：
        // 不先给一份的话，没打开过排行榜的玩家这些列表全是空的
        sendLeaderboardSnapshot(player);
    }

    private static void tickPlayer(ServerPlayer player) {
        // 每秒检查一次利息
        if (player.tickCount % 20 == 0) {
            if (checkAndApplyInterest(player)) {
                syncPlayerBalance(player);
                ServerEvents.markLeaderboardDirty();
                // 利息是真金白银：不标脏的话，崩在周期落盘之前这一期就白发了
                persistPlayer(player);
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
                Config.getListingExpiryMinutes(),
                Config.getMaxListingsPerPlayer(),
                Config.getMaxListingPrice(),
                Config.getMaxTransactionAmount(),
                Config.isTransferEnabled(),
                Config.getEnabledDenominations(),
                balance.getFixedDeposits()
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

    /**
     * 单包最多携带的挂单条数。
     *
     * 按条数而不是按字节切：单条挂单的真实大小要编码物品 NBT 才知道，
     * 每次同步都多编码一遍的开销比省下的带宽更贵。降到 100 条留出约一倍余量，
     * 常规物品远达不到 1MB；带超大 NBT 的极端挂单仍可能超限，但概率已大幅降低。
     */
    private static final int LISTING_CHUNK_SIZE = 100;

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
        // 已在订阅集合里就不再重复推全量：客户端每次打开界面都会发一次订阅，
        // 重复推等于把「一个请求 → 全服计算 + 全服发包」的放大效应留着
        if (!LEADERBOARD_SUBSCRIBERS.add(player.getUUID())) {
            return;
        }
        // 立刻推一次，打开界面不用等下一轮。
        // 必须传全服在线玩家：只传玩家自己的话，榜单里只有自己一条，要等下一次 tick 才补全。
        net.minecraft.server.MinecraftServer server = player.getServer();
        syncLeaderboard(server != null ? server.getPlayerList().getPlayers() : List.of(player));
    }

    public static void unsubscribeLeaderboard(ServerPlayer player) {
        if (player != null) {
            LEADERBOARD_SUBSCRIBERS.remove(player.getUUID());
        }
    }

    public static void syncLeaderboard(List<ServerPlayer> players) {
        SyncLeaderboardPacket packet = new SyncLeaderboardPacket(buildEntries(players));
        for (ServerPlayer player : players) {
            // 只推给正在看排行榜的玩家；关闭界面后不再消耗带宽
            if (LEADERBOARD_SUBSCRIBERS.contains(player.getUUID())) {
                ModMessages.sendToPlayer(player, packet);
            }
        }
    }

    /** 只给这一名玩家推一份当前在线榜单，不加入订阅集合：转账与管理员选人要用 */
    public static void sendLeaderboardSnapshot(ServerPlayer target) {
        if (target == null || target.getServer() == null) {
            return;
        }
        ModMessages.sendToPlayer(target,
                new SyncLeaderboardPacket(buildEntries(target.getServer().getPlayerList().getPlayers())));
    }

    private static List<PlayerEntry> buildEntries(List<ServerPlayer> players) {
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
        return entries;
    }
}
