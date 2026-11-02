package com.koko.marketbox.client;

import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.networking.packet.CloseFixedDepositPacket;
import com.koko.marketbox.networking.packet.OpenFixedDepositPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 银行页：余额、活期结息、定期存款入口。
 *
 * 布局是单列自上而下——顶部一张余额卡片，中间一排操作按钮，
 * 下方列出最近结息。旧版把信息切成左右两栏，左栏只放两个按钮，
 * 右侧挤了六块文字，分辨率一低就互相压。
 */
public class BankScreen extends Screen implements FeedbackReceiver {

    private static final int PADDING = 12;
    private static final int CARD_HEIGHT = 148;
    private static final int BUTTON_HEIGHT = 22;
    private static final int HISTORY_ROW = 12;

    private final Screen parent;
    private final Toast toast = new Toast();

    private int contentLeft;
    private int contentWidth;
    private int cardTop;
    private int buttonTop;
    private int historyTop;
    /** 输入模式下的控件行纵坐标 */
    private int inputRowY;
    /** 内容区内边距后的左边界与可用宽度 */
    private int innerLeft;
    private int innerWidth;

    /** 输入模式：存 / 取 */
    private enum Mode { NONE, DEPOSIT, WITHDRAW }
    private Mode mode = Mode.NONE;
    private EditBox amountInput;

    public BankScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.nav.bank"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = Math.min(this.width - navWidth, 760);

        // 内容块居中，避免超宽屏上摊得过开
        int left = contentLeft + ((this.width - navWidth) - contentWidth) / 2;
        this.cardTop = 42;
        this.buttonTop = cardTop + CARD_HEIGHT + 12;
        this.historyTop = buttonTop + BUTTON_HEIGHT + 22;

        Sidebar.buttons(this, Sidebar.Page.BANK).forEach(this::addRenderableWidget);

        // 与卡片内容同一条内边距，避免按钮顶在边框上
        int innerLeft = left + 14;
        int innerWidth = contentWidth - 28;
        this.innerLeft = innerLeft;
        this.innerWidth = innerWidth;

        if (mode == Mode.NONE) {
            int gap = 8;
            int w = (innerWidth - gap * 2) / 3;
            this.addRenderableWidget(new StyledButton(innerLeft, buttonTop, w, BUTTON_HEIGHT,
                    Component.translatable("gui.marketbox.button.feature1"),
                    b -> switchMode(Mode.DEPOSIT)));
            this.addRenderableWidget(new StyledButton(innerLeft + w + gap, buttonTop, w, BUTTON_HEIGHT,
                    Component.translatable("gui.marketbox.button.feature2"),
                    b -> switchMode(Mode.WITHDRAW)));
            this.addRenderableWidget(new StyledButton(innerLeft + (w + gap) * 2, buttonTop, w, BUTTON_HEIGHT,
                    Component.translatable("gui.marketbox.fixed.entry"),
                    b -> Minecraft.getInstance().setScreen(new FixedDepositScreen(this))));
        } else {
            // 输入模式：左侧固定宽度输入框 + 确认，右侧快捷金额，取消贴最右
            int inputWidth = 176;
            // 与普通模式的按钮行保持同一水平线，切换时界面不跳动
            int rowY = buttonTop;
            this.inputRowY = rowY;
            // 无边框的 EditBox 固定把文字画在 y+4 处，不会按高度居中（原版行为）。
            // 因此控件本身只给文字高度，靠 +3 的偏移把文字推到框的垂直中心附近。
            // 用 MC 标准输入框（带边框，文字按高度自动居中），
            // 与项目内其他界面保持一致
            this.amountInput = new EditBox(this.font, innerLeft + 8, rowY,
                    inputWidth - 16, BUTTON_HEIGHT, Component.empty());
            this.amountInput.setMaxLength(12);
            this.amountInput.setFilter(s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
            this.amountInput.setFocused(true);
            this.setFocused(this.amountInput);
            this.addRenderableWidget(this.amountInput);

            this.addRenderableWidget(new StyledButton(innerLeft + inputWidth + 6, rowY, 30, BUTTON_HEIGHT,
                    Component.literal("✓"), b -> submit()));

            int quickX = innerLeft + inputWidth + 44;
            int quickW = 58;
            double[] values = {
                    com.koko.marketbox.Config.getQuickAmount(1),
                    com.koko.marketbox.Config.getQuickAmount(2),
                    com.koko.marketbox.Config.getQuickAmount(3),
                    -1
            };
            String[] labels = {
                    formatAmount(values[0]), formatAmount(values[1]), formatAmount(values[2]),
                    Component.translatable("gui.marketbox.amount.all").getString()
            };
            for (int i = 0; i < 4; i++) {
                final double preset = values[i];
                this.addRenderableWidget(new StyledButton(quickX + i * (quickW + 4), rowY,
                        quickW, BUTTON_HEIGHT, Component.literal(labels[i]),
                        b -> applyQuick(preset)));
            }
            int cancelX = quickX + 4 * (quickW + 4) + 10;
            this.addRenderableWidget(new StyledButton(cancelX, rowY,
                    64, BUTTON_HEIGHT, Component.translatable("gui.cancel"),
                    b -> switchMode(Mode.NONE)));
        }
    }

    private void switchMode(Mode target) {
        this.mode = target;
        this.rebuildWidgets();
    }

    private static String formatAmount(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    private void applyQuick(double preset) {
        if (amountInput == null) {
            return;
        }
        double amount = preset;
        if (preset < 0) {
            var player = Minecraft.getInstance().player;
            double cash = player != null ? MoneyUtils.getInventoryMoneyTotal(player) : 0;
            amount = com.koko.marketbox.util.BankMath.quickAllAmount(
                    mode == Mode.DEPOSIT, ClientData.getPlayerBalance(), cash);
        }
        if (amount <= 0) {
            toast.show(Component.translatable("gui.marketbox.error.invalid_amount"), true);
            return;
        }
        amountInput.setValue(formatAmount(amount));
        amountInput.setFocused(true);
    }

    private void submit() {
        if (amountInput == null) {
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountInput.getValue().trim());
        } catch (NumberFormatException e) {
            toast.show(Component.translatable("gui.marketbox.fixed.need_amount"), true);
            return;
        }
        if (amount <= 0) {
            toast.show(Component.translatable("gui.marketbox.fixed.need_amount"), true);
            return;
        }
        if (mode == Mode.DEPOSIT) {
            ClientNetwork.send(new com.koko.marketbox.networking.packet.DepositMoneyPacket(amount));
        } else if (mode == Mode.WITHDRAW) {
            // 取款前先看余额，避免白跑一次服务端请求
            if (ClientData.getPlayerBalance() < amount) {
                toast.show(Component.translatable("gui.marketbox.error.not_enough_balance"), true);
                return;
            }
            ClientNetwork.send(new com.koko.marketbox.networking.packet.WithdrawMoneyPacket(amount));
        }
        switchMode(Mode.NONE);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }

        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getMarketBadgeCount());
        guiGraphics.fill(contentLeft, 0, this.width, this.height, 0xE6101010);

        int left = contentLeft + ((this.width - contentLeft) - contentWidth) / 2;
        var font = this.font;

        int innerLeft = left + 14;
        guiGraphics.drawString(font, this.title, innerLeft, 16, 0xFFAA00, false);

        renderBalanceCard(guiGraphics, left);

        if (mode != Mode.NONE) {
            // 当前操作由输入框占位符说明，不再单独占一行
            // 输入框底衬与边框
        }

        renderHistory(guiGraphics, innerLeft);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        toast.render(guiGraphics, this.width, this.height);
    }

    /**
     * 余额卡片：上半部分是身份与余额，下半部分是一行横向指标。
     *
     * 之前把利率、年化、利息、倒计时分散在卡片的左右两端，中间大片空白。
     * 改成四个等宽指标格铺满整行，宽度利用率从六成提到接近满格。
     */
    private void renderBalanceCard(GuiGraphics guiGraphics, int left) {
        var font = this.font;
        int top = cardTop;
        int w = contentWidth;
        int innerLeft = left + 14;
        int innerWidth = w - 28;

        guiGraphics.fill(left, top, left + w, top + CARD_HEIGHT, 0xFF151515);
        guiGraphics.renderOutline(left, top, left + w, top + CARD_HEIGHT, 0xFF3A3A3A);

        // 头像
        int avatarSize = 56;
        int avatarX = innerLeft;
        int avatarY = top + 24;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            net.minecraft.client.multiplayer.PlayerInfo info = null;
            if (Minecraft.getInstance().getConnection() != null) {
                info = Minecraft.getInstance().getConnection()
                        .getPlayerInfo(player.getGameProfile().getId());
            }
            var skin = info != null ? info.getSkin().texture() : MarketBoxScreen.DEFAULT_SKIN;
            PlayerFaceRenderer.draw(guiGraphics, skin, avatarX, avatarY, avatarSize);
        }

        int textLeft = avatarX + avatarSize + 14;
        // 头像 top+24 ~ top+80（56 高）；文字块与它上下对齐
        if (player != null) {
            guiGraphics.drawString(font, player.getName(), textLeft, top + 24, 0xDDDDDD, false);
        }
        guiGraphics.drawString(font, Component.translatable("gui.marketbox.balance.label"),
                textLeft, top + 38, 0x777777, false);

        String amount = "¥ " + MoneyUtils.formatValue(ClientData.getPlayerBalance());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(2.0f, 2.0f, 1.0f);
        // 2 倍缩放：实际高度约 18，绘制在 top+62 时底部约 top+80，与头像底齐平
        guiGraphics.drawString(font, amount, textLeft / 2, (top + 58) / 2, 0x55FF55, false);
        guiGraphics.pose().popPose();

        // 上半部分右侧：现金与存款，填掉头像区右侧的空白
        int rightX = left + w / 2 + 40;
        if (player != null) {
            double cash = MoneyUtils.getInventoryMoneyTotal(player);
            double deposit = ClientData.getPlayerBalance();
            int cellW = (left + w - rightX - 14) / 2;
            // 每格内容居中，避免文字挤在格子左侧、右侧又留白
            drawCenteredCell(guiGraphics, rightX, cellW, top + 40,
                    Component.translatable("gui.marketbox.balance.cash"),
                    MoneyUtils.formatValue(cash), 0x777777, 0xFFFF55);
            drawCenteredCell(guiGraphics, rightX + cellW, cellW, top + 40,
                    Component.translatable("gui.marketbox.balance.deposit"),
                    MoneyUtils.formatValue(deposit), 0x777777, 0x55FF55);
        }

        // 横向指标栏：四格等宽，铺满卡片宽度
        int metricsY = top + 96;
        int metricsHeight = 40;
        int gap = 8;
        int cellWidth = (innerWidth - gap * 3) / 4;

        double dailyRate = ClientData.getInterestRate();
        double annual = (Math.pow(1 + dailyRate / 100.0, 365) - 1) * 100;
        long lastTime = ClientData.getLastInterestTime();
        long interval = (long) ClientData.getInterestIntervalSeconds() * 1000L;
        long remain = Math.max(0, lastTime + interval - System.currentTimeMillis());

        String[] labels = {
                Component.translatable("gui.marketbox.balance.metric_rate").getString(),
                Component.translatable("gui.marketbox.balance.metric_annual").getString(),
                Component.translatable("gui.marketbox.balance.metric_interest").getString(),
                Component.translatable("gui.marketbox.balance.metric_next").getString()
        };
        String[] values = {
                String.format("%.2f%%", dailyRate),
                String.format("%.1f%%", annual),
                MoneyUtils.formatValue(ClientData.getTotalInterest()),
                formatCountdown(remain)
        };
        int[] colors = {0xAAAAAA, 0xAAAAAA, 0xFFAA00, 0x55FFFF};

        for (int i = 0; i < 4; i++) {
            int cellX = innerLeft + i * (cellWidth + gap);
            guiGraphics.fill(cellX, metricsY, cellX + cellWidth, metricsY + metricsHeight, 0xFF1C1C1C);
            // 每格内容居中，左右留白对称
            int centerX = cellX + cellWidth / 2;
            guiGraphics.drawCenteredString(font, labels[i], centerX, metricsY + 7, 0x777777);
            guiGraphics.drawCenteredString(font, values[i], centerX, metricsY + 22, colors[i]);
        }
    }

    /** 在一个格子里上下两行居中绘制（标签 + 数值） */
    private void drawCenteredCell(GuiGraphics guiGraphics, int cellX, int cellW, int y,
                                  Component label, String value, int labelColor, int valueColor) {
        int centerX = cellX + cellW / 2;
        guiGraphics.drawCenteredString(this.font, label, centerX, y, labelColor);
        guiGraphics.drawCenteredString(this.font, value, centerX, y + 14, valueColor);
    }

    private static String formatCountdown(long millis) {
        if (millis <= 0) {
            return "00:00";
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%02d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    /** 最近结息 + 定期到期提醒 */
    private void renderHistory(GuiGraphics guiGraphics, int left) {
        var font = this.font;
        int y = historyTop;

        guiGraphics.drawString(font, Component.translatable("gui.marketbox.recent_interest"),
                left, y, 0xAAAAAA, false);
        y += 14;

        var history = ClientData.getTransactions().stream()
                .filter(t -> "gui.marketbox.transaction.interest".equals(t.reason()))
                .limit(6)
                .toList();

        if (history.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.marketbox.balance.no_history"),
                    left, y, 0x666666, false);
            return;
        }

        for (var record : history) {
            var dt = java.time.Instant.ofEpochMilli(record.timestamp())
                    .atZone(java.time.ZoneId.systemDefault());
            String time = String.format("%02d-%02d %02d:%02d",
                    dt.getMonthValue(), dt.getDayOfMonth(), dt.getHour(), dt.getMinute());
            String amount = "+" + MoneyUtils.formatValue(record.amount());

            guiGraphics.drawString(font, time, left + 2, y, 0x777777, false);
            guiGraphics.drawString(font, amount, left + 130, y, 0x55FF55, false);
            y += HISTORY_ROW;
        }
    }

    @Override
    public void showFeedback(Component message, boolean isError) {
        toast.show(message, isError);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
