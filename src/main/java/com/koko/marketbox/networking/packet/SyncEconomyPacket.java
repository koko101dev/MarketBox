package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：经济概览数据。
 * 只包含展示用的聚合值，不含任何玩家个人隐私数据。
 */
public class SyncEconomyPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncEconomyPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_economy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEconomyPacket> CODEC =
            StreamCodec.ofMember(SyncEconomyPacket::write, SyncEconomyPacket::new);

    private final double onlineTotal;
    private final int onlineCount;
    private final double treasury;
    private final double interestIssued;
    private final double adminIssued;
    private final double feeDestroyed;
    private final double feeToTreasury;
    private final int listingCount;

    public SyncEconomyPacket(double onlineTotal, int onlineCount, double treasury,
                             double interestIssued, double adminIssued, double feeDestroyed,
                             double feeToTreasury, int listingCount) {
        this.onlineTotal = onlineTotal;
        this.onlineCount = onlineCount;
        this.treasury = treasury;
        this.interestIssued = interestIssued;
        this.adminIssued = adminIssued;
        this.feeDestroyed = feeDestroyed;
        this.feeToTreasury = feeToTreasury;
        this.listingCount = listingCount;
    }

    public SyncEconomyPacket(RegistryFriendlyByteBuf buf) {
        this.onlineTotal = buf.readDouble();
        this.onlineCount = buf.readVarInt();
        this.treasury = buf.readDouble();
        this.interestIssued = buf.readDouble();
        this.adminIssued = buf.readDouble();
        this.feeDestroyed = buf.readDouble();
        this.feeToTreasury = buf.readDouble();
        this.listingCount = buf.readVarInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(onlineTotal);
        buf.writeVarInt(onlineCount);
        buf.writeDouble(treasury);
        buf.writeDouble(interestIssued);
        buf.writeDouble(adminIssued);
        buf.writeDouble(feeDestroyed);
        buf.writeDouble(feeToTreasury);
        buf.writeVarInt(listingCount);
    }

    public double onlineTotal() {
        return onlineTotal;
    }

    public int onlineCount() {
        return onlineCount;
    }

    public double treasury() {
        return treasury;
    }

    public double interestIssued() {
        return interestIssued;
    }

    public double adminIssued() {
        return adminIssued;
    }

    public double feeDestroyed() {
        return feeDestroyed;
    }

    public double feeToTreasury() {
        return feeToTreasury;
    }

    public int listingCount() {
        return listingCount;
    }

    /** 净增货币 = 利息 + 管理员发放 - 销毁 */
    public double netInflation() {
        return interestIssued + adminIssued - feeDestroyed;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
