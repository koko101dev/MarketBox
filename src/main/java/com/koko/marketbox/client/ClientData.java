package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.capabilities.PlayerBalance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientData {
    private static double playerBalance;
    private static double totalInterest;
    private static long lastInterestTime;
    private static double interestRate;
    private static int interestIntervalSeconds;
    private static double totalIncome;
    private static double totalExpense;
    private static double pendingPayment;
    /** 当前玩家是否为管理员 */
    private static boolean admin = false;
    /** 市场成交手续费百分比（服务端同步） */
    private static double marketFeePercent = 0;
    private static double treasuryBalance = 0;
    private static boolean feeToTreasury = false;
    private static List<net.minecraft.world.item.ItemStack> returnedItems = new ArrayList<>();
    private static List<PlayerEntry> leaderboard = new ArrayList<>();
    private static List<MarketListing> marketListings = new ArrayList<>();
    private static List<PlayerBalance.Transaction> transactions = new ArrayList<>();

    public static boolean isAdmin() {
        return admin;
    }

    public static void setAdmin(boolean value) {
        admin = value;
    }

    public static boolean isFeeToTreasury() {
        return feeToTreasury;
    }

    public static void setFeeToTreasury(boolean value) {
        feeToTreasury = value;
    }

    public static double getTreasuryBalance() {
        return treasuryBalance;
    }

    public static void setTreasuryBalance(double value) {
        treasuryBalance = value;
    }

    public static double getMarketFeePercent() {
        return marketFeePercent;
    }

    public static void setMarketFeePercent(double value) {
        marketFeePercent = value;
    }

    public static void setPendingPayment(double value) {
        pendingPayment = value;
    }

    /** 银行页角标：有退货物品或有待领取收益时提示 */
    public static int getBankBadgeCount() {
        int count = returnedItems == null ? 0 : returnedItems.size();
        if (pendingPayment > 0) {
            count++;
        }
        return count;
    }

    /** 分块全量同步的临时缓冲 */
    private static final java.util.Map<Integer, List<MarketListing>> listingChunks = new java.util.HashMap<>();
    private static int expectedChunkCount = 0;

    public static void setMarketListings(List<MarketListing> listings) {
        marketListings = new java.util.ArrayList<>(listings);
    }

    /** 收到一块全量数据；收齐后整体替换本地列表 */
    public static void acceptListingChunk(int page, int totalPages, List<MarketListing> chunk) {
        if (totalPages <= 1) {
            setMarketListings(chunk);
            return;
        }
        if (expectedChunkCount != totalPages) {
            listingChunks.clear();
            expectedChunkCount = totalPages;
        }
        listingChunks.put(page, chunk);
        if (listingChunks.size() >= totalPages) {
            List<MarketListing> merged = new java.util.ArrayList<>();
            for (int i = 0; i < totalPages; i++) {
                merged.addAll(listingChunks.getOrDefault(i, List.of()));
            }
            setMarketListings(merged);
            listingChunks.clear();
            expectedChunkCount = 0;
        }
    }

    /** 增量：新增一条挂单 */
    public static void addMarketListing(MarketListing listing) {
        if (listing == null) {
            return;
        }
        marketListings.removeIf(l -> l.id().equals(listing.id()));
        marketListings.add(0, listing);
    }

    /** 增量：移除一条挂单 */
    public static void removeMarketListing(java.util.UUID id) {
        marketListings.removeIf(l -> l.id().equals(id));
    }

    public static List<MarketListing> getMarketListings() {
        return marketListings;
    }

    public static void setPlayerBalance(double balance) {
        playerBalance = balance;
    }

    public static double getPlayerBalance() {
        return playerBalance;
    }

    public static void setInterestData(double totalInterest, long lastInterestTime, double interestRate, int intervalSeconds) {
        ClientData.totalInterest = totalInterest;
        ClientData.lastInterestTime = lastInterestTime;
        ClientData.interestRate = interestRate;
        ClientData.interestIntervalSeconds = intervalSeconds;
    }

    public static void setIncomeExpenseData(double income, double expense) {
        ClientData.totalIncome = income;
        ClientData.totalExpense = expense;
    }

    public static double getTotalIncome() {
        return totalIncome;
    }

    public static double getTotalExpense() {
        return totalExpense;
    }

    public static double getTotalInterest() {
        return totalInterest;
    }

    public static long getLastInterestTime() {
        return lastInterestTime;
    }

    public static double getInterestRate() {
        return interestRate;
    }

    public static int getInterestIntervalSeconds() {
        return interestIntervalSeconds;
    }

    public static void setReturnedItems(List<net.minecraft.world.item.ItemStack> items) {
        returnedItems = items;
    }

    public static List<net.minecraft.world.item.ItemStack> getReturnedItems() {
        return returnedItems;
    }

    public static boolean hasReturnedItems() {
        return !returnedItems.isEmpty();
    }

    public static void setLeaderboard(List<PlayerEntry> entries) {
        leaderboard = entries;
    }

    public static List<PlayerEntry> getLeaderboard() {
        return leaderboard;
    }

    public static void setTransactions(List<PlayerBalance.Transaction> transactions) {
        ClientData.transactions = transactions;
    }

    public static List<PlayerBalance.Transaction> getTransactions() {
        return transactions;
    }

}
