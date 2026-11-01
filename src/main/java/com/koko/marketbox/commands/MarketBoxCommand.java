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
import com.koko.marketbox.networking.packet.SyncPricesPacket;
import com.koko.marketbox.util.MoneyUtils;
import com.koko.marketbox.util.PriceManager;
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
                        .requires(OP_ONLY)
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .suggests(AMOUNT_HINTS)
                                        .executes(context -> updateBalance(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                DoubleArgumentType.getDouble(context, "amount"),
                                                true)))))

                .then(Commands.literal("add")
                        .requires(OP_ONLY)
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .suggests(AMOUNT_HINTS)
                                        .executes(context -> updateBalance(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                DoubleArgumentType.getDouble(context, "amount"),
                                                false)))))

                .then(Commands.literal("reset")
                        .requires(OP_ONLY)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> resetBalance(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))

                .then(Commands.literal("reload")
                        .requires(OP_ONLY)
                        .executes(context -> reload(context.getSource())))

                // 市场管理
                .then(Commands.literal("market")
                        .requires(OP_ONLY)
                        .then(Commands.literal("list")
                                .executes(context -> listMarket(context.getSource())))
                        .then(Commands.literal("clear")
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
        PriceManager.loadPrices();

        if (source.getServer() != null) {
            ModMessages.sendToAll(source.getServer(), new SyncPricesPacket(PriceManager.getPrices()));
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

        PlayerEvents.syncPlayerBalance(target);
        ServerEvents.markLeaderboardDirty();
        PlayerEvents.persistPlayer(target);

        source.sendSuccess(() -> Component.translatable("commands.marketbox.reset.success", target.getName()), true);
        return 1;
    }

    private static int updateBalance(CommandSourceStack source, ServerPlayer target, double amount, boolean isSet) {
        PlayerBalance balance = PlayerBalanceAttachment.get(target);
        if (isSet) {
            balance.setBalance(amount);
        } else {
            balance.addBalance(amount, "gui.marketbox.transaction.admin");
        }

        PlayerEvents.syncPlayerBalance(target);
        ServerEvents.markLeaderboardDirty();
        PlayerEvents.persistPlayer(target);

        source.sendSuccess(() -> Component.translatable("commands.marketbox.update.success", target.getName()), true);
        return 1;
    }
}
