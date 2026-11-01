package com.koko.marketbox.client;

import com.koko.marketbox.Config;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HUD 叠加层：在屏幕上显示总资产（余额 + 背包现金）。
 */
public final class MoneyHudOverlay {
    /** 距屏幕边缘的留白：贴边会和 F3 调试信息、其他模组 HUD 挤在一起 */
    private static final int PADDING = 4;
    private static final int INNER_PADDING = 4;

    /** 总资产缓存：背包遍历每帧执行开销较大，250ms 刷新一次足够 */
    private static final long CACHE_MS = 250;
    private static double cachedTotal = -1;
    private static String cachedText = "";
    private static long cachedAt = 0;

    private MoneyHudOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(MoneyHudOverlay::render);
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker tickCounter) {
        if (!Config.isShowHud()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        long now = System.currentTimeMillis();
        if (cachedTotal < 0 || now - cachedAt > CACHE_MS) {
            cachedTotal = ClientData.getPlayerBalance() + MoneyUtils.getInventoryMoneyTotal(minecraft.player);
            cachedText = MoneyUtils.formatValue(cachedTotal);
            cachedAt = now;
        }
        String formattedMoney = cachedText;

        Component prefix = Component.translatable("gui.marketbox.hud_total_prefix");
        String colon = ": ";

        int prefixWidth = minecraft.font.width(prefix);
        int colonWidth = minecraft.font.width(colon);
        int valueWidth = minecraft.font.width(formattedMoney);

        int totalContentWidth = prefixWidth + colonWidth + valueWidth;
        int textHeight = minecraft.font.lineHeight;

        float scale = (float) Config.getHudScale();
        Config.HudTextStyle style = Config.getHudTextStyle();

        int boxWidth = totalContentWidth + INNER_PADDING * 2;
        int boxHeight = textHeight + INNER_PADDING * 2;

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int x = 0;
        int y = 0;

        Config.HudPosition position = Config.getHudPosition();
        int xOffset = Config.getHudXOffset();
        int yOffset = Config.getHudYOffset();

        switch (position) {
            case TOP_LEFT -> {
                x = PADDING + xOffset;
                y = PADDING + yOffset;
            }
            case TOP_RIGHT -> {
                x = width - (int) (boxWidth * scale) - PADDING + xOffset;
                y = PADDING + yOffset;
            }
            case BOTTOM_LEFT -> {
                x = PADDING + xOffset;
                y = height - (int) (boxHeight * scale) - PADDING + yOffset;
            }
            case BOTTOM_RIGHT -> {
                x = width - (int) (boxWidth * scale) - PADDING + xOffset;
                y = height - (int) (boxHeight * scale) - PADDING + yOffset;
            }
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, scale);

        boolean hasBackground = style == Config.HudTextStyle.BACKGROUND;
        if (hasBackground) {
            guiGraphics.fill(0, 0, boxWidth, boxHeight, 0x99000000);
            guiGraphics.fill(0, 0, 2, boxHeight, 0xFF55FF55);
        }

        int renderX = hasBackground ? INNER_PADDING : 0;
        int renderY = hasBackground ? INNER_PADDING : 0;
        boolean shadow = style == Config.HudTextStyle.SHADOW;

        guiGraphics.drawString(minecraft.font, prefix, renderX, renderY, 0xDDDDDD, shadow);
        renderX += prefixWidth;
        guiGraphics.drawString(minecraft.font, colon, renderX, renderY, 0xDDDDDD, shadow);
        renderX += colonWidth;
        guiGraphics.drawString(minecraft.font, formattedMoney, renderX, renderY, 0x55FF55, shadow);

        guiGraphics.pose().popPose();
    }
}
