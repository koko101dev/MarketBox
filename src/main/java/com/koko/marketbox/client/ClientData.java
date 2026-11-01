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
    // 经济概览（打开经济页时向服务端请求）
    private static double economyOnlineTotal;
    private static int economyOnlineCount;
    private static double economyInterest;
    private static double economyAdmin;
    private static double economyFeeDestroyed;
    private static double economyFeeToTreasury;
    private static int economyListingCount;
    /** 物品 ID -> 最近成交单价 */
    private static java.util.Map<String, Double> dealPrices = new java.util.HashMap<>();
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

    public static void setDealPrices(java.util.Map<String, Double> prices) {
        dealPrices = prices == null ? new java.util.HashMap<>() : prices;
    }

    public static double getDealPrice(net.minecraft.world.item.ItemStack stack) {
        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString();
        Double value = dealPrices.get(key);
        return value == null ? -1 : value;
    }

    public static void setEconomy(double onlineTotal, int onlineCount, double interest,
                                  double admin, double feeDestroyed, double feeToTreasury,
                                  int listingCount) {
        economyOnlineTotal = onlineTotal;
        economyOnlineCount = onlineCount;
        economyInterest = interest;
        economyAdmin = admin;
        economyFeeDestroyed = feeDestroyed;
        economyFeeToTreasury = feeToTreasury;
        economyListingCount = listingCount;
    }

    public static double getEconomyOnlineTotal() {
        return economyOnlineTotal;
    }

    public static int getEconomyOnlineCount() {
        return economyOnlineCount;
    }

    public static double getEconomyInterest() {
        return economyInterest;
    }

    public static double getEconomyAdmin() {
        return economyAdmin;
    }

    public static double getEconomyFeeDestroyed() {
        return economyFeeDestroyed;
    }

    public static double getEconomyFeeToTreasury() {
        return economyFeeToTreasury;
    }

    public static int getEconomyListingCount() {
        return economyListingCount;
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

    /**
     * 市场角标：退回物品与待领取收益都来自市场交易，
     * 提示应当出现在「市场」入口而不是「银行」。
     */
    public static int getMarketBadgeCount() {
        int count = returnedItems == null ? 0 : returnedItems.size();
        if (pendingPayment > 0) {
            count++;
        }
        return count;
    }

    /** 分块全量同步的临时缓冲 */
    private static final java.util.Map<Integer, List<MarketListing>> listingChunks = new java.util.HashMap<>();
    private static int expectedChunkCount = 0;
    /** 上次收到分块的时间：长时间收不齐说明连接中断，丢弃半成品 */
    private static long lastChunkAt = 0;
    private static final long CHUNK_TIMEOUT_MS = 10_000;

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
