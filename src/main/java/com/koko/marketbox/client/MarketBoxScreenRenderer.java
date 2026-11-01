package com.koko.marketbox.client;

import com.koko.marketbox.client.ClientData;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * MarketBoxScreen 的渲染辅助：只做绘制，不持有状态。
 * 拆出原因：MarketBoxScreen 超过 500 行规范上限。
 */
final class MarketBoxScreenRenderer {
    private MarketBoxScreenRenderer() {
    }



    static void renderMiddleSection(MarketBoxScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = screen.leftPos + screen.leftSectionWidth + MarketBoxScreen.PADDING;
        int startY = screen.topPos + MarketBoxScreen.PADDING; 
        int middleWidth = screen.guiWidth - screen.leftSectionWidth;
        int middleCenterX = screen.leftPos + screen.leftSectionWidth + middleWidth / 2;
        

        // Render Title (Centered)
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.interest_info"), middleCenterX, startY, 0xDDDDDD);
        
        // Horizontal line under title
        guiGraphics.fill(startX, startY + 11, screen.leftPos + screen.leftSectionWidth + middleWidth - MarketBoxScreen.PADDING, startY + 12, 0xFF444444);
        
        int contentY = startY + 25;
        int maxWidth = middleWidth - MarketBoxScreen.PADDING * 2;
        
        // 1. Current Interest Rate
        String rateText = String.format("%.2f%%", ClientData.getInterestRate());
        drawValueWithScaling(screen, guiGraphics, Component.translatable("gui.marketbox.interest_rate"), rateText, startX, contentY, maxWidth, 0x55FF55);
        
        contentY += 22;
        
        // 2. Total Interest Generated
        String interestText = MoneyUtils.formatValue(ClientData.getTotalInterest());
        drawValueWithScaling(screen, guiGraphics, Component.translatable("gui.marketbox.total_interest"), interestText, startX, contentY, maxWidth, 0xFFFF55);
        
        contentY += 22;
        
        // 3. Predicted Interest
        double predictedInterest = ClientData.getPlayerBalance() * (ClientData.getInterestRate() / 100.0);
        String predictedText = MoneyUtils.formatValue(predictedInterest);
        drawValueWithScaling(screen, guiGraphics, Component.translatable("gui.marketbox.predicted_interest"), predictedText, startX, contentY, maxWidth, 0xFFAA00);

        contentY += 22;
        
        // 4. Next Interest Countdown
        long lastInterestTime = ClientData.getLastInterestTime();
        long intervalMillis = (long) ClientData.getInterestIntervalSeconds() * 1000;
        long nextInterestTime = lastInterestTime + intervalMillis;
        long remainingMillis = nextInterestTime - System.currentTimeMillis();
        
        String countdownText;
        if (remainingMillis <= 0) {
            countdownText = "00:00";
        } else {
            long seconds = (remainingMillis / 1000) % 60;
            long minutes = (remainingMillis / (1000 * 60)) % 60;
            long hours = (remainingMillis / (1000 * 60 * 60));
            if (hours > 0) {
                countdownText = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            } else {
                countdownText = String.format("%02d:%02d", minutes, seconds);
            }
        }
        
        drawValueWithScaling(screen, guiGraphics, Component.translatable("gui.marketbox.next_interest"), countdownText, startX, contentY, maxWidth, 0x55FFFF);

        // 5. 收益曲线：纯展示，用复利公式让玩家直观看到利率的长期效果
        renderInterestCurve(screen, guiGraphics, startX, contentY + 24, maxWidth);
    }

    /**
     * 14 天复利增长曲线。
     *
     * 用 Bresenham 连接采样点而不是逐列画矩形：逐列填充在斜率小时
     * 会断成一段一段的虚线，看起来像锯齿。这里每 8px 采一个点再连线，线条连续。
     * 同时补上横向网格与时间轴标签，让增长幅度可读。
     */
    private static void renderInterestCurve(MarketBoxScreen screen, GuiGraphics guiGraphics,
                                            int plotX, int titleY, int plotW) {
        if (plotW < 100) {
            return;
        }
        double rate = ClientData.getInterestRate() / 100.0;
        if (!(rate > 0) || Double.isInfinite(rate)) {
            return;
        }

        int interval = Math.max(60, ClientData.getInterestIntervalSeconds());
        int periodsPerDay = Math.max(1, Math.min(288, 86400 / interval));
        final int days = 14;

        double maxGain = 0;
        for (int d = 0; d <= days; d++) {
            double g = (Math.pow(1 + rate, (double) periodsPerDay * d) - 1) * 100;
            if (Double.isFinite(g)) {
                maxGain = Math.max(maxGain, g);
            }
        }
        if (!(maxGain > 0)) {
            return;
        }

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int labelHeight = 12;
        int plotTop = titleY + 12;
        int available = screen.topPos + screen.guiHeight - MarketBoxScreen.PADDING - 8 - plotTop - labelHeight;
        int plotH = Math.min(104, available);
        if (plotH < 40) {
            return;
        }
        int plotBottom = plotTop + plotH;

        guiGraphics.drawString(font, Component.translatable("gui.marketbox.interest_curve", String.valueOf(days)),
                plotX, titleY, 0xAAAAAA, false);

        // 背景与边框
        guiGraphics.fill(plotX, plotTop, plotX + plotW, plotBottom, 0x18000000);
        guiGraphics.renderOutline(plotX, plotTop, plotW, plotH, 0x33FFFFFF);

        // 面积填充：很淡，只用来暗示"累计收益"的量
        int prevPy = plotBottom;
        for (int px = 0; px < plotW; px++) {
            double day = (double) px / (plotW - 1) * days;
            double gain = (Math.pow(1 + rate, periodsPerDay * day) - 1) * 100;
            double ratio = Double.isFinite(gain) ? Math.max(0.0, Math.min(1.0, gain / maxGain)) : 1.0;
            int py = plotBottom - (int) (ratio * (plotH - 3));

            guiGraphics.fill(plotX + px, py, plotX + px + 1, plotBottom, 0x1855FF55);
            // 曲线本体：每列都画，且从上一列高度连过来，线宽 3px，消除台阶感
            int from = Math.min(prevPy, py);
            int to = Math.max(prevPy, py) + 3;
            guiGraphics.fill(plotX + px, from, plotX + px + 1, to, 0xFF55DD55);
            prevPy = py;
        }

        // 右上角终点值，带半透明底避免与曲线粘连
        String maxLabel = String.format("+%.1f%%", maxGain);
        int labelW = font.width(maxLabel);
        int labelX = plotX + plotW - labelW - 6;
        guiGraphics.fill(labelX - 3, plotTop + 3, labelX + labelW + 3, plotTop + 14, 0x88000000);
        guiGraphics.drawString(font, maxLabel, labelX, plotTop + 5, 0x66FF66, false);

        // X 轴时间标签
        String d0 = Component.translatable("gui.marketbox.curve.day", "0").getString();
        String d7 = Component.translatable("gui.marketbox.curve.day", "7").getString();
        String d14 = Component.translatable("gui.marketbox.curve.day", String.valueOf(days)).getString();
        int labelY = plotBottom + 4;
        guiGraphics.drawString(font, d0, plotX + 1, labelY, 0x777777, false);
        guiGraphics.drawString(font, d7, plotX + plotW / 2 - font.width(d7) / 2, labelY, 0x777777, false);
        guiGraphics.drawString(font, d14, plotX + plotW - font.width(d14) - 1, labelY, 0x777777, false);
    }



    static void renderPlayerInfo(MarketBoxScreen screen, GuiGraphics guiGraphics) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        int avatarX = screen.leftPos + (screen.leftSectionWidth - MarketBoxScreen.AVATAR_SIZE) / 2;
        int avatarY = screen.topPos + MarketBoxScreen.PADDING + 5;
        
        // Draw Player Head
        PlayerInfo info = null;
        if (Minecraft.getInstance().getConnection() != null) {
            info = Minecraft.getInstance().getConnection().getPlayerInfo(player.getGameProfile().getId());
        }
        ResourceLocation skin = info != null ? info.getSkin().texture() : MarketBoxScreen.DEFAULT_SKIN;
        PlayerFaceRenderer.draw(guiGraphics, skin, avatarX, avatarY, MarketBoxScreen.AVATAR_SIZE);
        
        // Draw Player Name
        int nameY = avatarY + MarketBoxScreen.AVATAR_SIZE + 5;
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, player.getName(), screen.leftPos + screen.leftSectionWidth / 2, nameY, 0xFFFFFF);
        
        // Draw Total Balance
        int balanceY = nameY + 15;
        String balanceText = MoneyUtils.formatValue(ClientData.getPlayerBalance());
        Component balanceComp = Component.translatable("gui.marketbox.total_balance_short");
        
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, balanceComp, screen.leftPos + screen.leftSectionWidth / 2, balanceY, 0xAAAAAA);
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, balanceText, screen.leftPos + screen.leftSectionWidth / 2, balanceY + 10, 0xFFFF55);

        // Wallet Balance
        int walletY = balanceY + 30;
        Component walletLabel = Component.translatable("gui.marketbox.wallet_total");
        double walletAmount = MoneyUtils.getInventoryMoneyTotal(player);
        String walletText = MoneyUtils.formatValue(walletAmount);

        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, walletLabel, screen.leftPos + screen.leftSectionWidth / 2, walletY, 0xAAAAAA);
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, walletText, screen.leftPos + screen.leftSectionWidth / 2, walletY + 10, 0x55FF55);
    }

    static void renderSections(MarketBoxScreen screen, GuiGraphics guiGraphics) {
        // Vertical divider between Left and Middle
        int divider1X = screen.leftPos + screen.leftSectionWidth;
        guiGraphics.fill(divider1X, screen.topPos + MarketBoxScreen.PADDING, divider1X + 1, screen.topPos + screen.guiHeight - MarketBoxScreen.PADDING, 0x44FFFFFF);

    }

    static void drawValueWithScaling(MarketBoxScreen screen, GuiGraphics guiGraphics, Component label, String value, int x, int y, int maxWidth, int color) {
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, label, x, y, 0xAAAAAA, false);
        
        int valueWidth = net.minecraft.client.Minecraft.getInstance().font.width(value);
        // Allow the value to take up to 45% of the width for scaling purposes
        int availableWidth = (int)(maxWidth * 0.45f);
        
        float scale = 1.0f;
        if (valueWidth > availableWidth) {
            scale = (float) availableWidth / valueWidth;
        }
        
        if (scale < 0.5f) scale = 0.5f;

        guiGraphics.pose().pushPose();
        float scaledValueWidth = valueWidth * scale;
        // Right align: position the value so its right edge matches x + maxWidth (the box edge)
        float valueX = (x + maxWidth) - scaledValueWidth;
        
        float valueY = y + Math.round((1 - scale) * net.minecraft.client.Minecraft.getInstance().font.lineHeight / 2f);
        guiGraphics.pose().translate(Math.round(valueX), valueY, 0);
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, value, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }
}
