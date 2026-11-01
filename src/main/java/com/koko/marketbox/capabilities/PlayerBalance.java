package com.koko.marketbox.capabilities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Capability to store player balance.
 */
public class PlayerBalance {
    private final AtomicLong balanceCents = new AtomicLong(0);
    private final AtomicLong totalInterestCents = new AtomicLong(0);
    private final AtomicLong lastInterestTime = new AtomicLong(0);
    private final AtomicLong totalIncomeCents = new AtomicLong(0);
    private final AtomicLong totalExpenseCents = new AtomicLong(0);
    
    private final List<Transaction> transactions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final int MAX_TRANSACTIONS = 50;

    public double getBalance() {
        return balanceCents.get() / 100.0;
    }

    public long getBalanceCents() {
        return balanceCents.get();
    }

    public void setBalance(double balance) {
        setBalanceCents(Math.round(balance * 100));
    }

    public void setBalanceCents(long cents) {
        long old = balanceCents.getAndSet(cents);
        if (cents > old) {
            totalIncomeCents.addAndGet(cents - old);
        } else if (cents < old) {
            totalExpenseCents.addAndGet(old - cents);
        }
    }

    public void addBalance(double amount, String reason) {
        long cents = Math.round(amount * 100);
        if (cents == 0) return;
        
        if (cents > 0) {
            long currentCents = balanceCents.get();
            if (cents > Long.MAX_VALUE - currentCents) {
                cents = Long.MAX_VALUE - currentCents;
                if (cents <= 0) return;
            }
            totalIncomeCents.addAndGet(cents);
        } else {
            totalExpenseCents.addAndGet(Math.abs(cents));
        }
        balanceCents.addAndGet(cents);
        addTransaction(new Transaction(System.currentTimeMillis(), cents / 100.0, reason));
    }

    public void addBalance(double amount) {
        addBalance(amount, "gui.marketbox.transaction.unknown");
    }

    public double getTotalInterest() {
        return totalInterestCents.get() / 100.0;
    }

    public long getTotalInterestCents() {
        return totalInterestCents.get();
    }

    public void setTotalInterest(double amount) {
        totalInterestCents.set(Math.round(amount * 100));
    }

    public void addInterest(double amount) {
        long cents = Math.round(amount * 100);
        if (cents <= 0) return;

        long currentCents = balanceCents.get();
        if (cents > Long.MAX_VALUE - currentCents) {
            cents = Long.MAX_VALUE - currentCents;
            if (cents <= 0) return;
        }

        totalInterestCents.addAndGet(cents);
        balanceCents.addAndGet(cents);
        totalIncomeCents.addAndGet(cents);
        addTransaction(new Transaction(System.currentTimeMillis(), cents / 100.0, "gui.marketbox.transaction.interest"));
    }

    public double getTotalIncome() {
        return totalIncomeCents.get() / 100.0;
    }

    public double getTotalExpense() {
        return totalExpenseCents.get() / 100.0;
    }

    public void resetStatistics() {
        totalIncomeCents.set(0);
        totalExpenseCents.set(0);
    }

    public long getLastInterestTime() {
        return lastInterestTime.get();
    }

    public void setLastInterestTime(long time) {
        lastInterestTime.set(time);
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    private void addTransaction(Transaction transaction) {
        transactions.add(0, transaction);
        if (transactions.size() > MAX_TRANSACTIONS) {
            transactions.remove(transactions.size() - 1);
        }
    }

    public void copyFrom(PlayerBalance other) {
        this.balanceCents.set(other.balanceCents.get());
        this.totalInterestCents.set(other.totalInterestCents.get());
        this.lastInterestTime.set(other.lastInterestTime.get());
        this.totalIncomeCents.set(other.totalIncomeCents.get());
        this.totalExpenseCents.set(other.totalExpenseCents.get());
        this.transactions.clear();
        this.transactions.addAll(other.transactions);
    }

    public void saveNBTData(CompoundTag nbt) {
        nbt.putLong("balanceCents", balanceCents.get());
        nbt.putLong("totalInterestCents", totalInterestCents.get());
        nbt.putLong("lastInterestTime", lastInterestTime.get());
        nbt.putLong("totalIncomeCents", totalIncomeCents.get());
        nbt.putLong("totalExpenseCents", totalExpenseCents.get());
        
        ListTag list = new ListTag();
        for (Transaction t : transactions) {
            CompoundTag tTag = new CompoundTag();
            tTag.putLong("time", t.timestamp());
            tTag.putDouble("amount", t.amount());
            tTag.putString("reason", t.reason());
            list.add(tTag);
        }
        nbt.put("transactions", list);
    }

    public void loadNBTData(CompoundTag nbt) {
        if (nbt.contains("balanceCents")) {
            balanceCents.set(nbt.getLong("balanceCents"));
            totalInterestCents.set(nbt.getLong("totalInterestCents"));
        } else {
            balanceCents.set(Math.round(nbt.getDouble("balance") * 100));
            totalInterestCents.set(Math.round(nbt.getDouble("totalInterest") * 100));
        }
        lastInterestTime.set(nbt.getLong("lastInterestTime"));
        totalIncomeCents.set(nbt.getLong("totalIncomeCents"));
        totalExpenseCents.set(nbt.getLong("totalExpenseCents"));
        
        transactions.clear();
        if (nbt.contains("transactions", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("transactions", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tTag = list.getCompound(i);
                transactions.add(new Transaction(
                    tTag.getLong("time"),
                    tTag.getDouble("amount"),
                    tTag.getString("reason")
                ));
            }
        }
    }

    /**
     * 根据当前时间尝试计算并应用利息
     * 使用数学公式优化长时间离线的情况
     */
    public double applyInterest(double rate, int intervalSeconds) {
        long currentTime = System.currentTimeMillis();
        long lastTime = lastInterestTime.get();
        if (lastTime == 0) {
            lastInterestTime.set(currentTime);
            return 0;
        }

        long intervalMillis = (long) intervalSeconds * 1000;
        if (intervalMillis <= 0) return 0;

        long elapsedTime = currentTime - lastTime;
        long numInterests = elapsedTime / intervalMillis;

        if (numInterests > 0) {
            double currentBalance = getBalance();
            if (currentBalance <= 0) {
                lastInterestTime.addAndGet(numInterests * intervalMillis);
                return 0;
            }

            double newBalance;

            if (numInterests > 100) {
                double factor = Math.pow(1 + (rate / 100.0), numInterests);
                if (!Double.isFinite(factor) || factor > 1e15) {
                    lastInterestTime.addAndGet(numInterests * intervalMillis);
                    return 0;
                }
                newBalance = currentBalance * factor;
                if (!Double.isFinite(newBalance) || newBalance > 9e18) {
                    lastInterestTime.addAndGet(numInterests * intervalMillis);
                    return 0;
                }
            } else {
                newBalance = currentBalance;
                for (int i = 0; i < numInterests; i++) {
                    newBalance += newBalance * (rate / 100.0);
                    if (!Double.isFinite(newBalance) || newBalance > 9e18) {
                        lastInterestTime.addAndGet(numInterests * intervalMillis);
                        return 0;
                    }
                }
            }

            double totalInterestToAdd = newBalance - currentBalance;
            if (totalInterestToAdd > 0 && Double.isFinite(totalInterestToAdd)) {
                addInterest(totalInterestToAdd);
                lastInterestTime.addAndGet(numInterests * intervalMillis);
                return totalInterestToAdd;
            } else {
                lastInterestTime.addAndGet(numInterests * intervalMillis);
            }
        }
        return 0;
    }

    /** 从 NBT 构造实例，供 Fabric Attachment 反序列化使用 */
    public static PlayerBalance fromTag(CompoundTag tag) {
        PlayerBalance balance = new PlayerBalance();
        balance.loadNBTData(tag);
        return balance;
    }

    /** 序列化为 NBT，供 Fabric Attachment 持久化使用 */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        saveNBTData(tag);
        return tag;
    }

    /**
     * Transaction record
     */
    public record Transaction(long timestamp, double amount, String reason) {}
}
