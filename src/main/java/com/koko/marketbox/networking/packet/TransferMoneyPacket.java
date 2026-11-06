package com.koko.marketbox.networking.packet;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import com.koko.marketbox.util.MoneyUtils;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 客户端 -> 服务端：玩家之间转账。目标离线时款项挂入待领取列表。
 */
public class TransferMoneyPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TransferMoneyPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("transfer_money"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferMoneyPacket> CODEC =
            StreamCodec.ofMember(TransferMoneyPacket::write, TransferMoneyPacket::new);

    /** 单笔金额上限（分），防止 long 溢出 */

    private final UUID targetUuid;
    private final double amount;

    public TransferMoneyPacket(UUID targetUuid, double amount) {
        this.targetUuid = targetUuid;
        this.amount = amount;
    }

    public TransferMoneyPacket(RegistryFriendlyByteBuf buf) {
        this.targetUuid = buf.readUUID();
        this.amount = buf.readDouble();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
        buf.writeDouble(amount);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(TransferMoneyPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer sender = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(sender, "transfer")) {
                return;
            }
            double amount = payload.amount;
            UUID targetUuid = payload.targetUuid;
            if (sender == null || amount <= 0) {
                return;
            }
            // 服主可关闭玩家互转（例如防止线下交易与纠纷）
            if (!Config.isTransferEnabled()) {
                ModMessages.sendToPlayer(sender,
                        new ActionFeedbackPacket("gui.marketbox.error.transfer_disabled"));
                return;
            }

            long cents = Math.round(amount * 100);
            if (cents <= 0) {
                return;
            }
            if (amount > Config.getMaxTransactionAmount()) {
                ModMessages.sendToPlayer(sender, new ActionFeedbackPacket("gui.marketbox.error.amount_too_large"));
                return;
            }

            if (sender.getUUID().equals(targetUuid)) {
                ModMessages.sendToPlayer(sender, new ActionFeedbackPacket("gui.marketbox.error.self_transfer"));
                return;
            }

            PlayerBalance senderBalance = PlayerBalanceAttachment.get(sender);
            if (senderBalance.getBalanceCents() < cents) {
                ModMessages.sendToPlayer(sender, new ActionFeedbackPacket("gui.marketbox.error.not_enough_balance"));
                return;
            }

            double cleanAmount = cents / 100.0;
            // 先确认目标玩家真实存在：否则钱从发送方扣掉后无人能领取，等于销毁货币
            var profileLookup = sender.getServer().getProfileCache().get(targetUuid);
            if (profileLookup.isEmpty()) {
                ModMessages.sendToPlayer(sender, new ActionFeedbackPacket("gui.marketbox.error.player_not_found"));
                return;
            }
            String targetName = profileLookup.get().getName();

            boolean deducted = false;
            boolean credited = false;
            try {
                senderBalance.addBalance(-cleanAmount, "gui.marketbox.transaction.transfer_to_prefix:" + targetName);
                deducted = true;

                ServerPlayer target = sender.getServer().getPlayerList().getPlayer(targetUuid);
                if (target != null) {
                    PlayerBalance targetBalance = PlayerBalanceAttachment.get(target);
                    targetBalance.addBalance(cleanAmount, "gui.marketbox.transaction.transfer_from_prefix:" + sender.getName().getString());
                    credited = true;
                    PlayerEvents.syncPlayerBalance(target);
                    target.sendSystemMessage(Component.translatable("gui.marketbox.transfer.received",
                            sender.getName().getString(), cleanAmount));
                } else {
                    MarketManager.get(sender.serverLevel()).addPendingPayment(targetUuid, cleanAmount);
                    credited = true;
                }

                PlayerEvents.syncPlayerBalance(sender);
                ServerEvents.markLeaderboardDirty();
                // 交易后立即落盘，避免异常退出丢失余额与物品变更
                PlayerEvents.persistPlayer(sender);
                ModMessages.sendToPlayer(sender, new ActionFeedbackPacket("gui.marketbox.feedback.transferred", MoneyUtils.formatValue(cleanAmount), false));
                sender.sendSystemMessage(Component.translatable("gui.marketbox.transfer.success", targetName, cleanAmount));
            } catch (Exception e) {
                // 只扣了钱、还没入账就失败：原路退回，不能凭空销毁
                if (deducted && !credited) {
                    senderBalance.addBalance(cleanAmount, "gui.marketbox.transaction.transfer_refund");
                }
                com.koko.marketbox.MarketBox.LOGGER.error(
                        "Transfer failed: sender={}, target={}, amount={}",
                        sender.getUUID(), targetUuid, cleanAmount, e);
                ModMessages.sendToPlayer(sender,
                        new ActionFeedbackPacket("gui.marketbox.error.operation_failed"));
            }
        });
    }
}
