package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户端 -> 服务端：一次提交多条挂单。
 *
 * 单条上架包带 80ms 限流，批量提交十几种物品会被拒掉一部分，
 * 因此这里合并成一个包，限流只算一次；每条挂单仍逐条做完整校验。
 */
public class BulkListMarketItemPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BulkListMarketItemPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("bulk_list_market_item"));

    /** 一次最多提交的条数，与单人挂单上限一致 */
    private static final int MAX_ENTRIES = 20;
    private static final long MAX_PRICE_CENTS = 100_000_000L;

    public static final StreamCodec<RegistryFriendlyByteBuf, BulkListMarketItemPacket> CODEC =
            StreamCodec.ofMember(BulkListMarketItemPacket::write, BulkListMarketItemPacket::new);

    /** 单条待挂售物品 */
    public record Entry(ItemStack item, double price, int quantity) {
    }

    private final List<Entry> entries;
    private final int expiryMinutes;

    public BulkListMarketItemPacket(List<Entry> entries, int expiryMinutes) {
        this.entries = entries;
        this.expiryMinutes = expiryMinutes;
    }

    public BulkListMarketItemPacket(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            double price = buf.readDouble();
            int quantity = buf.readVarInt();
            this.entries.add(new Entry(stack, price, quantity));
        }
        this.expiryMinutes = buf.readVarInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.item());
            buf.writeDouble(entry.price());
            buf.writeVarInt(entry.quantity());
        }
        buf.writeVarInt(expiryMinutes);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(BulkListMarketItemPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player)) {
                return;
            }
            if (player == null || payload.entries.isEmpty()) {
                return;
            }
            if (payload.expiryMinutes <= 0 || payload.expiryMinutes > 360) {
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.market.error.invalid_duration"));
                return;
            }
            if (payload.entries.size() > MAX_ENTRIES) {
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.market.error.too_many_listings"));
                return;
            }

            try {
                MarketManager manager = MarketManager.get(player.serverLevel());
                int listed = 0;
                int failed = 0;
                // 只在进入循环前统计一次，避免每条都全量遍历挂单列表
                int currentCount = manager.countBySeller(player.getUUID());

                for (Entry entry : payload.entries) {
                    if (currentCount >= 20) {
                        failed++;
                        continue;
                    }
                    if (listItem(player, manager, entry, payload.expiryMinutes)) {
                        listed++;
                        currentCount++;
                    } else {
                        failed++;
                    }
                }

                if (listed > 0) {
                    PlayerEventsFacade.syncAndPersist(player);
                    String detail = listed + (failed > 0 ? (" / " + (listed + failed)) : "");
                    ModMessages.sendToPlayer(player, new ActionFeedbackPacket(
                            "gui.marketbox.market.bulk_done", detail, false));
                } else {
                    ModMessages.sendToPlayer(player,
                            new ActionFeedbackPacket("gui.marketbox.market.bulk_all_failed"));
                }
            } catch (Exception e) {
                MarketBox.LOGGER.error("Bulk listing failed", e);
            }
        });
    }

    /** 单条上架：校验价格与背包数量，成功后扣物并写入挂单 */
    private static boolean listItem(ServerPlayer player, MarketManager manager,
                                    Entry entry, int expiryMinutes) {
        ItemStack stack = entry.item();
        int quantity = entry.quantity();
        if (stack.isEmpty() || quantity <= 0) {
            return false;
        }
        if (entry.price <= 0 || Math.round(entry.price * 100) > MAX_PRICE_CENTS) {
            return false;
        }
        if (MoneyUtils.isCurrency(stack)) {
            return false;
        }

        // 统计背包里够不够
        int found = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (!invStack.isEmpty() && ItemStack.isSameItemSameComponents(invStack, stack)) {
                found += invStack.getCount();
            }
        }
        if (found < quantity) {
            return false;
        }

        // 扣除物品
        int remaining = quantity;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (invStack.isEmpty() || !ItemStack.isSameItemSameComponents(invStack, stack)) {
                continue;
            }
            int take = Math.min(remaining, invStack.getCount());
            invStack.shrink(take);
            remaining -= take;
        }
        if (remaining > 0) {
            // 理论上不会发生，保险起见放弃这条
            return false;
        }

        ItemStack listedStack = stack.copy();
        listedStack.setCount(quantity);
        MarketListing listing = new MarketListing(
                UUID.randomUUID(),
                listedStack,
                entry.price,
                player.getUUID(),
                player.getName().getString(),
                System.currentTimeMillis(),
                expiryMinutes,
                0);
        manager.addListing(listing);

        // 记录成交参考价，并广播给全服
        manager.recordDeal(listedStack, entry.price / quantity);
        ModMessages.sendToAll(player.getServer(), new MarketListingAddedPacket(listing));
        return true;
    }

    /** 避免直接依赖 PlayerEvents 造成循环引用的小门面 */
    private static final class PlayerEventsFacade {
        static void syncAndPersist(ServerPlayer player) {
            com.koko.marketbox.event.PlayerEvents.syncPlayerBalance(player);
            com.koko.marketbox.event.PlayerEvents.persistPlayer(player);
        }
    }
}
