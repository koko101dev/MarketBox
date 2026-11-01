package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.packet.MarketListingAddedPacket;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 客户端 -> 服务端：上架物品。所有校验与背包扣除均在服务端完成，不信任客户端数据。
 */
public class ListMarketItemPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ListMarketItemPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("list_market_item"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ListMarketItemPacket> CODEC =
            StreamCodec.ofMember(ListMarketItemPacket::write, ListMarketItemPacket::new);

    /** 单人最多同时挂单数量，避免刷屏与同步包膨胀 */
    private static final int MAX_LISTINGS_PER_PLAYER = 20;
    /** 单价上限（分），防止金额溢出 */
    private static final long MAX_PRICE_CENTS = 100_000_000L;

    private final ItemStack item;
    private final double price;
    private final int quantity;
    private final int expiryMinutes;
    /** 每人限购数量，0 表示不限 */
    private final int perPlayerLimit;

    public ListMarketItemPacket(ItemStack item, double price, int quantity, int expiryMinutes, int perPlayerLimit) {
        this.item = item;
        this.price = price;
        this.quantity = quantity;
        this.expiryMinutes = expiryMinutes;
        this.perPlayerLimit = perPlayerLimit;
    }

    public ListMarketItemPacket(RegistryFriendlyByteBuf buf) {
        this.item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        this.price = buf.readDouble();
        this.quantity = buf.readVarInt();
        this.expiryMinutes = buf.readVarInt();
        this.perPlayerLimit = buf.readVarInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
        buf.writeDouble(price);
        buf.writeVarInt(quantity);
        buf.writeVarInt(expiryMinutes);
        buf.writeVarInt(perPlayerLimit);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(ListMarketItemPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player)) {
                return;
            }
            if (player == null || payload.item.isEmpty()) {
                return;
            }

            if (payload.price <= 0) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.error.invalid_price"));
                return;
            }
            if (Math.round(payload.price * 100) > MAX_PRICE_CENTS) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.error.price_too_high"));
                return;
            }
            if (payload.quantity <= 0) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.error.invalid_quantity"));
                return;
            }
            if (payload.expiryMinutes <= 0 || payload.expiryMinutes > 60) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.error.invalid_duration"));
                return;
            }
            MarketManager manager = MarketManager.get(player.serverLevel());
            if (manager.countBySeller(player.getUUID()) >= MAX_LISTINGS_PER_PLAYER) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.error.too_many_listings"));
                return;
            }
            if (MoneyUtils.isCurrency(payload.item)) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.error.cannot_list_currency"));
                return;
            }

            // 第一遍统计、第二遍扣除：避免中途失败导致物品丢失或复制
            int totalFound = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (!invStack.isEmpty() && ItemStack.isSameItemSameComponents(invStack, payload.item)) {
                    totalFound += invStack.getCount();
                }
            }

            if (totalFound < payload.quantity) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.item_not_found"));
                return;
            }

            int remaining = payload.quantity;
            for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                ItemStack invStack = player.getInventory().getItem(i);
                if (!invStack.isEmpty() && ItemStack.isSameItemSameComponents(invStack, payload.item)) {
                    int toShrink = Math.min(invStack.getCount(), remaining);
                    invStack.shrink(toShrink);
                    remaining -= toShrink;
                }
            }

            ItemStack itemToSell = payload.item.copy();
            itemToSell.setCount(payload.quantity);

            MarketListing created = new MarketListing(
                    UUID.randomUUID(),
                    itemToSell,
                    payload.price,
                    player.getUUID(),
                    player.getName().getString(),
                    System.currentTimeMillis(),
                    payload.expiryMinutes,
                    payload.perPlayerLimit
            );
            manager.addListing(created);

            player.containerMenu.broadcastChanges();
            // 增量推送：只发这一条，带宽不再随在线人数平方增长
            ModMessages.sendToAll(player.getServer(), new MarketListingAddedPacket(created));
            ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.list_success", "", false));
        });
    }
}
