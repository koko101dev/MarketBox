package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * 服务端 -> 客户端：某条挂单已被移除（售出、下架或过期）。
 * 只带 id，客户端从本地列表删除，包体固定 16 字节。
 */
public class MarketListingRemovedPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MarketListingRemovedPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("market_listing_removed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketListingRemovedPacket> CODEC =
            StreamCodec.ofMember(MarketListingRemovedPacket::write, MarketListingRemovedPacket::new);

    private final UUID listingId;

    public MarketListingRemovedPacket(UUID listingId) {
        this.listingId = listingId;
    }

    public MarketListingRemovedPacket(RegistryFriendlyByteBuf buf) {
        this.listingId = buf.readUUID();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(listingId);
    }

    public UUID listingId() {
        return listingId;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
