package com.koko.marketbox.networking.packet;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 <-> 服务端：上架黑名单的增删。
 *
 * 客户端只在管理员界面里发送；服务端仍会再校验一次权限。
 * 权限校验统一放在服务端，避免依赖客户端判断。
 */
public class UpdateBlacklistPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateBlacklistPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("update_blacklist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateBlacklistPacket> CODEC =
            StreamCodec.ofMember(UpdateBlacklistPacket::write, UpdateBlacklistPacket::new);

    private final String itemId;
    private final boolean add;

    public UpdateBlacklistPacket(String itemId, boolean add) {
        this.itemId = itemId;
        this.add = add;
    }

    public UpdateBlacklistPacket(RegistryFriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
        this.add = buf.readBoolean();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(itemId);
        buf.writeBoolean(add);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(UpdateBlacklistPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player)) {
                return;
            }
            // 等级 2 起可管理（与 /marketbox 的 OP 门槛一致）
            if (player == null || !player.hasPermissions(2)) {
                return;
            }
            if (payload.itemId == null || payload.itemId.isBlank()) {
                return;
            }

            if (payload.add) {
                Config.addToBlacklist(payload.itemId);
            } else {
                Config.removeFromBlacklist(payload.itemId);
            }

            MarketBox.LOGGER.info("[Audit] {} {} blacklist entry {}",
                    player.getName().getString(),
                    payload.add ? "added" : "removed",
                    payload.itemId);

            // 回发最新列表，界面据此刷新
            ModMessages.sendToPlayer(player, new SyncBlacklistPacket(Config.getListingBlacklist()));
        });
    }

    /** 把物品转成注册表 ID，供黑名单存储 */
    public static String itemId(net.minecraft.world.item.ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
