package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：同步余额、利息参数、统计、退货列表与流水。
 * 仅作数据载体，客户端处理逻辑位于 client 包，避免服务端加载客户端类。
 */
public class SyncPlayerBalancePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPlayerBalancePacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_player_balance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerBalancePacket> CODEC =
            StreamCodec.ofMember(SyncPlayerBalancePacket::write, SyncPlayerBalancePacket::new);

    private final double balance;
    private final double totalInterest;
    private final long lastInterestTime;
    private final double interestRate;
    private final int intervalSeconds;
    private final double totalIncome;
    private final double totalExpense;
    /** 待领取的挂单收益（如卖家不在线时的分账） */
    private final double pendingPayment;
    /** 市场成交手续费百分比，随配置一起下发 */
    private final double marketFeePercent;
    /** 市场金库余额，供设置界面只读展示 */
    private final double treasuryBalance;
    private final boolean feeToTreasury;
    private final List<ItemStack> returnedItems;
    private final List<PlayerBalance.Transaction> transactions;

    public SyncPlayerBalancePacket(double balance, double totalInterest, long lastInterestTime,
                                   double interestRate, int intervalSeconds, double totalIncome,
                                   double totalExpense, double pendingPayment, double marketFeePercent, double treasuryBalance, boolean feeToTreasury,
                                   List<ItemStack> returnedItems,
                                   List<PlayerBalance.Transaction> transactions) {
        this.balance = balance;
        this.totalInterest = totalInterest;
        this.lastInterestTime = lastInterestTime;
        this.interestRate = interestRate;
        this.intervalSeconds = intervalSeconds;
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
        this.pendingPayment = pendingPayment;
        this.marketFeePercent = marketFeePercent;
        this.treasuryBalance = treasuryBalance;
        this.feeToTreasury = feeToTreasury;
        this.returnedItems = returnedItems;
        this.transactions = transactions;
    }

    public SyncPlayerBalancePacket(RegistryFriendlyByteBuf buf) {
        this.balance = buf.readDouble();
        this.totalInterest = buf.readDouble();
        this.lastInterestTime = buf.readLong();
        this.interestRate = buf.readDouble();
        this.intervalSeconds = buf.readInt();
        this.totalIncome = buf.readDouble();
        this.totalExpense = buf.readDouble();
        this.pendingPayment = buf.readDouble();
        this.marketFeePercent = buf.readDouble();
        this.treasuryBalance = buf.readDouble();
        this.feeToTreasury = buf.readBoolean();

        int returnedSize = buf.readVarInt();
        this.returnedItems = new ArrayList<>(returnedSize);
        for (int i = 0; i < returnedSize; i++) {
            this.returnedItems.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }

        int transSize = buf.readVarInt();
        this.transactions = new ArrayList<>(transSize);
        for (int i = 0; i < transSize; i++) {
            this.transactions.add(new PlayerBalance.Transaction(buf.readLong(), buf.readDouble(), buf.readUtf()));
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(balance);
        buf.writeDouble(totalInterest);
        buf.writeLong(lastInterestTime);
        buf.writeDouble(interestRate);
        buf.writeInt(intervalSeconds);
        buf.writeDouble(totalIncome);
        buf.writeDouble(totalExpense);
        buf.writeDouble(pendingPayment);
        buf.writeDouble(marketFeePercent);
        buf.writeDouble(treasuryBalance);
        buf.writeBoolean(feeToTreasury);

        buf.writeVarInt(returnedItems.size());
        for (ItemStack stack : returnedItems) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        }

        buf.writeVarInt(transactions.size());
        for (PlayerBalance.Transaction t : transactions) {
            buf.writeLong(t.timestamp());
            buf.writeDouble(t.amount());
            buf.writeUtf(t.reason());
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public double balance() {
        return balance;
    }

    public double totalInterest() {
        return totalInterest;
    }

    public long lastInterestTime() {
        return lastInterestTime;
    }

    public double interestRate() {
        return interestRate;
    }

    public int intervalSeconds() {
        return intervalSeconds;
    }

    public double totalIncome() {
        return totalIncome;
    }

    public double totalExpense() {
        return totalExpense;
    }

    public boolean feeToTreasury() {
        return feeToTreasury;
    }

    public double treasuryBalance() {
        return treasuryBalance;
    }

    public double marketFeePercent() {
        return marketFeePercent;
    }

    public double pendingPayment() {
        return pendingPayment;
    }

    public List<ItemStack> returnedItems() {
        return returnedItems;
    }

    public List<PlayerBalance.Transaction> transactions() {
        return transactions;
    }
}
