package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.market.MarketListing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：市场上新增了一条挂单。
 * 增量推送替代原先的全量广播，避免玩家数量与挂单数量相乘造成的带宽放大。
 */
public class MarketListingAddedPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MarketListingAddedPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("market_listing_added"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketListingAddedPacket> CODEC =
            StreamCodec.ofMember(MarketListingAddedPacket::write, MarketListingAddedPacket::new);

    private final MarketListing listing;

    public MarketListingAddedPacket(MarketListing listing) {
        this.listing = listing;
    }

    public MarketListingAddedPacket(RegistryFriendlyByteBuf buf) {
        this.listing = MarketListing.STREAM_CODEC.decode(buf);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        MarketListing.STREAM_CODEC.encode(buf, listing);
    }

    public MarketListing listing() {
        return listing;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
