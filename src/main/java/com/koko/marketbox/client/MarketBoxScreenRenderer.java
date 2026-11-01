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

        // 5. 收益预估：直接给出金额，比一条永远向上的曲线更有用
        renderEarningsForecast(screen, guiGraphics, startX, contentY + 26, maxWidth);

        // 6. 最近几次结息：让玩家看到利息实际到账的节奏
        renderRecentInterest(guiGraphics, startX, contentY + 88, maxWidth);
    }


    /**
     * 最近几次结息记录。
     * 数据来自玩家流水里 reason 为 interest 的条目，最多显示 5 条。
     */
    private static void renderRecentInterest(GuiGraphics guiGraphics, int x, int y, int width) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        var history = ClientData.getTransactions().stream()
                .filter(t -> "gui.marketbox.transaction.interest".equals(t.reason()))
                .limit(5)
                .toList();
        if (history.isEmpty()) {
            return;
        }

        guiGraphics.drawString(font,
                Component.translatable("gui.marketbox.recent_interest"), x, y, 0xAAAAAA, false);

        int rowY = y + 12;
        for (var record : history) {
            var dt = java.time.Instant.ofEpochMilli(record.timestamp())
                    .atZone(java.time.ZoneId.systemDefault());
            String time = String.format("%02d-%02d %02d:%02d",
                    dt.getMonthValue(), dt.getDayOfMonth(), dt.getHour(), dt.getMinute());
            String amount = "+" + MoneyUtils.formatValue(record.amount());

            guiGraphics.drawString(font, time, x + 2, rowY, 0x777777, false);
            guiGraphics.drawString(font, amount, x + width - font.width(amount), rowY, 0x55FF55, false);
            rowY += 10;
        }
    }

    /**
     * 收益预估：按当前利率算出 14 天后的余额，直接显示金额。
     *
     * 原先这里画的是复利曲线，但利率固定时它永远是一条单调上升的曲线，
     * 玩家看完仍然不知道"能赚多少"。改成数字更直接。
     */
    private static void renderEarningsForecast(MarketBoxScreen screen, GuiGraphics guiGraphics,
                                               int x, int y, int width) {
        double rate = ClientData.getInterestRate() / 100.0;
        double balance = ClientData.getPlayerBalance();
        if (!(rate > 0) || balance <= 0) {
            return;
        }

        int interval = Math.max(60, ClientData.getInterestIntervalSeconds());
        int periodsPerDay = Math.max(1, Math.min(288, 86400 / interval));
        double future = balance * Math.pow(1 + rate, (double) periodsPerDay * 14);
        if (!Double.isFinite(future)) {
            return;
        }
        double gain = future - balance;
        if (gain <= 0) {
            return;
        }

        var font = net.minecraft.client.Minecraft.getInstance().font;

        // 用「每天」而不是「14 天」：玩家对"一天"有直观感受，14 天要换算
        double perDay = balance * (Math.pow(1 + rate, periodsPerDay) - 1);
        if (!Double.isFinite(perDay) || perDay <= 0) {
            return;
        }

        String title = Component.translatable("gui.marketbox.forecast.title").getString();
        guiGraphics.drawString(font, title, x, y, 0xAAAAAA, false);

        String perDayText = Component.translatable("gui.marketbox.forecast.per_day",
                MoneyUtils.formatValue(perDay)).getString();
        guiGraphics.drawString(font, perDayText, x + 2, y + 14, 0x55FF55, false);

        String futureText = Component.translatable("gui.marketbox.forecast.future",
                MoneyUtils.formatValue(future)).getString();
        guiGraphics.drawString(font, futureText, x + 2, y + 28, 0x888888, false);
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
