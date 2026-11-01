package com.koko.marketbox.client;

import com.koko.marketbox.MarketBox;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * 按键绑定注册。Fabric 通过 KeyBindingHelper 注册，默认不绑定按键。
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
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        ));
    }
}
