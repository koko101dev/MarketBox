package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.capabilities.PlayerBalance;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ClientData {
    /** 1.21 默认皮肤贴图（宽版 Steve），玩家信息尚未加载时的头像占位 */
    public static final net.minecraft.resources.ResourceLocation DEFAULT_SKIN =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private static double playerBalance;
    private static double totalInterest;
    private static long lastInterestTime;
    private static double interestRate;
    private static int interestIntervalSeconds;
    private static double totalIncome;
    private static double totalExpense;
    private static double pendingPayment;
    /** 当前玩家权限等级：0 普通，1~2 管理员，4 服主 */
    private static int permissionLevel = 0;
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
    private static java.util.Map<String, Long> incomeByCategory = new java.util.HashMap<>();
    private static java.util.Map<String, Long> expenseByCategory = new java.util.HashMap<>();
    /** 挂单有效期（分钟），服主设定，界面只做展示 */
    private static int listingExpiryMinutes = 120;
    /** 禁止上架的物品 ID，管理员界面用 */
    private static java.util.List<String> blacklist = new java.util.ArrayList<>();
    /** 市场限制项，管理员设置界面用 */
    private static int maxListings = 20;
    private static double maxListingPrice = 1_000_000;
    private static double maxTransactionAmount = 1_000_000_000;
    private static boolean transferEnabled = true;
    private static java.util.List<com.koko.marketbox.capabilities.FixedDeposit> fixedDeposits =
            new java.util.ArrayList<>();
    private static java.util.List<Double> enabledDenominations =
            new java.util.ArrayList<>(java.util.List.of(0.01, 0.1, 1.0, 5.0, 10.0, 20.0, 50.0, 100.0));

    /** 管理员及以上：可查看经济页、使用管理员操作、看到黑名单入口 */
    public static boolean isAdmin() {
        return permissionLevel >= 2;
    }

    /** 服主级：可修改利率、手续费等全服经济参数 */
    public static boolean isOwner() {
        return permissionLevel >= 4;
    }

    public static int getPermissionLevel() {
        return permissionLevel;
    }

    public static void setPermissionLevel(int level) {
        permissionLevel = level;
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
            clearListingChunks();
            return;
        }
        long now = System.currentTimeMillis();
        // 上一轮还没收齐就断了连接：超过 10 秒的残留分块直接丢弃，
        // 否则下次连接可能用旧分块补齐缺页，出现点了购买却提示挂单不存在
        if (lastChunkAt != 0 && now - lastChunkAt > CHUNK_TIMEOUT_MS) {
            listingChunks.clear();
            expectedChunkCount = 0;
        }
        lastChunkAt = now;

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
            clearListingChunks();
        }
    }

    /** 丢弃半成品分块；断开连接时调用，避免跨连接残留 */
    public static void clearListingChunks() {
        listingChunks.clear();
        expectedChunkCount = 0;
        lastChunkAt = 0;
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

    public static void setCategoryTotals(java.util.Map<String, Long> income,
                                         java.util.Map<String, Long> expense) {
        incomeByCategory = income == null ? new java.util.HashMap<>() : income;
        expenseByCategory = expense == null ? new java.util.HashMap<>() : expense;
    }

    public static int getMaxListings() {
        return maxListings;
    }

    public static double getMaxListingPrice() {
        return maxListingPrice;
    }

    public static double getMaxTransactionAmount() {
        return maxTransactionAmount;
    }

    public static boolean isTransferEnabled() {
        return transferEnabled;
    }

    public static java.util.List<com.koko.marketbox.capabilities.FixedDeposit> getFixedDeposits() {
        return fixedDeposits;
    }

    public static void setFixedDeposits(
            java.util.List<com.koko.marketbox.capabilities.FixedDeposit> list) {
        fixedDeposits = list == null ? new java.util.ArrayList<>() : list;
    }

    public static java.util.List<Double> getEnabledDenominations() {
        return enabledDenominations;
    }

    public static void setEnabledDenominations(java.util.List<Double> values) {
        enabledDenominations = values == null ? new java.util.ArrayList<>() : values;
    }

    public static void setMarketLimits(int listings, double price, double tx, boolean transfer) {
        maxListings = listings;
        maxListingPrice = price;
        maxTransactionAmount = tx;
        transferEnabled = transfer;
    }

    public static java.util.List<String> getBlacklist() {
        return blacklist;
    }

    public static void setBlacklist(java.util.List<String> list) {
        blacklist = list == null ? new java.util.ArrayList<>() : list;
    }

    public static int getListingExpiryMinutes() {
        return listingExpiryMinutes;
    }

    public static void setListingExpiryMinutes(int minutes) {
        listingExpiryMinutes = minutes;
    }

    public static java.util.Map<String, Long> getIncomeByCategory() {
        return incomeByCategory;
    }

    public static java.util.Map<String, Long> getExpenseByCategory() {
        return expenseByCategory;
    }

    public static void setTransactions(List<PlayerBalance.Transaction> transactions) {
        ClientData.transactions = transactions;
    }

    public static List<PlayerBalance.Transaction> getTransactions() {
        return transactions;
    }

}
