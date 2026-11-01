package com.koko.marketbox.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * 客户端入口：按键、提示框、HUD、下行包接收与界面热键。
 */
public class MarketBoxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 本模组的界面已各自跳过背景模糊（见各 Screen 的 renderBackground），
        // 不再依赖这个全局开关。此处额外应用一次，供希望关闭所有界面模糊的玩家使用；
        // 启动阶段设置可能被 options.txt 的加载覆盖，故再注册一次首帧补设。
        registerDeferredBlurApply();

        KeyInit.register();
        ClientEvents.register();
        MoneyHudOverlay.register();
        ClientPacketHandlers.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 使用 while 消费，避免同一 tick 内多次触发导致重复开界面
            while (KeyInit.OPEN_MARKETBOX_GUI.consumeClick()) {
                if (client.screen == null && client.player != null) {
                    client.setScreen(new MarketBoxScreen());
                }
            }
        });
    }

    /**
     * 按配置应用菜单背景模糊度。
     * 开启时置 0（关闭模糊），关闭时恢复原版默认值 5。
     * 供启动时与配置保存后调用，异常不影响模组其余功能。
     */
    public static void applyMenuBlurSetting() {
        try {
            // 只在配置要求关闭模糊时干预；否则不动用户的原始设置
            if (com.koko.marketbox.Config.isDisableMenuBlur()) {
                Minecraft.getInstance().options.menuBackgroundBlurriness().set(0);
            }
        } catch (Exception e) {
            com.koko.marketbox.MarketBox.LOGGER.error("Failed to apply menu-blur setting", e);
        }
    }

    /** 首次 tick 时应用一次：过早设置会被 options.txt 的加载结果覆盖 */
    private static boolean blurApplied = false;

    public static void registerDeferredBlurApply() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!blurApplied) {
                blurApplied = true;
                applyMenuBlurSetting();
            }
        });
    }
}
