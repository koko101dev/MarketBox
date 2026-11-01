package com.koko.marketbox.item;

import com.koko.marketbox.MarketBox;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * 货币物品注册。
 * Fabric 无 DeferredRegister，改用原版 Registry.register；静态字段在类加载时完成注册，
 * 因此主类必须先触及本类（register() 仅用于触发类初始化）。
 */
public class ModItems {
    public static final Item MONEY_0_01 = register("money_0_01");
    public static final Item MONEY_0_1 = register("money_0_1");
    public static final Item MONEY_1 = register("money_1");
    public static final Item MONEY_5 = register("money_5");
    public static final Item MONEY_10 = register("money_10");
    public static final Item MONEY_20 = register("money_20");
    public static final Item MONEY_50 = register("money_50");
    public static final Item MONEY_100 = register("money_100");

    private static Item register(String name) {
        return Registry.register(BuiltInRegistries.ITEM, MarketBox.id(name), new Item(new Item.Properties()));
    }

    /** 触发类加载，内部无逻辑 */
    public static void register() {
        // 静态字段已在类初始化阶段完成注册
    }
}
