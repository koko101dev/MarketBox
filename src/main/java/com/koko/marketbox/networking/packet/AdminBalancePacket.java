package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.networking.PacketRateLimiter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 客户端 -> 服务端：管理员在设置界面直接调整某位玩家的余额。
 * 与 /marketbox set|add|reset 命令等价，服务端校验权限（等级 >= 2）后才执行。
 */
public class AdminBalancePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AdminBalancePacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("admin_balance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AdminBalancePacket> CODEC =
            StreamCodec.ofMember(AdminBalancePacket::write, AdminBalancePacket::new);

    public static final int ACTION_SET = 0;
    public static final int ACTION_ADD = 1;
    public static final int ACTION_RESET = 2;

    private final UUID targetUuid;
    private final double amount;
    private final int action;

    public AdminBalancePacket(UUID targetUuid, double amount, int action) {
        this.targetUuid = targetUuid;
        this.amount = amount;
        this.action = action;
    }

    public AdminBalancePacket(RegistryFriendlyByteBuf buf) {
        this.targetUuid = buf.readUUID();
        this.amount = buf.readDouble();
        this.action = buf.readVarInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
        buf.writeDouble(amount);
        buf.writeVarInt(action);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(AdminBalancePacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer admin = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(admin)) {
                return;
            }
            try {
                // 资金操作需要等级 4（服主级）
                if (admin == null || !admin.hasPermissions(4) || admin.getServer() == null) {
                    return;
                }

                ServerPlayer target = admin.getServer().getPlayerList().getPlayer(payload.targetUuid);
                if (target == null) {
                    ServerPlayNetworking.send(admin, new ActionFeedbackPacket("gui.marketbox.admin.error.offline"));
                    return;
                }

                PlayerBalance balance = PlayerBalanceAttachment.get(target);
                double amount = Math.max(0, Math.min(1_000_000_000.0, payload.amount));
                switch (payload.action) {
                    case ACTION_SET -> balance.setBalance(amount);
                    case ACTION_ADD -> balance.addBalance(amount, "gui.marketbox.transaction.admin");
                    case ACTION_RESET -> balance.setBalance(0);
                    default -> {
                        return;
                    }
                }

                PlayerEvents.syncPlayerBalance(target);
                PlayerEvents.persistPlayer(target);
                ServerEvents.markLeaderboardDirty();

                String key = switch (payload.action) {
                    case ACTION_SET -> "gui.marketbox.admin.set_done";
                    case ACTION_ADD -> "gui.marketbox.admin.add_done";
                    default -> "gui.marketbox.admin.reset_done";
                };
                ServerPlayNetworking.send(admin, new ActionFeedbackPacket(key, target.getName().getString(), false));
            } catch (Exception e) {
                MarketBox.LOGGER.error("管理员调整余额失败 类型={}", e.getClass().getSimpleName());
            }
        });
    }
}
