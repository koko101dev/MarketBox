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
import com.koko.marketbox.networking.packet.MarketListingRemovedPacket;
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
 * 客户端 -> 服务端：购买市场挂单。服务端权威校验余额与归属。
 */
public class PurchaseMarketItemPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PurchaseMarketItemPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("purchase_market_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseMarketItemPacket> CODEC =
            StreamCodec.ofMember(PurchaseMarketItemPacket::write, PurchaseMarketItemPacket::new);

    private final UUID listingId;

    public PurchaseMarketItemPacket(UUID listingId) {
        this.listingId = listingId;
    }

    public PurchaseMarketItemPacket(RegistryFriendlyByteBuf buf) {
        this.listingId = buf.readUUID();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(listingId);
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

            long priceCents = Math.round(listing.price() * 100);
            if (priceCents <= 0) {
                return;
            }
            double cleanPrice = priceCents / 100.0;

            PlayerBalance buyerBalance = PlayerBalanceAttachment.get(buyer);
            if (buyerBalance.getBalanceCents() < priceCents) {
                ModMessages.sendToPlayer(buyer, new ActionFeedbackPacket("gui.marketbox.error.not_enough_balance"));
                return;
            }

            String itemNameStr = listing.item().getHoverName().getString();
            String detail = " x" + listing.item().getCount();
            buyerBalance.addBalance(-cleanPrice, "gui.marketbox.transaction.market_buy_prefix:" + itemNameStr + detail);

            ServerPlayer seller = buyer.getServer().getPlayerList().getPlayer(listing.sellerUUID());
            if (seller != null) {
                PlayerBalance sellerBalance = PlayerBalanceAttachment.get(seller);
                // 手续费从卖家实收中扣除并销毁，用于抑制通胀
                double fee = cleanPrice * Config.getMarketFeePercent() / 100.0;
                double sellerReceive = cleanPrice - fee;
                sellerBalance.addBalance(sellerReceive, "gui.marketbox.transaction.market_sell_prefix:" + itemNameStr + detail);
                PlayerEvents.syncPlayerBalance(seller);

                Component itemName = listing.item().getHoverName();
                Component quantity = Component.literal(String.valueOf(listing.item().getCount())).withStyle(ChatFormatting.AQUA);
                Component formattedPrice = Component.literal(String.format("%.2f", sellerReceive)).withStyle(ChatFormatting.YELLOW);
                seller.sendSystemMessage(Component.translatable("gui.marketbox.market.sold_notification",
                        itemName, quantity, formattedPrice));
            } else {
                manager.addPendingPayment(listing.sellerUUID(), cleanPrice * (1 - Config.getMarketFeePercent() / 100.0));
            }

            manager.removeListing(payload.listingId);

            ItemStack purchasedItem = listing.item().copy();
            if (!buyer.getInventory().add(purchasedItem)) {
                buyer.drop(purchasedItem, false);
            }
            buyer.containerMenu.broadcastChanges();

            PlayerEvents.syncPlayerBalance(buyer);
            ModMessages.sendToAll(buyer.getServer(), new MarketListingRemovedPacket(payload.listingId));
            ModMessages.sendToPlayer(buyer, new ActionFeedbackPacket("gui.marketbox.market.purchase_success", "", false));
            ServerEvents.markLeaderboardDirty();
            // 交易后立即落盘，避免异常退出丢失余额与物品变更
            PlayerEvents.persistPlayer(buyer);
        });
    }
}
