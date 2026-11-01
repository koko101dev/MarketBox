package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import com.koko.marketbox.util.EconomyStats;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 客户端 -> 服务端：从市场金库拨款给指定玩家（等价于 /marketbox treasury withdraw）。
 * 需要权限等级 4；操作会向全服广播，保证资金流动公开可查。
 */
public class TreasuryGrantPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TreasuryGrantPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("treasury_grant"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TreasuryGrantPacket> CODEC =
            StreamCodec.ofMember(TreasuryGrantPacket::write, TreasuryGrantPacket::new);

    private final UUID targetUuid;
    private final double amount;

    public TreasuryGrantPacket(UUID targetUuid, double amount) {
        this.targetUuid = targetUuid;
        this.amount = amount;
    }

    public TreasuryGrantPacket(RegistryFriendlyByteBuf buf) {
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

    public static void handle(TreasuryGrantPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer admin = context.player();
        context.server().execute(() -> {
            try {
                if (admin == null || !admin.hasPermissions(4) || admin.getServer() == null) {
                    return;
                }
                if (!PacketRateLimiter.allow(admin)) {
                    return;
                }

                ServerPlayer target = admin.getServer().getPlayerList().getPlayer(payload.targetUuid);
                if (target == null) {
                    ModMessages.sendToPlayer(admin, new ActionFeedbackPacket("gui.marketbox.admin.error.offline"));
                    return;
                }

                MarketManager manager = MarketManager.get(admin.serverLevel());
                double granted = manager.withdrawTreasury(Math.min(payload.amount, 1_000_000_000.0));
                if (granted <= 0) {
                    ModMessages.sendToPlayer(admin,
                            new ActionFeedbackPacket("commands.marketbox.treasury.insufficient"));
                    return;
                }

                PlayerBalance balance = PlayerBalanceAttachment.get(target);
                balance.addBalance(granted, "gui.marketbox.transaction.treasury");
                PlayerEvents.syncPlayerBalance(target);
                PlayerEvents.persistPlayer(target);
                ServerEvents.markLeaderboardDirty();
                EconomyStats.addTreasuryGrant(granted);

                String formatted = MoneyUtils.formatValue(granted);
                String targetName = target.getName().getString();

                // 资金流动对全服公开
                admin.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("commands.marketbox.treasury.broadcast", formatted, targetName), false);
                target.sendSystemMessage(Component.translatable("commands.marketbox.treasury.received", formatted));
                ModMessages.sendToPlayer(admin, new ActionFeedbackPacket(
                        "commands.marketbox.treasury.withdraw_success", formatted + " -> " + targetName, false));
            } catch (Exception e) {
                MarketBox.LOGGER.error("金库拨款失败 类型={}", e.getClass().getSimpleName());
            }
        });
    }
}
