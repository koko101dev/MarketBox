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

    private final Map<String, Double> prices;

    public SyncDealPricesPacket(Map<String, Double> prices) {
        this.prices = new HashMap<>(prices);
    }

    public SyncDealPricesPacket(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.prices = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            this.prices.put(buf.readUtf(), buf.readDouble());
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
