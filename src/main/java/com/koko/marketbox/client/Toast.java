package com.koko.marketbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 屏幕底部的操作提示条。
 * 带底板、左侧色条与状态图标，成功为绿色、失败为红色，停留后淡出。
 * 统一替代各界面原先「纯文字贴在按钮上方」的实现。
 */
final class Toast {
    private static final long HOLD_MS = 2200;
    private static final long FADE_MS = 600;
    /** 距屏幕底部的留白 */
    private static final int BOTTOM_MARGIN = 28;

    private Component message;
    private boolean error;
    private long shownAt;

    void show(Component message, boolean error) {
        this.message = message;
        this.error = error;
        this.shownAt = System.currentTimeMillis();
    }

    void clear() {
        this.message = null;
    }

    /** 在屏幕底部居中绘制；无内容时不画任何东西 */
    void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (message == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - shownAt;
        if (elapsed > HOLD_MS + FADE_MS) {
            message = null;
            return;
        }

        float fade = elapsed <= HOLD_MS ? 1.0f : 1.0f - (elapsed - HOLD_MS) / (float) FADE_MS;
        int alpha = (int) (Math.max(0.0f, Math.min(1.0f, fade)) * 255);
        if (alpha <= 4) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        String icon = error ? "!" : "\u2713";
        int iconWidth = font.width(icon);

        int padX = 10;
        int padY = 6;
        int gap = 6;
        // 宽度不超过屏幕：长提示在窄窗口里会左右都出屏，前后半句都看不全
        int maxBox = Math.max(80, screenWidth - 16);
        int textWidth = Math.min(font.width(message), maxBox - padX * 2 - iconWidth - gap);
        // 放不下时截断，避免文字直接画到框外
        Component shown = textWidth < font.width(message)
                ? Component.literal(font.plainSubstrByWidth(message.getString(), textWidth))
                : message;
        int boxWidth = padX * 2 + iconWidth + gap + textWidth;
        int boxHeight = font.lineHeight + padY * 2;
        int x = Math.max(2, (screenWidth - boxWidth) / 2);
        int y = screenHeight - boxHeight - BOTTOM_MARGIN;

        int accent = (alpha << 24) | (error ? 0xFF5555 : 0x55FF55);

        // 底板 + 左侧状态色条 + 边框
        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, (alpha << 24) | 0xE6101010);
        guiGraphics.fill(x, y, x + 3, y + boxHeight, accent);
        guiGraphics.renderOutline(x, y, boxWidth, boxHeight, (alpha << 24) | 0x666666);

        guiGraphics.drawString(font, icon, x + padX, y + padY, accent, false);
        guiGraphics.drawString(font, shown, x + padX + iconWidth + gap, y + padY,
                (alpha << 24) | 0xFFFFFF, false);
    }
}
