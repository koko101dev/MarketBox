package com.koko.marketbox.networking.packet;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.FixedDeposit;
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

import java.util.UUID;

/**
 * 客户端 -> 服务端：把余额转为一笔定期存款。
 *
 * 利率按期限档位固定，存入时锁定；服务端校验笔数与金额，客户端传值一律裁剪。
 */
public class OpenFixedDepositPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenFixedDepositPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("open_fixed_deposit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFixedDepositPacket> CODEC =
            StreamCodec.ofMember(OpenFixedDepositPacket::write, OpenFixedDepositPacket::new);

    /** 每人最多同时持有的定期笔数 */
    public static final int MAX_DEPOSITS = 3;
    /** 可选期限（天） */
    public static final int[] ALLOWED_DAYS = {7, 14, 30};

    /** 日利率由服主配置决定，客户端只做展示 */
    public static double rateForDays(int days) {
        return com.koko.marketbox.Config.getFixedRate(days);
    }

    private final double amount;
    private final int days;

    public OpenFixedDepositPacket(double amount, int days) {
        this.amount = amount;
        this.days = days;
    }

    public OpenFixedDepositPacket(RegistryFriendlyByteBuf buf) {
        this.amount = buf.readDouble();
        this.days = buf.readVarInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(amount);
        buf.writeVarInt(days);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(OpenFixedDepositPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player, "fixed_open")) {
                return;
            }
            // 与存取款一致：定期通道过去只校验了 cents>0，
            // 可以把远超单笔限额的钱直接转进去，绕过 maxTransactionAmount
            if (!Double.isFinite(payload.amount) || payload.amount > Config.getMaxTransactionAmount()) {
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.error.amount_too_large"));
                return;
            }
            long cents = Math.round(payload.amount * 100);
            if (cents <= 0) {
                return;
            }
            if (payload.days != 7 && payload.days != 14 && payload.days != 30) {
                return;
            }

            PlayerBalance balance = PlayerBalanceAttachment.get(player);
            if (balance.getBalanceCents() < cents) {
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.error.not_enough_balance"));
                return;
            }
            if (balance.getFixedDeposits().size() >= MAX_DEPOSITS) {
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.fixed.too_many"));
                return;
            }

            double cleanAmount = cents / 100.0;
            boolean deducted = false;
            try {
                // 余额 → 定期同样是资产形式转换，不计入累计支出
                balance.moveBalance(-cleanAmount, "gui.marketbox.transaction.fixed_deposit");
                deducted = true;
                balance.addFixedDeposit(new FixedDeposit(
                        UUID.randomUUID(),
                        cents,
                        rateForDays(payload.days),
                        System.currentTimeMillis(),
                        payload.days));

                PlayerEvents.syncPlayerBalance(player);
                ServerEvents.markLeaderboardDirty();
                PlayerEvents.persistPlayer(player);
                ModMessages.sendToPlayer(player, ActionFeedbackPacket.success(
                        "gui.marketbox.fixed.opened",
                        MoneyUtils.formatValue(cleanAmount) + " / " + payload.days + "天"));
            } catch (Exception e) {
                // 余额已扣但定期没建起来：把钱退回活期余额
                if (deducted) {
                    balance.moveBalance(cleanAmount, "gui.marketbox.transaction.fixed_deposit");
                }
                com.koko.marketbox.MarketBox.LOGGER.error(
                        "Open fixed deposit failed: player={}, amount={}, days={}",
                        player.getUUID(), cleanAmount, payload.days, e);
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.error.operation_failed"));
            }
        });
    }
}
