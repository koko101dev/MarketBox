package com.koko.marketbox.market;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 市场挂单条目。
 * 网络编解码改用 1.21 的 StreamCodec；物品栈必须走 RegistryFriendlyByteBuf，
 * 因为 1.20.5 起物品数据由组件承载，需要注册表上下文才能正确读写。
 *
 * perPlayerLimit：每位玩家最多可购买的数量，0 表示不限。
 */
public record MarketListing(UUID id, ItemStack item, double price, UUID sellerUUID,
                            String sellerName, long createdAt, int expiryMinutes,
                            int perPlayerLimit) {

    /** 兼容未设置限购的旧调用 */
    public MarketListing(UUID id, ItemStack item, double price, UUID sellerUUID,
                         String sellerName, long createdAt, int expiryMinutes) {
        this(id, item, price, sellerUUID, sellerName, createdAt, expiryMinutes, 0);
    }

    /** 挂单被部分购买后，按剩余数量与剩余总价生成新的条目（id 不变） */
    public MarketListing withRemaining(int remainingCount, double remainingPrice) {
        ItemStack remaining = item.copy();
        remaining.setCount(remainingCount);
        return new MarketListing(id, remaining, remainingPrice, sellerUUID, sellerName,
                createdAt, expiryMinutes, perPlayerLimit);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MarketListing> STREAM_CODEC = StreamCodec.of(
            (buf, listing) -> {
                buf.writeUUID(listing.id());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, listing.item());
                buf.writeDouble(listing.price());
                buf.writeUUID(listing.sellerUUID());
                buf.writeUtf(listing.sellerName());
                buf.writeLong(listing.createdAt());
                buf.writeVarInt(listing.expiryMinutes());
                buf.writeVarInt(listing.perPlayerLimit());
            },
            buf -> new MarketListing(
                    buf.readUUID(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readDouble(),
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readLong(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );
}
