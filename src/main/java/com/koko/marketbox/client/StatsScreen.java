package com.koko.marketbox.client;

import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 收支统计页：总收入/总支出概览、收支比例条、可滚动的交易流水。
 * 从银行主界面拆出，避免主界面同时承载操作与统计。
 */
public class StatsScreen extends Screen {

    private static final int PADDING = 12;
    private static final int ROW_HEIGHT = 18;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    private final Screen parent;
    private int contentLeft;
    private int contentWidth;
    private int contentHeight;
    private int scrollOffset = 0;

    public StatsScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.nav.stats"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;
        this.contentHeight = this.height;
        Sidebar.buttons(this, Sidebar.Page.STATS).forEach(this::addRenderableWidget);
    }

    /** 列表可滚动区域（顶部概览占固定高度） */
    private int listTop() {
        return 110;
    }

    private int listHeight() {
        return contentHeight - listTop() - PADDING;
    }

    private int maxScroll(List<?> rows) {
        return Math.max(0, rows.size() * ROW_HEIGHT - listHeight());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getBankBadgeCount());

        guiGraphics.fill(contentLeft, 0, this.width, contentHeight, 0xE6101010);

        Minecraft mc = Minecraft.getInstance();
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        // 标题
        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.stats.title"), x, 12, 0xFFAA00, false);
        guiGraphics.fill(x, 26, x + w, 27, 0xFF3A3A3A);

        double income = ClientData.getTotalIncome();
        double expense = ClientData.getTotalExpense();
        double net = income - expense;

        // 三块概览
        int cardW = w / 3 - 6;
        drawCard(guiGraphics, mc, x, 34, cardW, "gui.marketbox.chart.income", income, 0x55FF55);
        drawCard(guiGraphics, mc, x + cardW + 9, 34, cardW, "gui.marketbox.chart.expense", expense, 0xFF5555);
        drawCard(guiGraphics, mc, x + (cardW + 9) * 2, 34, cardW, "gui.marketbox.stats.net", net,
                net >= 0 ? 0xFFFF55 : 0xFF5555);

        // 比例条
        double total = income + expense;
        int barY = 76;
        int barH = 10;
        if (total > 0) {
            guiGraphics.fill(x, barY, x + w, barY + barH, 0xFF050505);
            guiGraphics.renderOutline(x, barY, w, barH, 0xFF333333);
            int incomeW = (int) (w * (income / total));
            guiGraphics.fill(x + 1, barY + 1, x + incomeW, barY + barH - 1, 0xFF3FA34D);
            guiGraphics.fill(x + incomeW, barY + 1, x + w - 1, barY + barH - 1, 0xFF9C3B3B);
        } else {
            // 没有收支数据时不留空轨道，避免看起来像一条多余的横杠
            guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.stats.no_data"),
                    x, barY + 1, 0x666666, false);
        }

        // 流水表头
        int headerY = 96;
        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.stats.time"), x, headerY, 0x888888, false);
        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.stats.detail"), x + 76, headerY, 0x888888, false);
        String amountHeader = Component.translatable("gui.marketbox.stats.amount").getString();
        guiGraphics.drawString(mc.font, amountHeader, x + w - mc.font.width(amountHeader), headerY, 0x888888, false);

        // 流水列表
        List<PlayerBalance.Transaction> tx = ClientData.getTransactions();
        int top = listTop();
        int height = listHeight();
        int offset = Math.min(scrollOffset, maxScroll(tx));

        guiGraphics.enableScissor(contentLeft, top, this.width, top + height);
        if (tx.isEmpty()) {
            guiGraphics.drawCenteredString(mc.font, Component.translatable("gui.marketbox.history.no_data"),
                    contentLeft + contentWidth / 2, top + height / 2 - 4, 0x555555);
        } else {
            for (int i = 0; i < tx.size(); i++) {
                int rowY = top + i * ROW_HEIGHT - offset;
                if (rowY + ROW_HEIGHT < top || rowY > top + height) {
                    continue;
                }
                PlayerBalance.Transaction t = tx.get(i);
                if (i % 2 == 0) {
                    guiGraphics.fill(contentLeft, rowY, this.width, rowY + ROW_HEIGHT, 0x14FFFFFF);
                }
                guiGraphics.drawString(mc.font, TIME_FORMAT.format(new Date(t.timestamp())), x, rowY + 5, 0x999999, false);

                Component detail = resolveReason(t.reason());
                String detailText = mc.font.plainSubstrByWidth(detail.getString(), w - 170);
                guiGraphics.drawString(mc.font, detailText, x + 76, rowY + 5, 0xDDDDDD, false);

                String amount = (t.amount() > 0 ? "+" : "") + String.format("%.2f", t.amount());
                guiGraphics.drawString(mc.font, amount, x + w - mc.font.width(amount), rowY + 5,
                        t.amount() > 0 ? 0x55FF55 : 0xFF5555, false);
            }
        }
        guiGraphics.disableScissor();

        // 滚动条
        if (maxScroll(tx) > 0) {
            int thumbH = Math.max(20, height * height / (tx.size() * ROW_HEIGHT));
            int thumbY = top + (height - thumbH) * offset / maxScroll(tx);
            guiGraphics.fill(this.width - 5, top, this.width - 2, top + height, 0x33FFFFFF);
            guiGraphics.fill(this.width - 5, thumbY, this.width - 2, thumbY + thumbH, 0xFFAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics g, Minecraft mc, int x, int y, int w, String labelKey, double value, int color) {
        g.fill(x, y, x + w, y + 32, 0x33FFFFFF);
        g.renderOutline(x, y, w, 32, 0x44FFFFFF);
        g.drawString(mc.font, Component.translatable(labelKey), x + 6, y + 5, 0xAAAAAA, false);
        String text = MoneyUtils.formatValue(Math.abs(value));
        if (value < 0) {
            text = "-" + text;
        }
        g.drawString(mc.font, text, x + 6, y + 17, color, false);
    }

    private Component resolveReason(String reason) {
        if (reason.contains(":")) {
            String[] parts = reason.split(":", 2);
            return Component.translatable(parts[0]).append(parts[1]);
        }
        return Component.translatable(reason);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<PlayerBalance.Transaction> tx = ClientData.getTransactions();
        int max = maxScroll(tx);
        if (max > 0 && mouseY >= listTop()) {
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) (scrollY * ROW_HEIGHT * 2)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
