package com.koko.marketbox.networking.packet;

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
 * 客户端 -> 服务端：结束一笔定期存款。
 *
 * 到期：本金与利息一并回到余额；
 * 未到期：只退本金、不计利息，作为提前支取的代价。
 */
public class CloseFixedDepositPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CloseFixedDepositPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("close_fixed_deposit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloseFixedDepositPacket> CODEC =
            StreamCodec.ofMember(CloseFixedDepositPacket::write, CloseFixedDepositPacket::new);

    private final UUID depositId;

    public CloseFixedDepositPacket(UUID depositId) {
        this.depositId = depositId;
    }

    public CloseFixedDepositPacket(RegistryFriendlyByteBuf buf) {
        this.depositId = buf.readUUID();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(depositId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(CloseFixedDepositPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player)) {
                return;
            }

            PlayerBalance balance = PlayerBalanceAttachment.get(player);
            FixedDeposit deposit = balance.findFixedDeposit(payload.depositId);
            if (deposit == null) {
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.fixed.not_found"));
                return;
            }

            long now = System.currentTimeMillis();
            boolean matured = deposit.isMatured(now);
            long payoutCents = matured ? deposit.totalCents() : deposit.principalCents();
            double payout = payoutCents / 100.0;

            balance.removeFixedDeposit(deposit.id());
            balance.addBalance(payout, matured
                    ? "gui.marketbox.transaction.fixed_matured"
                    : "gui.marketbox.transaction.fixed_early");

            PlayerEvents.syncPlayerBalance(player);
            ServerEvents.markLeaderboardDirty();
            PlayerEvents.persistPlayer(player);

            if (matured) {
                ModMessages.sendToPlayer(player, ActionFeedbackPacket.success(
                        "gui.marketbox.fixed.claimed",
                        MoneyUtils.formatValue(payout)));
            } else {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket(
                        "gui.marketbox.fixed.early_withdraw",
                        MoneyUtils.formatValue(payout), true));
            }
        });
    }
}
