package com.koko.marketbox.capabilities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    
    /** 定期存款：每笔独立锁定利率与期限 */
    private final java.util.List<FixedDeposit> fixedDeposits = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** 按用途分类的累计收入/支出（分），供统计页拆分展示 */
    private final java.util.Map<String, Long> incomeByCategory = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> expenseByCategory = new java.util.concurrent.ConcurrentHashMap<>();

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

    /**
     * 直接改写余额，不计入总收入/总支出。
     * 供管理员操作使用：否则「设成 100 万」会被记成玩家赚了 100 万，统计失真。
     */
    public void setBalanceAdmin(double balance) {
        balanceCents.set(Math.round(balance * 100));
        addTransaction(new Transaction(System.currentTimeMillis(), balance,
                "gui.marketbox.transaction.admin_set"));
    }

    public void setBalanceCents(long cents) {
        long old = balanceCents.getAndSet(cents);
        if (cents > old) {
            totalIncomeCents.addAndGet(cents - old);
        } else if (cents < old) {
            totalExpenseCents.addAndGet(old - cents);
        }
    }

    /** 从流水原因里提取用途分类，用于统计页的收支拆分 */
    public static String categoryOf(String reason) {
        if (reason == null) {
            return "other";
        }
        if (reason.startsWith("gui.marketbox.transaction.interest")) return "interest";
        if (reason.startsWith("gui.marketbox.transaction.market_sell")) return "market_sell";
        if (reason.startsWith("gui.marketbox.transaction.market_buy")) return "market_buy";
        if (reason.startsWith("gui.marketbox.transaction.market_result")) return "market_result";
        if (reason.startsWith("gui.marketbox.transaction.transfer_from")) return "transfer_in";
        if (reason.startsWith("gui.marketbox.transaction.transfer_to")) return "transfer_out";
        if (reason.startsWith("gui.marketbox.transaction.admin")) return "admin";
        if (reason.startsWith("gui.marketbox.transaction.treasury")) return "treasury";
        return "other";
    }

    public void addBalance(double amount, String reason) {
        long cents = Math.round(amount * 100);
        if (cents == 0) return;

        // 分类累计：与总额分开统计，便于在统计页拆分来源
        String category = categoryOf(reason);
        if (cents > 0) {
            incomeByCategory.merge(category, cents, Long::sum);
        } else {
            expenseByCategory.merge(category, -cents, Long::sum);
        }
        
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
        if (!fixedDeposits.isEmpty()) {
            ListTag deposits = new ListTag();
            for (FixedDeposit d : fixedDeposits) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("id", d.id());
                tag.putLong("principal", d.principalCents());
                tag.putDouble("rate", d.dailyRatePercent());
                tag.putLong("start", d.startTime());
                tag.putInt("days", d.days());
                deposits.add(tag);
            }
            nbt.put("fixedDeposits", deposits);
        }

        nbt.put("incomeByCategory", writeLongMap(incomeByCategory));
        nbt.put("expenseByCategory", writeLongMap(expenseByCategory));
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
        
        fixedDeposits.clear();
        if (nbt.contains("fixedDeposits", Tag.TAG_LIST)) {
            ListTag deposits = nbt.getList("fixedDeposits", Tag.TAG_COMPOUND);
            for (int i = 0; i < deposits.size(); i++) {
                CompoundTag tag = deposits.getCompound(i);
                fixedDeposits.add(new FixedDeposit(
                        tag.getUUID("id"),
                        tag.getLong("principal"),
                        tag.getDouble("rate"),
                        tag.getLong("start"),
                        tag.getInt("days")));
            }
        }

        incomeByCategory.clear();
        expenseByCategory.clear();
        readLongMap(nbt.getCompound("incomeByCategory"), incomeByCategory);
        readLongMap(nbt.getCompound("expenseByCategory"), expenseByCategory);

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

    private static CompoundTag writeLongMap(java.util.Map<String, Long> map) {
        CompoundTag tag = new CompoundTag();
        map.forEach(tag::putLong);
        return tag;
    }

    private static void readLongMap(CompoundTag tag, java.util.Map<String, Long> target) {
        for (String key : tag.getAllKeys()) {
            target.put(key, tag.getLong(key));
        }
    }

    public java.util.List<FixedDeposit> getFixedDeposits() {
        return new java.util.ArrayList<>(fixedDeposits);
    }

    /** 存入一笔定期：本金从余额扣除由调用方负责 */
    public void addFixedDeposit(FixedDeposit deposit) {
        fixedDeposits.add(deposit);
    }

    public void removeFixedDeposit(UUID id) {
        fixedDeposits.removeIf(d -> d.id().equals(id));
    }

    public FixedDeposit findFixedDeposit(UUID id) {
        for (FixedDeposit d : fixedDeposits) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /** 分类收入（分），key 见 {@link #categoryOf} */
    public java.util.Map<String, Long> getIncomeByCategory() {
        return new java.util.HashMap<>(incomeByCategory);
    }

    /** 分类支出（分） */
    public java.util.Map<String, Long> getExpenseByCategory() {
        return new java.util.HashMap<>(expenseByCategory);
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
