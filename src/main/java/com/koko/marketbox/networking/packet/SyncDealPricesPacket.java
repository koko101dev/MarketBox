package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端 -> 客户端：物品的最近成交单价。
 * 成交价与挂单价分开同步：挂单会消失，成交记录则长期保留。
 */
public class SyncDealPricesPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncDealPricesPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_deal_prices"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncDealPricesPacket> CODEC =
            StreamCodec.ofMember(SyncDealPricesPacket::write, SyncDealPricesPacket::new);

    /** 成交价表最多条目数（每种物品一条） */
    public static final int MAX_ENTRIES = 100_000;

    private final Map<String, Double> prices;

    public SyncDealPricesPacket(Map<String, Double> prices) {
        this.prices = new HashMap<>(prices);
    }

    public SyncDealPricesPacket(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new io.netty.handler.codec.DecoderException("invalid deal price size: " + size);
        }
        this.prices = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            // 物品 ID 最长约 100 字符，限到 256
            this.prices.put(buf.readUtf(256), buf.readDouble());
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(prices.size());
        prices.forEach((key, value) -> {
            buf.writeUtf(key);
            buf.writeDouble(value);
        });
    }

    public Map<String, Double> prices() {
        return prices;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
