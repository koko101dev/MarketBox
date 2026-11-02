package com.koko.marketbox.networking.packet;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 -> 服务端：把余额提取为背包中的货币物品。
 */
public class WithdrawMoneyPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WithdrawMoneyPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("withdraw_money"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawMoneyPacket> CODEC =
            StreamCodec.ofMember(WithdrawMoneyPacket::write, WithdrawMoneyPacket::new);

    /** 单笔金额上限（分），防止 long 溢出 */

    private final double amount;

    public WithdrawMoneyPacket(double amount) {
        this.amount = amount;
    }

    public WithdrawMoneyPacket(RegistryFriendlyByteBuf buf) {
        this.amount = buf.readDouble();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(amount);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(WithdrawMoneyPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player)) {
                return;
            }
            double amount = payload.amount;
            if (player == null || amount <= 0) {
                return;
            }

            long cents = Math.round(amount * 100);
            if (cents <= 0) {
                return;
            }
            if (amount > Config.getMaxTransactionAmount()) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.error.amount_too_large"));
                return;
            }

            PlayerBalance balance = PlayerBalanceAttachment.get(player);
            if (balance.getBalanceCents() < cents) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.error.not_enough_balance"));
                return;
            }

            double cleanAmount = cents / 100.0;
            // 面额被裁剪后可能凑不出该金额，先拦下，避免扣钱却发不足额物品
            if (!MoneyUtils.canMakeExact(cleanAmount)) {
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.error.amount_not_makeable"));
                return;
            }
            balance.addBalance(-cleanAmount, "gui.marketbox.transaction.withdraw");
            MoneyUtils.giveMoneyToInventory(player, cleanAmount);
            PlayerEvents.syncPlayerBalance(player);
            player.containerMenu.broadcastChanges();
            ServerEvents.markLeaderboardDirty();
            // 交易后立即落盘，避免异常退出丢失余额与物品变更
            PlayerEvents.persistPlayer(player);
            ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.feedback.withdrew", MoneyUtils.formatValue(cleanAmount), false));
        });
    }
}
