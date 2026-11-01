package com.koko.marketbox.networking.packet;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 -> 服务端：修改服务端权威的利息配置。
 * 仅管理员（权限等级 >= 2）可提交，数值一律在服务端裁剪后再落盘。
 */
public class UpdateCommonConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateCommonConfigPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("update_common_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCommonConfigPacket> CODEC =
            StreamCodec.ofMember(UpdateCommonConfigPacket::write, UpdateCommonConfigPacket::new);

    private final double interestRate;
    private final int interestIntervalSeconds;

    public UpdateCommonConfigPacket(double interestRate, int interestIntervalSeconds) {
        this.interestRate = interestRate;
        this.interestIntervalSeconds = interestIntervalSeconds;
    }

    public UpdateCommonConfigPacket(RegistryFriendlyByteBuf buf) {
        this.interestRate = buf.readDouble();
        this.interestIntervalSeconds = buf.readInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(interestRate);
        buf.writeInt(interestIntervalSeconds);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(UpdateCommonConfigPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            try {
                if (player == null || !player.hasPermissions(2) || player.getServer() == null) {
                    return;
                }

                // 服务端再次裁剪，防止客户端伪造越界值
                double safeRate = Math.max(0.0, Math.min(100.0, payload.interestRate));
                int safeInterval = Math.max(1, payload.interestIntervalSeconds);

                Config.setInterestSettings(safeRate, safeInterval);

                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    try {
                        PlayerEvents.syncPlayerBalance(p);
                    } catch (Exception e) {
                        MarketBox.LOGGER.error("配置更新后同步余额失败 player={} 类型={}",
                                p.getUUID(), e.getClass().getSimpleName(), e);
                    }
                }
                ServerEvents.markLeaderboardDirty();
            } catch (Exception e) {
                MarketBox.LOGGER.error("更新公共配置失败 类型={}", e.getClass().getSimpleName(), e);
            }
        });
    }
}
