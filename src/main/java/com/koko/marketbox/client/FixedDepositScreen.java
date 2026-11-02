package com.koko.marketbox.client;

import com.koko.marketbox.capabilities.FixedDeposit;
import com.koko.marketbox.networking.packet.CloseFixedDepositPacket;
import com.koko.marketbox.networking.packet.OpenFixedDepositPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 定期存款：把钱按期限锁定，到期一次性给付本息。
 *
 * 提前取出只退本金，作为违约代价；每笔在存入时锁定利率，
 * 之后服主调整活期利率不影响已存入的定期。
 */
public class FixedDepositScreen extends Screen implements FeedbackReceiver {

    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 26;
    private static final int LIST_TOP = 126;

    private final Screen parent;
    private final Toast toast = new Toast();

    private int contentLeft;
    private int contentWidth;

    private EditBox amountBox;
    private int selectedDays = 7;
    /** 已提示过一次、等待二次确认的提前取出目标 */
    private java.util.UUID pendingEarlyId = null;
    private final StyledButton[] dayButtons = new StyledButton[3];

    public FixedDepositScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.fixed.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;

        Sidebar.buttons(this, Sidebar.Page.BANK).forEach(this::addRenderableWidget);

        int x = contentLeft + PADDING;
        int controlY = 78;

        // 右侧控件从右边界往左排：原先写死 x+210 / x+306 / x+372，
        // 逻辑宽 427 时「全部取出」会跑到屏幕外
        int right = contentLeft + contentWidth - PADDING;
        int claimAllW = 72;
        int openW = 60;
        int gap = 6;
        int claimAllX = right - claimAllW;
        int openX = claimAllX - gap - openW;
        int boxW = Math.max(60, Math.min(90, openX - gap - (x + 200)));
        int boxX = openX - gap - boxW;

        // 期限按钮
        int[] options = OpenFixedDepositPacket.ALLOWED_DAYS;
        for (int i = 0; i < options.length; i++) {
            final int days = options[i];
            dayButtons[i] = new StyledButton(x + i * 66, controlY, 62, 18,
                    Component.translatable("gui.marketbox.fixed.days", days),
                    b -> {
                        selectedDays = days;
                        refreshDayButtons();
                    }, false, selectedDays == days);
            this.addRenderableWidget(dayButtons[i]);
        }

        // 金额输入
        this.amountBox = new EditBox(this.font, boxX, controlY, boxW, 18,
                Component.translatable("gui.marketbox.fixed.amount"));
        this.amountBox.setMaxLength(12);
        this.amountBox.setFilter(s -> s.isEmpty() || s.matches("\\d{0,12}(\\.\\d{0,2})?"));
        this.addRenderableWidget(this.amountBox);

        // 存入
        this.addRenderableWidget(new StyledButton(openX, controlY, openW, 18,
                Component.translatable("gui.marketbox.fixed.open"), b -> openDeposit()));

        // 返回银行主页：定期是银行的二级页面
        this.addRenderableWidget(new StyledButton(this.width - PADDING - 60, 12, 60, 18,
                Component.translatable("gui.marketbox.fixed.back"), b -> this.onClose()));

        // 全部取出（到期逐笔领取在列表里点）
        this.addRenderableWidget(new StyledButton(claimAllX, controlY, claimAllW, 18,
                Component.translatable("gui.marketbox.fixed.claim_all"), b -> claimAllMatured()));
    }

    private void refreshDayButtons() {
        int[] options = OpenFixedDepositPacket.ALLOWED_DAYS;
        for (int i = 0; i < dayButtons.length; i++) {
            if (dayButtons[i] != null) {
                dayButtons[i].setHighlighted(selectedDays == options[i]);
            }
        }
    }

    private void openDeposit() {
        String text = amountBox == null ? "" : amountBox.getValue().trim();
        if (text.isEmpty()) {
            toast.show(Component.translatable("gui.marketbox.fixed.need_amount"), true);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            toast.show(Component.translatable("gui.marketbox.fixed.need_amount"), true);
            return;
        }
        if (amount <= 0) {
            toast.show(Component.translatable("gui.marketbox.fixed.need_amount"), true);
            return;
        }
        ClientNetwork.send(new OpenFixedDepositPacket(amount, selectedDays));
        amountBox.setValue("");
    }

    private void claimAllMatured() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (FixedDeposit deposit : ClientData.getFixedDeposits()) {
            if (deposit.isMatured(now)) {
                ClientNetwork.send(new CloseFixedDepositPacket(deposit.id()));
                count++;
            }
        }
        if (count == 0) {
            toast.show(Component.translatable("gui.marketbox.fixed.none_matured"), true);
        }
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

        var font = this.font;
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        guiGraphics.drawString(font, this.title, x, 12, 0xFFAA00, false);
        guiGraphics.drawString(font,
                Component.translatable("gui.marketbox.fixed.hint"),
                x, 28, 0x777777, false);

        // 利率说明
        String rateLine = Component.translatable("gui.marketbox.fixed.rate_line",
                String.format("%.2f", OpenFixedDepositPacket.rateForDays(7)),
                String.format("%.2f", OpenFixedDepositPacket.rateForDays(14)),
                String.format("%.2f", OpenFixedDepositPacket.rateForDays(30))).getString();
        guiGraphics.drawString(font, rateLine, x, 44, 0x888888, false);

        guiGraphics.drawString(font, Component.translatable("gui.marketbox.fixed.amount_label"),
                x + 206, 60, 0x888888, false);

        guiGraphics.fill(x, LIST_TOP - 10, x + w, LIST_TOP - 9, 0xFF3A3A3A);

        renderDeposits(guiGraphics, x, w, mouseX, mouseY);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        toast.render(guiGraphics, this.width, this.height);
    }

    private void renderDeposits(GuiGraphics guiGraphics, int x, int w, int mouseX, int mouseY) {
        var font = this.font;
        List<FixedDeposit> deposits = ClientData.getFixedDeposits();
        long now = System.currentTimeMillis();

        guiGraphics.drawString(font,
                Component.translatable("gui.marketbox.fixed.list_title", deposits.size(),
                        OpenFixedDepositPacket.MAX_DEPOSITS),
                x, LIST_TOP - 26, 0x888888, false);

        if (deposits.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.marketbox.fixed.empty"),
                    x, LIST_TOP + 4, 0x666666, false);
            return;
        }

        int maxRows = Math.max(1, (this.height - LIST_TOP - PADDING - 24) / ROW_HEIGHT);
        for (int i = 0; i < Math.min(deposits.size(), maxRows); i++) {
            FixedDeposit deposit = deposits.get(i);
            int rowY = LIST_TOP + i * ROW_HEIGHT;
            boolean matured = deposit.isMatured(now);

            if (i % 2 == 0) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x12FFFFFF);
            }
            if (matured) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x2255FF55);
            }

            String principal = MoneyUtils.formatValue(deposit.principalCents() / 100.0);
            guiGraphics.drawString(font, principal, x + 4, rowY + 9, 0xEEEEEE, false);

            String plan = Component.translatable("gui.marketbox.fixed.row_plan",
                    deposit.days(), String.format("%.2f", deposit.dailyRatePercent())).getString();
            guiGraphics.drawString(font, plan, x + 100, rowY + 9, 0x888888, false);

            String status;
            int statusColor;
            if (matured) {
                status = Component.translatable("gui.marketbox.fixed.row_matured",
                        MoneyUtils.formatValue(deposit.totalCents() / 100.0)).getString();
                statusColor = 0x55FF55;
            } else {
                long remainMs = deposit.remainingMillis(now);
                long hours = remainMs / 3_600_000L;
                status = Component.translatable("gui.marketbox.fixed.row_remaining",
                        hours >= 1 ? (hours + " 小时") : ((remainMs / 60_000L) + " 分钟")).getString();
                statusColor = 0xAAAAAA;
            }
            guiGraphics.drawString(font, status, x + w - 190, rowY + 9, statusColor, false);

            // 右侧操作提示
            String action = Component.translatable(matured
                    ? "gui.marketbox.fixed.action_claim"
                    : "gui.marketbox.fixed.action_early").getString();
            guiGraphics.drawString(font, action, x + w - font.width(action) - 8, rowY + 9,
                    matured ? 0x55FF55 : 0xCC7777, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        if (button == 0 && mouseX >= x && mouseX <= x + w && mouseY >= LIST_TOP) {
            int index = (int) ((mouseY - LIST_TOP) / ROW_HEIGHT);
            List<FixedDeposit> deposits = ClientData.getFixedDeposits();
            if (index >= 0 && index < deposits.size()) {
                FixedDeposit deposit = deposits.get(index);
                // 提前取出只退本金：第一次点击先提示，再点一次才执行，避免误点白存
                if (!deposit.isMatured(System.currentTimeMillis())
                        && !deposit.id().equals(pendingEarlyId)) {
                    pendingEarlyId = deposit.id();
                    toast.show(Component.translatable("gui.marketbox.fixed.early_confirm"), true);
                    return true;
                }
                pendingEarlyId = null;
                ClientNetwork.send(new CloseFixedDepositPacket(deposit.id()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
}
