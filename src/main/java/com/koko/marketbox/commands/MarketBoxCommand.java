package com.koko.marketbox.commands;

import com.koko.marketbox.Config;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.packet.SyncPricesPacket;
import com.koko.marketbox.util.PriceManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /marketbox 命令：设置、追加、重置余额，以及重载配置与物价表。
 */
public final class MarketBoxCommand {
    private MarketBoxCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                build(dispatcher));
    }

    private static void build(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("marketbox")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> updateBalance(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                DoubleArgumentType.getDouble(context, "amount"),
                                                true)))))
                .then(Commands.literal("add")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(context -> updateBalance(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                DoubleArgumentType.getDouble(context, "amount"),
                                                false)))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(context -> resetBalance(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target")))))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
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

        source.sendSuccess(() -> Component.translatable("commands.marketbox.update.success", target.getName()), true);
        return 1;
    }
}
