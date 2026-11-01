package com.koko.marketbox.networking.packet;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * 客户端 -> 服务端：购买市场挂单（支持部分购买）。
 *
 * quantity 为想买的数量，服务端会按剩余量、每人限购与余额三重裁剪，
 * 全部校验在服务端完成，客户端的数量只作为「意愿」。
 */
public class PurchaseMarketItemPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PurchaseMarketItemPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("purchase_market_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseMarketItemPacket> CODEC =
            StreamCodec.ofMember(PurchaseMarketItemPacket::write, PurchaseMarketItemPacket::new);

    private final UUID listingId;
    /** 想购买的数量；<=0 表示按整单购买 */
    private final int quantity;

    public PurchaseMarketItemPacket(UUID listingId) {
        this(listingId, 0);
    }

    public PurchaseMarketItemPacket(UUID listingId, int quantity) {
        this.listingId = listingId;
        this.quantity = quantity;
    }

    public PurchaseMarketItemPacket(RegistryFriendlyByteBuf buf) {
        this.listingId = buf.readUUID();
        this.quantity = buf.readVarInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(listingId);
        buf.writeVarInt(quantity);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(PurchaseMarketItemPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer buyer = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(buyer)) {
                return;
            }
            if (buyer == null) {
                return;
            }

            try {
                MarketManager manager = MarketManager.get(buyer.serverLevel());
                Optional<MarketListing> optionalListing = manager.getListings().stream()
                        .filter(l -> l.id().equals(payload.listingId))
                        .findFirst();

                if (optionalListing.isEmpty()) {
                    ModMessages.sendToPlayer(buyer, new ActionFeedbackPacket("gui.marketbox.market.error.not_found"));
                    return;
                }

                MarketListing listing = optionalListing.get();
                if (listing.sellerUUID().equals(buyer.getUUID())) {
                    ModMessages.sendToPlayer(buyer, new ActionFeedbackPacket("gui.marketbox.market.error.cannot_buy_own"));
                    return;
                }

                int available = Math.max(1, listing.item().getCount());
                int want = payload.quantity <= 0 ? available : Math.min(payload.quantity, available);

                // 每人限购：已买 + 本次 不能超过上限
                int limit = listing.perPlayerLimit();
                if (limit > 0) {
                    int bought = manager.getPurchased(listing.id(), buyer.getUUID());
                    int allowed = limit - bought;
                    if (allowed <= 0) {
                        ModMessages.sendToPlayer(buyer,
                                new ActionFeedbackPacket("gui.marketbox.market.error.limit_reached"));
                        return;
                    }
                    want = Math.min(want, allowed);
                }
                if (want <= 0) {
                    return;
                }

                double unitPrice = listing.price() / available;
                long costCents = Math.round(unitPrice * want * 100);
                if (costCents <= 0) {
                    return;
                }
                double cost = costCents / 100.0;

                PlayerBalance buyerBalance = PlayerBalanceAttachment.get(buyer);
                if (buyerBalance.getBalanceCents() < costCents) {
                    ModMessages.sendToPlayer(buyer, new ActionFeedbackPacket("gui.marketbox.error.not_enough_balance"));
                    return;
                }

                String itemNameStr = listing.item().getHoverName().getString();
                String detail = " x" + want;
                buyerBalance.addBalance(-cost, "gui.marketbox.transaction.market_buy_prefix:" + itemNameStr + detail);

                // 卖家收款（在线直接到账，离线进入待领取）
                double fee = cost * Config.getMarketFeePercent() / 100.0;
                double sellerReceive = cost - fee;
                boolean toTreasury = Config.isFeeToTreasury();
                if (fee > 0 && toTreasury) {
                    manager.addTreasury(fee);
                }
                com.koko.marketbox.util.EconomyStats.addFee(fee, toTreasury);

                ServerPlayer seller = buyer.getServer().getPlayerList().getPlayer(listing.sellerUUID());
                if (seller != null) {
                    PlayerBalance sellerBalance = PlayerBalanceAttachment.get(seller);
                    sellerBalance.addBalance(sellerReceive,
                            "gui.marketbox.transaction.market_sell_prefix:" + itemNameStr + detail);
                    PlayerEvents.syncPlayerBalance(seller);
                    PlayerEvents.persistPlayer(seller);

                    Component itemName = listing.item().getHoverName();
                    Component soldCount = Component.literal(String.valueOf(want)).withStyle(ChatFormatting.AQUA);
                    Component formattedPrice = Component.literal(String.format("%.2f", sellerReceive))
                            .withStyle(ChatFormatting.YELLOW);
                    seller.sendSystemMessage(Component.translatable("gui.marketbox.market.sold_notification",
                            itemName, soldCount, formattedPrice));
                } else {
                    manager.addPendingPayment(listing.sellerUUID(), sellerReceive);
                }

                // 交付物品
                ItemStack purchasedItem = listing.item().copy();
                purchasedItem.setCount(want);
                if (!buyer.getInventory().add(purchasedItem)) {
                    buyer.drop(purchasedItem, false);
                }
                buyer.containerMenu.broadcastChanges();

                manager.addPurchase(listing.id(), buyer.getUUID(), want);
                manager.recordDeal(listing.item(), unitPrice);
                // 成交价只同步给参与双方，避免每次交易都全服广播
                SyncDealPricesPacket dealUpdate = new SyncDealPricesPacket(manager.getDealPrices());
                ModMessages.sendToPlayer(buyer, dealUpdate);
                if (seller != null) {
                    ModMessages.sendToPlayer(seller, dealUpdate);
                }

                // 更新挂单：买完则移除，否则替换为剩余数量
                int remaining = available - want;
                if (remaining <= 0) {
                    manager.removeListing(listing.id());
                    manager.clearPurchases(listing.id());
                    ModMessages.sendToAll(buyer.getServer(), new MarketListingRemovedPacket(listing.id()));
                } else {
                    MarketListing updated = listing.withRemaining(remaining, listing.price() - cost);
                    manager.replaceListing(updated);
                    ModMessages.sendToAll(buyer.getServer(), new MarketListingRemovedPacket(listing.id()));
                    ModMessages.sendToAll(buyer.getServer(), new MarketListingAddedPacket(updated));
                }

                String bought = String.format("%d 个，花费 %.2f", want, cost);
                ModMessages.sendToPlayer(buyer,
                        new ActionFeedbackPacket("gui.marketbox.market.purchase_success_detail", bought, false));

                PlayerEvents.syncPlayerBalance(buyer);
                PlayerEvents.persistPlayer(buyer);
                ServerEvents.markLeaderboardDirty();
            } catch (Exception e) {
                MarketBox.LOGGER.error("购买挂单失败 类型={}", e.getClass().getSimpleName());
            }
        });
    }
}
