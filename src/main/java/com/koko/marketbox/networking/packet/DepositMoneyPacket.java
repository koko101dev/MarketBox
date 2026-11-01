package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 -> 服务端：把背包中的货币存入余额。
 */
public class DepositMoneyPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DepositMoneyPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("deposit_money"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DepositMoneyPacket> CODEC =
            StreamCodec.ofMember(DepositMoneyPacket::write, DepositMoneyPacket::new);

    /** 单笔金额上限（分），防止 long 溢出 */
    private static final long MAX_CENTS = 100_000_000_000L;

    private final double amount;

    public DepositMoneyPacket(double amount) {
        this.amount = amount;
    }

    public DepositMoneyPacket(RegistryFriendlyByteBuf buf) {
        this.amount = buf.readDouble();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(amount);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(DepositMoneyPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        // 统一切回服务端主线程，避免并发修改玩家数据
        context.server().execute(() -> {
            double amount = payload.amount;
            if (player == null || amount <= 0) {
                return;
            }

            long cents = Math.round(amount * 100);
            if (cents <= 0) {
                return;
            }
            if (cents > MAX_CENTS) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.error.amount_too_large"));
                return;
            }
            double cleanAmount = cents / 100.0;

            if (!MoneyUtils.takeMoneyFromInventory(player, cleanAmount)) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.error.not_enough_items"));
                return;
            }

            PlayerBalance balance = PlayerBalanceAttachment.get(player);
            balance.addBalance(cleanAmount, "gui.marketbox.transaction.deposit");
            PlayerEvents.syncPlayerBalance(player);
            player.containerMenu.broadcastChanges();
            ServerEvents.markLeaderboardDirty();
            // 交易后立即落盘，避免异常退出丢失余额与物品变更
            PlayerEvents.persistPlayer(player);
            ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.feedback.deposited", MoneyUtils.formatValue(cleanAmount), false));
        });
    }
}
