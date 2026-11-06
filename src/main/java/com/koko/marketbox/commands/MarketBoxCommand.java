package com.koko.marketbox.commands;

import com.koko.marketbox.Config;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.packet.MarketListingRemovedPacket;
import com.koko.marketbox.util.MoneyUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * /marketbox 命令树。
 *
 * 权限分级：查询自己的余额对所有人开放，其余子命令（改余额、重载、市场管理）需要权限等级 2。
 * 玩家名参数使用 EntityArgument.player()，自带在线玩家 Tab 补全；金额参数附带常用值建议。
 */
public final class MarketBoxCommand {

    /** 权限等级 2（与 OP 判定一致，但更严格：不依赖 isOp 标志） */
    private static final Predicate<CommandSourceStack> OP_ONLY = source -> source.hasPermission(2);

    /** 权限等级 4：资金与破坏性操作（改余额、清空市场、金库拨款） */
    private static final Predicate<CommandSourceStack> OWNER_ONLY = source -> source.hasPermission(4);

    /** 金额参数的补全建议，减少手敲 */
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> AMOUNT_HINTS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    new String[]{"100", "1000", "10000", "100000"}, builder);

    private MarketBoxCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                build(dispatcher));
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("marketbox")
                // 查询余额：不带参数查自己，带参数查他人需要 OP
                .then(Commands.literal("balance")
                        .executes(context -> showBalance(context.getSource(), null))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(OP_ONLY)
                                .executes(context -> showBalance(context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))

                .then(Commands.literal("set")
                        .requires(OWNER_ONLY)
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .suggests(AMOUNT_HINTS)
                                        .executes(context -> updateBalance(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                DoubleArgumentType.getDouble(context, "amount"),
                                                true)))))

                .then(Commands.literal("add")
                        .requires(OWNER_ONLY)
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .suggests(AMOUNT_HINTS)
                                        .executes(context -> updateBalance(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                DoubleArgumentType.getDouble(context, "amount"),
                                                false)))))

                .then(Commands.literal("reset")
                        .requires(OWNER_ONLY)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> resetBalance(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))

                .then(Commands.literal("reload")
                        .requires(OP_ONLY)
                        .executes(context -> reload(context.getSource())))

                .then(Commands.literal("economy")
                        .requires(OP_ONLY)
                        .executes(context -> showEconomy(context.getSource())))

                .then(Commands.literal("treasury")
                        .executes(context -> showTreasury(context.getSource()))
                        .then(Commands.literal("withdraw")
                                .requires(OWNER_ONLY)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                                .suggests(AMOUNT_HINTS)
                                                .executes(context -> withdrawTreasury(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "target"),
                                                        DoubleArgumentType.getDouble(context, "amount")))))))

                .then(Commands.literal("listingexpiry")
                        .requires(OWNER_ONLY)
                        .executes(context -> {
                            int current = Config.getListingExpiryMinutes();
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    "commands.marketbox.listingexpiry.query", current), false);
                            return 1;
                        })
                        .then(Commands.argument("minutes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 1440))
                                .executes(context -> {
                                    int minutes = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "minutes");
                                    Config.setListingExpiryMinutes(minutes);
                                    context.getSource().sendSuccess(() -> Component.translatable(
                                            "commands.marketbox.listingexpiry.set", Config.getListingExpiryMinutes()), true);
                                    return 1;
                                })))

                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))

                // 市场管理
                .then(Commands.literal("market")
                        .requires(OP_ONLY)
                        .then(Commands.literal("list")
                                .executes(context -> listMarket(context.getSource())))
                        .then(Commands.literal("clear")
                                .requires(OWNER_ONLY)
                                .executes(context -> clearMarket(context.getSource())))));
    }

    private static int showBalance(CommandSourceStack source, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer who = target != null ? target : source.getPlayerOrException();
        String amount = MoneyUtils.formatValue(PlayerBalanceAttachment.get(who).getBalance());

        if (target == null) {
            source.sendSuccess(() -> Component.translatable("commands.marketbox.balance.self", amount), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.marketbox.balance.other",
                    who.getName().getString(), amount), false);
        }
        return 1;
    }

    /** 输出中文命令说明表 */
    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("commands.marketbox.help.header"), false);
        for (String key : new String[]{"balance", "set", "add", "reset",
                "market_list", "market_clear", "listingexpiry", "treasury", "economy", "reload"}) {
            source.sendSuccess(() -> Component.translatable("commands.marketbox.help." + key), false);
        }
        return 1;
    }

    /** 查看金库余额 */
    /** 经济概览：货币存量与本次会话的增减 */
    private static int showEconomy(CommandSourceStack source) {
        var server = source.getServer();
        var manager = MarketManager.get(server.overworld());

        // 在线玩家总资产（离线玩家数据未加载，无法统计）
        double onlineTotal = 0;
        int onlineCount = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            onlineTotal += PlayerBalanceAttachment.get(p).getBalance();
            onlineCount++;
        }

        // lambda 需要 effectively-final，这里做一次复制
        final double totalSnapshot = onlineTotal;
        final int countSnapshot = onlineCount;
        double treasury = manager.getTreasuryBalance();
        double net = com.koko.marketbox.util.EconomyStats.netInflation();

        source.sendSuccess(() -> Component.translatable("commands.marketbox.economy.header"), false);
        source.sendSuccess(() -> Component.translatable("commands.marketbox.economy.holdings",
                MoneyUtils.formatValue(totalSnapshot), String.valueOf(countSnapshot)), false);
        source.sendSuccess(() -> Component.translatable("commands.marketbox.economy.treasury",
                MoneyUtils.formatValue(treasury)), false);
        source.sendSuccess(() -> Component.translatable("commands.marketbox.economy.interest",
                MoneyUtils.formatValue(com.koko.marketbox.util.EconomyStats.getInterestIssued())), false);
        source.sendSuccess(() -> Component.translatable("commands.marketbox.economy.admin",
                MoneyUtils.formatValue(com.koko.marketbox.util.EconomyStats.getAdminIssued())), false);
        source.sendSuccess(() -> Component.translatable("commands.marketbox.economy.fee_destroyed",
                MoneyUtils.formatValue(com.koko.marketbox.util.EconomyStats.getFeeDestroyed())), false);
        source.sendSuccess(() -> Component.translatable("commands.marketbox.economy.net",
                MoneyUtils.formatValue(net)), false);
        return 1;
    }

    private static int showTreasury(CommandSourceStack source) {
        MarketManager manager = MarketManager.get(source.getServer().overworld());
        String balance = MoneyUtils.formatValue(manager.getTreasuryBalance());
        String mode = Config.isFeeToTreasury() ? "TREASURY" : "DESTROY";
        source.sendSuccess(() -> Component.translatable("commands.marketbox.treasury.balance", balance, mode), false);
        return 1;
    }

    /** 从金库拨款给指定在线玩家 */
    private static int withdrawTreasury(CommandSourceStack source, ServerPlayer target, double amount) {
        MarketManager manager = MarketManager.get(source.getServer().overworld());
        double granted = manager.withdrawTreasury(amount);
        if (granted <= 0) {
            source.sendFailure(Component.translatable("commands.marketbox.treasury.insufficient"));
            return 0;
        }

        PlayerBalance balance = PlayerBalanceAttachment.get(target);
        balance.addBalance(granted, "gui.marketbox.transaction.treasury");
        PlayerEvents.syncPlayerBalance(target);
        PlayerEvents.persistPlayer(target);
        ServerEvents.markLeaderboardDirty();

        com.koko.marketbox.util.EconomyStats.addTreasuryGrant(granted);
        String formatted = MoneyUtils.formatValue(granted);
        source.sendSuccess(() -> Component.translatable("commands.marketbox.treasury.withdraw_success",
                formatted, target.getName().getString()), true);
        target.sendSystemMessage(Component.translatable("commands.marketbox.treasury.received", formatted));
        return 1;
    }

    private static int listMarket(CommandSourceStack source) {
        MarketManager manager = MarketManager.get(source.getServer().overworld());
        int count = manager.getListings().size();
        source.sendSuccess(() -> Component.translatable("commands.marketbox.market.list", count), false);
        return count;
    }

    /** 清空全部挂单：物品退还给卖家（离线则进入待领取），并通知客户端移除 */
    private static int clearMarket(CommandSourceStack source) {
        MarketManager manager = MarketManager.get(source.getServer().overworld());
        List<MarketListing> snapshot = new ArrayList<>(manager.getListings());
        if (snapshot.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.marketbox.market.empty"), false);
            return 0;
        }

        for (MarketListing listing : snapshot) {
            manager.addReturnedItem(listing.sellerUUID(), listing.item().copy());
            manager.removeListing(listing.id());
            ModMessages.sendToAll(source.getServer(), new MarketListingRemovedPacket(listing.id()));

            ServerPlayer seller = source.getServer().getPlayerList().getPlayer(listing.sellerUUID());
            if (seller != null) {
                seller.sendSystemMessage(Component.translatable("commands.marketbox.market.cleared_notice"));
            }
        }

        source.sendSuccess(() -> Component.translatable("commands.marketbox.market.cleared", snapshot.size()), true);
        return snapshot.size();
    }

    private static int reload(CommandSourceStack source) {
        Config.load();

        if (source.getServer() != null) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                PlayerEvents.syncPlayerBalance(player);
            }
        }

        source.sendSuccess(() -> Component.translatable("commands.marketbox.reload.success"), true);
        return 1;
    }

    private static int resetBalance(CommandSourceStack source, ServerPlayer target) {
        PlayerBalance balance = PlayerBalanceAttachment.get(target);
        balance.setBalance(0);
        balance.setTotalInterest(0);
        balance.resetStatistics();

        // 定期存款的本金来自余额：不清掉的话「清零」之后点一下到期就能把本息全取回来
        for (var deposit : balance.getFixedDeposits()) {
            balance.removeFixedDeposit(deposit.id());
        }
        // 待领取的市场收益同理，不清会下次上线自动到账
        MarketManager manager = MarketManager.get(target.serverLevel());
        manager.claimPendingPayment(target.getUUID());

        PlayerEvents.syncPlayerBalance(target);
        ServerEvents.markLeaderboardDirty();
        PlayerEvents.persistPlayer(target);

        source.sendSuccess(() -> Component.translatable("commands.marketbox.reset.success", target.getName()), true);
        return 1;
    }

    private static int updateBalance(CommandSourceStack source, ServerPlayer target, double amount, boolean isSet) {
        PlayerBalance balance = PlayerBalanceAttachment.get(target);
        if (isSet) {
            // 用 admin 版本：setBalance 会把差额记进「累计收入」，
            // 管理员设一次 100 万，排行榜的总收入维度就被污染了
            balance.setBalanceAdmin(amount);
        } else {
            balance.addBalance(amount, "gui.marketbox.transaction.admin");
            com.koko.marketbox.util.EconomyStats.addAdminIssued(amount);
        }

        PlayerEvents.syncPlayerBalance(target);
        ServerEvents.markLeaderboardDirty();
        PlayerEvents.persistPlayer(target);

        source.sendSuccess(() -> Component.translatable("commands.marketbox.update.success", target.getName()), true);
        return 1;
    }
}
