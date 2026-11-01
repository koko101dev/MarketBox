package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.packet.MarketListingRemovedPacket;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * 客户端 -> 服务端：下架自己的挂单并取回物品。
 */
public class DelistMarketItemPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DelistMarketItemPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("delist_market_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DelistMarketItemPacket> CODEC =
            StreamCodec.ofMember(DelistMarketItemPacket::write, DelistMarketItemPacket::new);

    private final UUID listingId;

    public DelistMarketItemPacket(UUID listingId) {
        this.listingId = listingId;
    }

    public DelistMarketItemPacket(RegistryFriendlyByteBuf buf) {
        this.listingId = buf.readUUID();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(listingId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(DelistMarketItemPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player)) {
                return;
            }
            if (player == null) {
                return;
            }

            MarketManager manager = MarketManager.get(player.serverLevel());
            Optional<MarketListing> listingOpt = manager.getListings().stream()
                    .filter(l -> l.id().equals(payload.listingId))
                    .findFirst();

            if (listingOpt.isEmpty()) {
                return;
            }

            MarketListing listing = listingOpt.get();
            // 安全校验：仅卖家本人可下架
            if (!listing.sellerUUID().equals(player.getUUID())) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.not_seller"));
                return;
            }

            manager.removeListing(payload.listingId);

            ItemStack returnedItem = listing.item().copy();
            if (!player.getInventory().add(returnedItem)) {
                player.drop(returnedItem, false);
            }
            player.containerMenu.broadcastChanges();

            ModMessages.sendToAll(player.getServer(), new MarketListingRemovedPacket(payload.listingId));
            ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.delist_success", "", false));
        });
    }
}
