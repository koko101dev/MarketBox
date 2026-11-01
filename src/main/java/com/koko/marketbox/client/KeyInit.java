package com.koko.marketbox.client;

import com.koko.marketbox.MarketBox;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * 按键绑定注册。
 *
 * 默认键为 K：Tab 被原版玩家列表占用，M / B / J 常被地图、背包、物品管理器等模组占用，
 * 同一个键绑定多个功能时只有先注册的一方收到事件，因此选一个冲突最少的键。
 * 玩家可在「设置 → 按键」或原版控制设置里重绑。
 */
public final class KeyInit {
    public static final String CATEGORY = "key.categories." + MarketBox.MODID;

    public static KeyMapping OPEN_MARKETBOX_GUI;

    private KeyInit() {
    }

    public static void register() {
        OPEN_MARKETBOX_GUI = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key." + MarketBox.MODID + ".open_gui",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                CATEGORY
        ));

        // 兜底：按键被其他模组占用时，仍可用 /mb 打开界面
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT
                .register((dispatcher, registryAccess) -> dispatcher.register(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("mb")
                                .executes(context -> {
                                    net.minecraft.client.Minecraft.getInstance()
                                            .setScreen(new MarketBoxScreen());
                                    return 1;
                                })));
    }
}
