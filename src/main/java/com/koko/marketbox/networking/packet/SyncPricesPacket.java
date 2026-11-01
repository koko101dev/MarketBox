package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端 -> 客户端：同步物品价格表（客户端本地展示用）。
 */
public class SyncPricesPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPricesPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_prices"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPricesPacket> CODEC =
            StreamCodec.ofMember(SyncPricesPacket::write, SyncPricesPacket::new);

    private final Map<String, Double> prices;

    public SyncPricesPacket(Map<String, Double> prices) {
        this.prices = prices;
    }

    public SyncPricesPacket(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.prices = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            this.prices.put(buf.readUtf(), buf.readDouble());
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(prices.size());
        for (Map.Entry<String, Double> entry : prices.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeDouble(entry.getValue());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public Map<String, Double> prices() {
        return prices;
    }
}
