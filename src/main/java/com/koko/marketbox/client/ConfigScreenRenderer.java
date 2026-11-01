package com.koko.marketbox.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.EditBox;

/**
 * ConfigScreen 的渲染辅助：仅负责绘制，不持有状态。
 * 拆出原因：ConfigScreen 超过 500 行规范上限。
 */
final class ConfigScreenRenderer {
    private ConfigScreenRenderer() {
    }

    static void renderInlineValidation(ConfigScreen screen, GuiGraphics guiGraphics) {
        // Interest Rate validation
        if (screen.interestRateBox != null && !screen.interestRateBox.getValue().isEmpty()) {
            try {
                double val = Double.parseDouble(screen.interestRateBox.getValue());
                if (val < 0.0 || val > 100.0) {
                    renderFieldError(screen, guiGraphics, screen.interestRateBox, Component.translatable("gui.marketbox.config.error.rate_range", "0", "100"));
                }
            } catch (NumberFormatException ignored) {}
        }

        // Interest Interval validation
        if (screen.interestIntervalBox != null && !screen.interestIntervalBox.getValue().isEmpty()) {
            try {
                int val = Integer.parseInt(screen.interestIntervalBox.getValue());
                if (val < 1) {
                    renderFieldError(screen, guiGraphics, screen.interestIntervalBox, Component.translatable("gui.marketbox.config.error.interval_min", "1"));
                }
            } catch (NumberFormatException ignored) {}
        }

        // HUD Scale validation
        if (screen.hudScaleBox != null && !screen.hudScaleBox.getValue().isEmpty()) {
            try {
                double val = Double.parseDouble(screen.hudScaleBox.getValue());
                if (val < 0.5) {
                    renderFieldError(screen, guiGraphics, screen.hudScaleBox, Component.translatable("gui.marketbox.config.error.scale_min", "0.5"));
                } else if (val > 3.0) {
                    renderFieldError(screen, guiGraphics, screen.hudScaleBox, Component.translatable("gui.marketbox.config.error.scale_max", "3.0"));
                }
            } catch (NumberFormatException ignored) {}
        }
    }

    static void renderFieldError(ConfigScreen screen, GuiGraphics guiGraphics, EditBox box, Component error) {
        int errorX = box.getX() - net.minecraft.client.Minecraft.getInstance().font.width(error) - 4;
        int errorY = box.getY() + (box.getHeight() - 8) / 2;
        if (errorX < screen.contentLeft + ConfigScreen.PADDING) {
            // Not enough space on left, show below the screen.box
            errorX = box.getX();
            errorY = box.getY() + box.getHeight() + 2;
        }
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, error, errorX, errorY, 0xFF5555, false);
    }

    static void renderSectionHeader(ConfigScreen screen, GuiGraphics guiGraphics, Component title, int y) {
        int barY = y + 4;
        guiGraphics.fill(screen.contentLeft + ConfigScreen.PADDING, barY, screen.contentLeft + screen.contentWidth - ConfigScreen.PADDING, barY + 18, 0xFF252525);
        guiGraphics.renderOutline(screen.contentLeft + ConfigScreen.PADDING, barY, screen.contentWidth - ConfigScreen.PADDING * 2, 18, 0xFF444444);
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, title, screen.contentLeft + screen.contentWidth / 2, barY + 5, 0xFFAA00);
    }

    static void renderConfigRow(ConfigScreen screen, GuiGraphics guiGraphics, Component label, int y) {
        int labelX = screen.contentLeft + ConfigScreen.PADDING + 4;
        int labelY = y + screen.rowHeight / 2 - 4;
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, label, labelX, labelY, 0xDDDDDD, false);
    }

    /**
     * 右侧滚动条与按钮栏上方的渐隐阴影。
     * 阴影只在未滚到底时出现，提示下方仍有内容。
     */
    static void renderScrollBar(ConfigScreen screen, GuiGraphics guiGraphics, int contentBottom) {
        if (screen.maxScroll <= 0) {
            return;
        }
        int visibleHeight = screen.guiHeight - ConfigScreen.BOTTOM_BAR_HEIGHT;
        int contentHeight = visibleHeight + screen.maxScroll;
        int barHeight = Math.max(24, visibleHeight * visibleHeight / contentHeight);
        int barY = screen.topPos + (visibleHeight - barHeight) * screen.scrollOffset / screen.maxScroll;
        int barX = screen.contentLeft + screen.contentWidth - 5;

        guiGraphics.fill(barX, screen.topPos + 2, barX + 5, screen.topPos + visibleHeight - 2, 0x44FFFFFF);
        guiGraphics.fill(barX, barY, barX + 5, barY + barHeight, 0xFFCCCCCC);

        if (screen.scrollOffset < screen.maxScroll) {
            guiGraphics.fill(screen.leftPos, contentBottom - 6,
                    screen.leftPos + screen.guiWidth, contentBottom, 0x55000000);
            guiGraphics.fill(screen.leftPos, contentBottom - 3,
                    screen.leftPos + screen.guiWidth, contentBottom, 0x77000000);
        }
    }
}
