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
        // 1.20.2 起打开任意界面都会对世界背景做高斯模糊，会把界面整体观感拉糊。
        // 本模组的界面以信息阅读为主，这里统一关闭菜单背景模糊，保证可读性。
        applyMenuBlurSetting();

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
            int target = com.koko.marketbox.Config.isDisableMenuBlur() ? 0 : 5;
            Minecraft.getInstance().options.menuBackgroundBlurriness().set(target);
        } catch (Exception e) {
            com.koko.marketbox.MarketBox.LOGGER.error("应用菜单背景模糊配置失败：{}", e.getClass().getSimpleName());
        }
    }
}
