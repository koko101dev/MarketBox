package com.koko.marketbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 配置界面里的「打开界面按键」绑定行。
 * 拆出原因：ConfigScreen 需要控制在 500 行规范上限内。
 */
final class ConfigKeyBind {

    private ConfigKeyBind() {
    }

    /** 生成显示当前按键的按钮；点击后进入监听状态 */
    static StyledButton createButton(ConfigScreen screen, int x, int y, int width, int height) {
        return new StyledButton(x, y, width, height, label(screen), b -> {
            screen.listeningForKey = true;
            screen.refreshWidgets();
        }, false, screen.listeningForKey);
    }

    private static Component label(ConfigScreen screen) {
        if (screen.listeningForKey) {
            return Component.translatable("gui.marketbox.config.key.waiting");
        }
        return KeyInit.OPEN_MARKETBOX_GUI.getTranslatedKeyMessage();
    }

    /**
     * 处理监听状态下的按键。
     * @return true 表示按键已被消费，调用方应直接返回
     */
    static boolean handleKeyPressed(ConfigScreen screen, int keyCode, int scanCode) {
        if (!screen.listeningForKey) {
            return false;
        }

        // ESC 只取消监听，不关闭界面
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            screen.listeningForKey = false;
            screen.refreshWidgets();
            return true;
        }

        KeyInit.OPEN_MARKETBOX_GUI.setKey(InputConstants.getKey(keyCode, scanCode));
        // 重建绑定缓存并写入 options.txt，重启后保持
        KeyMapping.resetMapping();
        if (Minecraft.getInstance().options != null) {
            Minecraft.getInstance().options.save();
        }
        screen.listeningForKey = false;
        screen.refreshWidgets();
        return true;
    }
}
