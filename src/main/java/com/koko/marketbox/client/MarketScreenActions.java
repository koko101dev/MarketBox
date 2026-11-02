package com.koko.marketbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/**
 * MarketScreen 的按钮状态与交易动作。拆出以降低单文件行数，业务逻辑未改动。
 */
final class MarketScreenActions {
    private MarketScreenActions() {
    }

    static void updateCategoryButtons(MarketScreen screen) {
        for (int i = 0; i < screen.categoryButtons.size(); i++) {
            StyledButton btn = screen.categoryButtons.get(i);
            Category cat = Category.values()[i];
            btn.setHighlighted(cat == screen.currentCategory);
        }
    }

    static void updateWidgetStates(MarketScreen screen) {
        boolean active = !screen.isSelectingItem;
        screen.searchBox.visible = true; // Keep visible
        screen.searchBox.setEditable(active);
        
        // Also find and update the claim button and back button
        screen.children().forEach(r -> {
            if (r instanceof StyledButton btn) {
                // Keep everything visible but control active state
                btn.visible = true;
                // 侧边栏导航始终可用，其余按钮跟随选择状态
                if (!isNavButton(screen, btn)) {
                    btn.active = active;
                }
            }
        });
    }

    /** 侧边栏导航项：按坐标判断（位于左侧导航区内），新增页面时不会漏 */
    private static boolean isNavButton(MarketScreen screen, StyledButton btn) {
        return btn.getX() < screen.leftPos;
    }

}
