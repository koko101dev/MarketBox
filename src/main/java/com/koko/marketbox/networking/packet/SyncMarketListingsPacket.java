package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.market.MarketListing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：同步全部市场挂单。
 */
public class SyncMarketListingsPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncMarketListingsPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_market_listings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMarketListingsPacket> CODEC =
            StreamCodec.ofMember(SyncMarketListingsPacket::write, SyncMarketListingsPacket::new);

    /** 单包最多携带的挂单数：写入侧每块 200 条，这里留出余量并挡住伪造的长度前缀 */
    public static final int MAX_LISTINGS = 512;

    private final List<MarketListing> listings;
    /** 分页信息：进服时挂单过多会拆成多个包，避免单包超过 1MB 上限 */
    private final int page;
    private final int totalPages;

    public SyncMarketListingsPacket(List<MarketListing> listings) {
        this(listings, 0, 1);
    }

    public SyncMarketListingsPacket(List<MarketListing> listings, int page, int totalPages) {
        this.listings = listings;
        this.page = page;
        this.totalPages = totalPages;
    }

    public SyncMarketListingsPacket(RegistryFriendlyByteBuf buf) {
        this.page = buf.readVarInt();
        this.totalPages = buf.readVarInt();
        int size = buf.readVarInt();
        // 解码早于任何业务校验：不限制长度前缀的话，5 个字节就能让客户端预分配几个 G
        if (size < 0 || size > MAX_LISTINGS) {
            throw new io.netty.handler.codec.DecoderException("invalid listing count: " + size);
        }
        this.listings = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            this.listings.add(MarketListing.STREAM_CODEC.decode(buf));
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(page);
        buf.writeVarInt(totalPages);
        buf.writeVarInt(listings.size());
        for (MarketListing listing : listings) {
            MarketListing.STREAM_CODEC.encode(buf, listing);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public List<MarketListing> listings() {
        return listings;
    }

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }
}
