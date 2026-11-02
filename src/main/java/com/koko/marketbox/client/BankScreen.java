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
    private static final int CARD_HEIGHT = 104;
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

        if (mode == Mode.NONE) {
            int w = (contentWidth - PADDING * 2) / 3;
            int gap = 8;
            this.addRenderableWidget(new StyledButton(left, buttonTop, w - gap, BUTTON_HEIGHT,
                    Component.translatable("gui.marketbox.button.feature1"),
                    b -> switchMode(Mode.DEPOSIT)));
            this.addRenderableWidget(new StyledButton(left + w, buttonTop, w - gap, BUTTON_HEIGHT,
                    Component.translatable("gui.marketbox.button.feature2"),
                    b -> switchMode(Mode.WITHDRAW)));
            this.addRenderableWidget(new StyledButton(left + w * 2, buttonTop, w - gap, BUTTON_HEIGHT,
                    Component.translatable("gui.marketbox.fixed.entry"),
                    b -> Minecraft.getInstance().setScreen(new FixedDepositScreen(this))));
        } else {
            // 输入模式：左侧固定宽度输入框 + 确认，右侧快捷金额，取消贴最右
            int inputWidth = 176;
            // 与普通模式的按钮行保持同一水平线，切换时界面不跳动
            int rowY = buttonTop;
            this.inputRowY = rowY;
            this.amountInput = new EditBox(this.font, left + 8, rowY + 5,
                    inputWidth - 16, BUTTON_HEIGHT - 10, Component.empty());
            this.amountInput.setMaxLength(12);
            // 边框由界面自绘，关掉 EditBox 默认的白框，避免两层描边
            this.amountInput.setBordered(false);
            this.amountInput.setHint(Component.translatable("gui.marketbox.balance.input_hint",
                    Component.translatable(mode == Mode.DEPOSIT
                            ? "gui.marketbox.button.feature1" : "gui.marketbox.button.feature2")));
            this.amountInput.setFilter(s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
            this.amountInput.setFocused(true);
            this.setFocused(this.amountInput);
            this.addRenderableWidget(this.amountInput);

            this.addRenderableWidget(new StyledButton(left + inputWidth + 6, rowY, 30, BUTTON_HEIGHT,
                    Component.literal("✓"), b -> submit()));

            int quickX = left + inputWidth + 44;
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
            amount = mode == Mode.DEPOSIT
                    ? ClientData.getPlayerBalance()
                    : Math.min(ClientData.getPlayerBalance(), 1_000_000);
        }
        amountInput.setValue(formatAmount(amount));
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

        guiGraphics.drawString(font, this.title, left, 16, 0xFFAA00, false);

        renderBalanceCard(guiGraphics, left);

        if (mode != Mode.NONE) {
            // 当前操作由输入框占位符说明，不再单独占一行
            // 输入框底衬与边框
            guiGraphics.fill(left, inputRowY, left + 176, inputRowY + BUTTON_HEIGHT, 0xFF050505);
            guiGraphics.renderOutline(left, inputRowY, 176, BUTTON_HEIGHT, 0xFF666666);
        }

        renderHistory(guiGraphics, left);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        toast.render(guiGraphics, this.width, this.height);
    }

    /** 余额卡片：把最重要的数字放到最大，辅助信息排在两侧 */
    private void renderBalanceCard(GuiGraphics guiGraphics, int left) {
        var font = this.font;
        int top = cardTop;
        int w = contentWidth;

        guiGraphics.fill(left, top, left + w, top + CARD_HEIGHT, 0xFF151515);
        guiGraphics.renderOutline(left, top, left + w, top + CARD_HEIGHT, 0xFF3A3A3A);

        // 头像：放在卡片左侧，和余额并排
        int avatarSize = 52;
        int avatarX = left + 14;
        int avatarY = top + (CARD_HEIGHT - avatarSize) / 2;
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

        if (player != null) {
            guiGraphics.drawString(font, player.getName(), textLeft, top + 10, 0xDDDDDD, false);
        }
        guiGraphics.drawString(font, Component.translatable("gui.marketbox.balance.label"),
                textLeft, top + 24, 0x777777, false);

        // 余额放大两倍：整页最该被一眼看到的就是这个数字
        String amount = "¥ " + MoneyUtils.formatValue(ClientData.getPlayerBalance());
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(2.0f, 2.0f, 1.0f);
        guiGraphics.drawString(font, amount, textLeft / 2, (top + 38) / 2, 0x55FF55, false);
        guiGraphics.pose().popPose();

        // 左下：活期利率与年化
        double dailyRate = ClientData.getInterestRate();
        double annual = (Math.pow(1 + dailyRate / 100.0, 365) - 1) * 100;
        String rateLine = Component.translatable("gui.marketbox.balance.rate_line",
                String.format("%.2f", dailyRate), String.format("%.1f", annual)).getString();
        guiGraphics.drawString(font, rateLine, textLeft, top + 82, 0x888888, false);

        // 右下：下次结息倒计时
        long lastTime = ClientData.getLastInterestTime();
        long interval = (long) ClientData.getInterestIntervalSeconds() * 1000L;
        long remain = Math.max(0, lastTime + interval - System.currentTimeMillis());
        String countdown = formatCountdown(remain);
        String label = Component.translatable("gui.marketbox.balance.next", countdown).getString();
        guiGraphics.drawString(font, label,
                left + w - font.width(label) - 14, top + 82, 0x55FFFF, false);

        // 右上：累计利息
        String total = Component.translatable("gui.marketbox.balance.total_interest",
                MoneyUtils.formatValue(ClientData.getTotalInterest())).getString();
        guiGraphics.drawString(font, total,
                left + w - font.width(total) - 14, top + 10, 0xFFAA00, false);
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
