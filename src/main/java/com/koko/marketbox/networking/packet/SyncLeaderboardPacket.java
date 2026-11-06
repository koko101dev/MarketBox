package com.koko.marketbox.networking.packet;

import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.MarketBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端 -> 客户端：同步财富排行榜。
 */
public class SyncLeaderboardPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncLeaderboardPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_leaderboard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLeaderboardPacket> CODEC =
            StreamCodec.ofMember(SyncLeaderboardPacket::write, SyncLeaderboardPacket::new);

    /** 榜单最多容纳的条目数（在线玩家数上限，留足余量） */
    public static final int MAX_ENTRIES = 4096;

    private final List<PlayerEntry> entries;

    public SyncLeaderboardPacket(List<PlayerEntry> entries) {
        this.entries = entries;
    }

    public SyncLeaderboardPacket(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new io.netty.handler.codec.DecoderException("invalid leaderboard size: " + size);
        }
        this.entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            // 玩家名最长 16，限到 64 避免伪造超长字符串
            String name = buf.readUtf(64);
            UUID uuid = buf.readUUID();
            double balance = buf.readDouble();
            double deposits = buf.readDouble();
            double income = buf.readDouble();
            double expense = buf.readDouble();
            this.entries.add(new PlayerEntry(name, uuid, balance, deposits, income, expense));
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (PlayerEntry entry : entries) {
            buf.writeUtf(entry.name());
            buf.writeUUID(entry.uuid());
            buf.writeDouble(entry.balance());
            buf.writeDouble(entry.deposits());
            buf.writeDouble(entry.income());
            buf.writeDouble(entry.expense());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public List<PlayerEntry> entries() {
        return entries;
    }
}
