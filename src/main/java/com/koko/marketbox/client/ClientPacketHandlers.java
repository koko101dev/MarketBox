package com.koko.marketbox.client;

import com.koko.marketbox.networking.packet.ActionFeedbackPacket;
import com.koko.marketbox.networking.packet.SyncLeaderboardPacket;
import com.koko.marketbox.networking.packet.MarketListingAddedPacket;
import com.koko.marketbox.networking.packet.SyncAdminStatusPacket;
import com.koko.marketbox.networking.packet.SyncDealPricesPacket;
import com.koko.marketbox.networking.packet.SyncEconomyPacket;
import com.koko.marketbox.networking.packet.MarketListingRemovedPacket;
import com.koko.marketbox.networking.packet.SyncMarketListingsPacket;
import com.koko.marketbox.networking.packet.SyncPlayerBalancePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/**
 * 下行包（服务端 -> 客户端）处理。
 * 全部切回客户端主线程执行，避免在 netty 线程操作界面状态。
 */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPlayerBalancePacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientData.setPlayerBalance(payload.balance());
                    ClientData.setInterestData(payload.totalInterest(), payload.lastInterestTime(),
                            payload.interestRate(), payload.intervalSeconds());
                    ClientData.setIncomeExpenseData(payload.totalIncome(), payload.totalExpense());
                    ClientData.setPendingPayment(payload.pendingPayment());
                    ClientData.setMarketFeePercent(payload.marketFeePercent());
                    ClientData.setTreasuryBalance(payload.treasuryBalance());
                    ClientData.setFeeToTreasury(payload.feeToTreasury());
                    ClientData.setReturnedItems(payload.returnedItems());
                    ClientData.setTransactions(payload.transactions());

                    ClientData.setCategoryTotals(payload.incomeByCategory(), payload.expenseByCategory());
                    ClientData.setListingExpiryMinutes(payload.listingExpiryMinutes());
                    ClientData.setMarketLimits(payload.maxListings(), payload.maxListingPrice(),
                            payload.maxTransactionAmount(), payload.transferEnabled());
                    ClientData.setEnabledDenominations(payload.enabledDenominations());
                }));

        ClientPlayNetworking.registerGlobalReceiver(
                com.koko.marketbox.networking.packet.SyncBlacklistPacket.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientData.setBlacklist(payload.itemIds())));

        ClientPlayNetworking.registerGlobalReceiver(SyncLeaderboardPacket.ID, (payload, context) ->
                context.client().execute(() -> ClientData.setLeaderboard(payload.entries())));

        ClientPlayNetworking.registerGlobalReceiver(SyncMarketListingsPacket.ID, (payload, context) ->
                context.client().execute(() ->
                        ClientData.acceptListingChunk(payload.page(), payload.totalPages(), payload.listings())));

        ClientPlayNetworking.registerGlobalReceiver(SyncAdminStatusPacket.ID, (payload, context) ->
                context.client().execute(() -> ClientData.setPermissionLevel(payload.permissionLevel())));

        ClientPlayNetworking.registerGlobalReceiver(SyncDealPricesPacket.ID, (payload, context) ->
                context.client().execute(() -> ClientData.setDealPrices(payload.prices())));

        ClientPlayNetworking.registerGlobalReceiver(SyncEconomyPacket.ID, (payload, context) ->
                context.client().execute(() -> ClientData.setEconomy(
                        payload.onlineTotal(), payload.onlineCount(),
                        payload.interestIssued(), payload.adminIssued(),
                        payload.feeDestroyed(), payload.feeToTreasury(),
                        payload.listingCount())));

        ClientPlayNetworking.registerGlobalReceiver(MarketListingAddedPacket.ID, (payload, context) ->
                context.client().execute(() -> ClientData.addMarketListing(payload.listing())));

        ClientPlayNetworking.registerGlobalReceiver(MarketListingRemovedPacket.ID, (payload, context) ->
                context.client().execute(() -> ClientData.removeMarketListing(payload.listingId())));

        ClientPlayNetworking.registerGlobalReceiver(ActionFeedbackPacket.ID, (payload, context) ->
                context.client().execute(() -> {
                    Component message = payload.arg().isEmpty()
                            ? Component.translatable(payload.messageKey())
                            : Component.translatable(payload.messageKey(), payload.arg());
                    // 任何实现了 FeedbackReceiver 的界面都能收到，新增界面无需改这里
                    if (context.client().screen instanceof FeedbackReceiver receiver) {
                        receiver.showFeedback(message, payload.error());
                    }
                }));
    }
}
