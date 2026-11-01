package com.koko.marketbox.market;

import com.koko.marketbox.MarketBox;

import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.networking.ModMessages;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 市场数据：挂单、待领取退还物品、待入账款项。
 * 1.21 起 SavedData 的读写带注册表上下文，物品栈统一用 ItemStack.CODEC 序列化。
 */
public class MarketManager extends SavedData {
    private static final String DATA_NAME = "marketbox_market";

    private final List<MarketListing> listings = new CopyOnWriteArrayList<>();
    private final Map<UUID, List<ItemStack>> returnedItems = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingPaymentsCents = new ConcurrentHashMap<>();

    private static final SavedData.Factory<MarketManager> FACTORY =
            new SavedData.Factory<>(MarketManager::new, MarketManager::load, null);

    public static MarketManager get(ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void addListing(MarketListing listing) {
        listings.add(listing);
        setDirty();
    }

    /** 统计某玩家当前挂单数量，用于限制单人挂单上限 */
    public int countBySeller(UUID seller) {
        int count = 0;
        for (MarketListing listing : listings) {
            if (listing.sellerUUID().equals(seller)) {
                count++;
            }
        }
        return count;
    }

    public void removeListing(UUID id) {
        listings.removeIf(listing -> listing.id().equals(id));
        setDirty();
    }

    /** 定期扫描过期挂单，退还原物并通知在线卖家 */
    public void checkAutoDelist(ServerLevel level) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Set<UUID> notifiedSellers = new HashSet<>();

        List<MarketListing> toRemove = new ArrayList<>();
        for (MarketListing listing : listings) {
            long expiryTimeMs = (long) listing.expiryMinutes() * 60 * 1000;
            if (now - listing.createdAt() >= expiryTimeMs) {
                toRemove.add(listing);
            }
        }

        for (MarketListing listing : toRemove) {
            if (listings.remove(listing)) {
                addReturnedItem(listing.sellerUUID(), listing.item());
                notifiedSellers.add(listing.sellerUUID());
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        setDirty();
        // 逐条通知移除，替代原先的全量广播
        for (MarketListing listing : toRemove) {
            ModMessages.sendToAll(level.getServer(), new com.koko.marketbox.networking.packet.MarketListingRemovedPacket(listing.id()));
        }

        for (UUID sellerUUID : notifiedSellers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(sellerUUID);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("gui.marketbox.market.returned_items_notification"));
                PlayerEvents.syncPlayerBalance(player);
            }
        }
    }

    public void addReturnedItem(UUID playerUUID, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        returnedItems.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    public List<ItemStack> getReturnedItems(UUID playerUUID) {
        List<ItemStack> items = returnedItems.get(playerUUID);
        if (items == null) {
            return new ArrayList<>();
        }
        // 防御性拷贝，避免调用方修改内部集合
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            copy.add(stack.copy());
        }
        return copy;
    }

    public List<ItemStack> claimReturnedItems(UUID playerUUID) {
        List<ItemStack> items = returnedItems.remove(playerUUID);
        if (items != null) {
            setDirty();
            return items;
        }
        return new ArrayList<>();
    }

    public void addPendingPayment(UUID playerUUID, double amount) {
        if (amount <= 0) {
            return;
        }
        long cents = Math.round(amount * 100);
        pendingPaymentsCents.merge(playerUUID, cents, Long::sum);
        setDirty();
    }

    /** 查询待领取付款（不消费），用于界面角标 */
    public double getPendingPayment(UUID playerUUID) {
        Long cents = pendingPaymentsCents.get(playerUUID);
        return cents == null ? 0 : cents / 100.0;
    }

    public double claimPendingPayment(UUID playerUUID) {
        Long cents = pendingPaymentsCents.remove(playerUUID);
        if (cents != null) {
            setDirty();
            return cents / 100.0;
        }
        return 0;
    }

    public List<MarketListing> getListings() {
        return new ArrayList<>(listings);
    }

    // ---- 持久化 ----

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();
        for (MarketListing listing : listings) {
            CompoundTag listingTag = new CompoundTag();
            listingTag.putUUID("id", listing.id());
            listingTag.put("item", writeItem(listing.item(), registries));
            listingTag.putDouble("price", listing.price());
            listingTag.putUUID("sellerUUID", listing.sellerUUID());
            listingTag.putString("sellerName", listing.sellerName());
            listingTag.putLong("createdAt", listing.createdAt());
            listingTag.putInt("expiryMinutes", listing.expiryMinutes());
            listTag.add(listingTag);
        }
        tag.put("listings", listTag);

        CompoundTag returnedTag = new CompoundTag();
        for (Map.Entry<UUID, List<ItemStack>> entry : returnedItems.entrySet()) {
            ListTag itemTags = new ListTag();
            for (ItemStack stack : entry.getValue()) {
                itemTags.add(writeItem(stack, registries));
            }
            returnedTag.put(entry.getKey().toString(), itemTags);
        }
        tag.put("returnedItems", returnedTag);

        CompoundTag paymentsTag = new CompoundTag();
        for (Map.Entry<UUID, Long> entry : pendingPaymentsCents.entrySet()) {
            paymentsTag.putLong(entry.getKey().toString(), entry.getValue());
        }
        tag.put("pendingPaymentsCents", paymentsTag);
        return tag;
    }

    /**
     * 从存档读取。
     * 每条记录独立 try-catch：单条数据损坏只跳过该条并记日志，
     * 不能让一条坏数据导致整个市场数据（乃至世界）加载失败。
     */
    public static MarketManager load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketManager manager = new MarketManager();

        if (tag.contains("listings", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("listings", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                try {
                    CompoundTag listingTag = listTag.getCompound(i);
                    manager.listings.add(new MarketListing(
                            listingTag.getUUID("id"),
                            readItem(listingTag.get("item"), registries),
                            listingTag.getDouble("price"),
                            listingTag.getUUID("sellerUUID"),
                            listingTag.getString("sellerName"),
                            listingTag.getLong("createdAt"),
                            listingTag.contains("expiryMinutes") ? listingTag.getInt("expiryMinutes") : 60
                    ));
                } catch (Exception e) {
                    MarketBox.LOGGER.warn("[MarketBox] 跳过第 {} 条无法解析的挂单：{}", i, e.getClass().getSimpleName());
                }
            }
        }

        if (tag.contains("returnedItems", Tag.TAG_COMPOUND)) {
            CompoundTag returnedTag = tag.getCompound("returnedItems");
            for (String key : returnedTag.getAllKeys()) {
                try {
                    UUID ownerId = UUID.fromString(key);
                    ListTag itemTags = returnedTag.getList(key, Tag.TAG_COMPOUND);
                    List<ItemStack> list = new ArrayList<>();
                    for (int i = 0; i < itemTags.size(); i++) {
                        try {
                            list.add(readItem(itemTags.get(i), registries));
                        } catch (Exception e) {
                            MarketBox.LOGGER.warn("[MarketBox] 跳过一件无法解析的退货物品：{}", e.getClass().getSimpleName());
                        }
                    }
                    if (!list.isEmpty()) {
                        manager.returnedItems.put(ownerId, list);
                    }
                } catch (Exception e) {
                    MarketBox.LOGGER.warn("[MarketBox] 跳过格式异常的退货记录 key={}：{}", key, e.getClass().getSimpleName());
                }
            }
        }

        if (tag.contains("pendingPaymentsCents", Tag.TAG_COMPOUND)) {
            CompoundTag paymentsTag = tag.getCompound("pendingPaymentsCents");
            for (String key : paymentsTag.getAllKeys()) {
                try {
                    manager.pendingPaymentsCents.put(UUID.fromString(key), paymentsTag.getLong(key));
                } catch (Exception e) {
                    MarketBox.LOGGER.warn("[MarketBox] 跳过格式异常的待领取记录 key={}", key);
                }
            }
        }
        return manager;
    }

    /** 物品栈 -> NBT，需要注册表上下文以解析数据组件 */
    private static Tag writeItem(ItemStack stack, HolderLookup.Provider registries) {
        return ItemStack.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .result()
                .orElseGet(CompoundTag::new);
    }

    private static ItemStack readItem(@Nullable Tag tag, HolderLookup.Provider registries) {
        if (tag == null) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .result()
                .orElse(ItemStack.EMPTY);
    }
}
