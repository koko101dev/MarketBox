package com.koko.marketbox.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.tags.ItemTags;

/**
 * 市场物品分类。原为 MarketScreen 的内部枚举，拆出以降低单文件行数。
 */

enum Category {
    ALL("all", s -> true),
    BLOCKS("blocks", s -> s.getItem() instanceof BlockItem),
    WEAPONS("weapons", s -> {
        Item i = s.getItem();
        return i instanceof SwordItem || 
               i instanceof AxeItem || 
               i instanceof ProjectileWeaponItem || 
               i instanceof TridentItem ||
               i instanceof ShieldItem;
    }),
    TOOLS("tools", s -> {
        Item i = s.getItem();
        // 斧同时属于 DiggerItem 与 AxeItem：这里排除掉，否则「武器」和「工具」两个页签会出现同一批货
        return (i instanceof DiggerItem && !(i instanceof AxeItem)) || // 镐、铲、锄
               i instanceof ShearsItem || 
               i instanceof FishingRodItem ||
               i instanceof BucketItem ||
               i instanceof CompassItem ||
               i.getDescriptionId().equals("item.minecraft.clock") ||
               i instanceof BrushItem ||
               i instanceof SpyglassItem;
    }),
    ARMOR("armor", s -> s.getItem() instanceof ArmorItem || s.getItem() instanceof ElytraItem),
    FOOD("food", s -> s.has(net.minecraft.core.component.DataComponents.FOOD)),
    RESOURCES("resources", s -> s.is(ItemTags.COALS) || isResourceLike(s)),
    REDSTONE("redstone", s -> {
        if (s.is(Items.REDSTONE)) return true;
        Item i = s.getItem();
        if (i instanceof BlockItem) {
            Block b = ((BlockItem)i).getBlock();
            return b instanceof DiodeBlock || 
                   b instanceof RedstoneLampBlock ||
                   b instanceof ObserverBlock ||
                   b instanceof ButtonBlock ||
                   b instanceof LeverBlock ||
                   b instanceof PressurePlateBlock;
        }
        return false;
    }),
    OTHERS("others", s -> false);

    final String id;
    final java.util.function.Predicate<ItemStack> filter;

    Category(String id, java.util.function.Predicate<ItemStack> filter) {
        this.id = id;
        this.filter = filter;
    }

    public Component getDisplayName() {
        return Component.translatable("gui.marketbox.market.category." + id);
    }
    
    /** 「其他」判定结果的缓存：同种物品只跑一遍全分类扫描 */
    private static final java.util.Map<net.minecraft.world.item.Item, Boolean> OTHERS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public boolean matches(ItemStack stack) {
        if (this == ALL) return true;
        if (this == OTHERS) {
            return OTHERS_CACHE.computeIfAbsent(stack.getItem(), item -> {
                ItemStack probe = new ItemStack(item);
                for (Category c : values()) {
                    if (c != ALL && c != OTHERS && c.filter.test(probe)) {
                        return false;
                    }
                }
                return true;
            });
        }
        return filter.test(stack);
    }

    static boolean isResourceLike(ItemStack stack) {
        String path = stack.getItem().getDescriptionId();
        return path.endsWith("_ingot")
                || path.endsWith("_gem")
                || path.endsWith("_ore")
                || path.contains("diamond")
                || path.contains("emerald")
                || path.contains("quartz")
                || path.contains("amethyst")
                || path.contains("lapis")
                || path.contains("raw_");
    }
}
