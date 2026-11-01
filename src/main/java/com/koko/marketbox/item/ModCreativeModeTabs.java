package com.koko.marketbox.item;

import com.koko.marketbox.MarketBox;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * 创造模式物品栏标签页注册。
 */
public class ModCreativeModeTabs {
    public static final CreativeModeTab MARKETBOX_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            MarketBox.id("marketbox_tab"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.MONEY_100))
                    .title(Component.translatable("creativetab.marketbox_tab"))
                    .displayItems((context, output) -> {
                        output.accept(ModItems.MONEY_0_01);
                        output.accept(ModItems.MONEY_0_1);
                        output.accept(ModItems.MONEY_1);
                        output.accept(ModItems.MONEY_5);
                        output.accept(ModItems.MONEY_10);
                        output.accept(ModItems.MONEY_20);
                        output.accept(ModItems.MONEY_50);
                        output.accept(ModItems.MONEY_100);
                    })
                    .build());

    /** 触发类加载，内部无逻辑 */
    public static void register() {
        // 静态字段已在类初始化阶段完成注册
    }
}
