package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：下发禁止上架的物品列表。
 *
 * 只在管理员打开黑名单界面、或修改后回发时使用，数据量很小。
 */
public class SyncBlacklistPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncBlacklistPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_blacklist"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBlacklistPacket> CODEC =
            StreamCodec.ofMember(SyncBlacklistPacket::write, SyncBlacklistPacket::new);

    /** 黑名单最多条目数 */
    public static final int MAX_ITEMS = 10_000;

    private final List<String> itemIds;

    public SyncBlacklistPacket(List<String> itemIds) {
        this.itemIds = itemIds;
    }

    public SyncBlacklistPacket(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ITEMS) {
            throw new io.netty.handler.codec.DecoderException("invalid blacklist size: " + size);
        }
        this.itemIds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.itemIds.add(buf.readUtf(256));
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(itemIds.size());
        for (String id : itemIds) {
            buf.writeUtf(id);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public List<String> itemIds() {
        return itemIds;
    }

    public static void handle(SyncBlacklistPacket payload, ServerPlayNetworking.Context context) {
        // 服务端不会收到这个包，仅作接口对齐
    }
}
