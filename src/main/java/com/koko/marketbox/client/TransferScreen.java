package com.koko.marketbox.client;

import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.Config;
import com.koko.marketbox.networking.packet.TransferMoneyPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * 转账页：左侧玩家列表选择收款人，右侧填写金额并确认。
 * 从银行页拆出，避免存取与转账挤在同一栏。
 */
public class TransferScreen extends Screen {

    private static final int PADDING = 12;
    private static final int ROW_HEIGHT = 26;
    private static final int AVATAR_SIZE = 16;
    private static final int INPUT_HEIGHT = 18;

    private final Screen parent;
    /** 从排行榜跳转时带入的收款人 */
    private final PlayerEntry presetTarget;
    private final Toast toast = new Toast();

    private int contentLeft;
    private int contentWidth;
    private int contentHeight;

    private int scrollOffset = 0;
    private String query = "";
    private EditBox searchBox;
    private PlayerEntry selected;
    private EditBox amountInput;
    private StyledButton confirmButton;

    public TransferScreen(Screen parent) {
        this(parent, null);
    }

    public TransferScreen(Screen parent, PlayerEntry presetTarget) {
        super(Component.translatable("gui.marketbox.nav.transfer"));
        this.parent = parent;
        this.presetTarget = presetTarget;
        this.selected = presetTarget;
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;
        this.contentHeight = this.height;

        Sidebar.buttons(this, Sidebar.Page.TRANSFER).forEach(this::addRenderableWidget);

        // 玩家列表搜索：人多时按名字快速定位
        int listW = (int) (contentWidth * 0.52f);
        this.searchBox = new EditBox(this.font, contentLeft + PADDING, 40, listW - PADDING, 14,
                Component.translatable("gui.marketbox.player_select.search"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.translatable("gui.marketbox.player_select.search"));
        this.searchBox.setResponder(text -> {
            this.query = text == null ? "" : text;
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchBox);

        int panelX = panelLeft();
        int panelWidth = panelWidth();

        // 金额输入框
        this.amountInput = new EditBox(this.font, panelX + PADDING, 62, panelWidth - PADDING * 2 - 46, INPUT_HEIGHT,
                Component.empty());
        this.amountInput.setMaxLength(12);
        this.amountInput.setFilter(s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
        this.amountInput.setCanLoseFocus(true);
        this.addRenderableWidget(this.amountInput);

        // 确认按钮（嵌在输入框右侧）
        this.confirmButton = new StyledButton(panelX + panelWidth - PADDING - 40, 62, 40, INPUT_HEIGHT,
                Component.translatable("gui.marketbox.transfer.confirm"), b -> doTransfer());
        this.addRenderableWidget(this.confirmButton);

        // 快捷金额：前三档来自配置，第四个为「全部」
        int gap = 2;
        int btnY = 86;
        int totalWidth = panelWidth - PADDING * 2;
        int btnW = (totalWidth - gap * 3) / 4;
        double[] values = {
                Config.getQuickAmount(1), Config.getQuickAmount(2), Config.getQuickAmount(3), -1
        };
        String[] labels = {
                formatAmount(values[0]), formatAmount(values[1]), formatAmount(values[2]),
                Component.translatable("gui.marketbox.amount.all").getString()
        };
        for (int i = 0; i < labels.length; i++) {
            final double preset = values[i];
            this.addRenderableWidget(new StyledButton(panelX + PADDING + (btnW + gap) * i, btnY, btnW, INPUT_HEIGHT,
                    Component.literal(labels[i]), b -> applyQuickAmount(preset)));
        }
    }

    private int listTop() {
        return 60;
    }

    private int listHeight() {
        return contentHeight - listTop() - PADDING;
    }

    private int panelLeft() {
        return contentLeft + (int) (contentWidth * 0.58f);
    }

    private int panelWidth() {
        return contentWidth - (int) (contentWidth * 0.58f) - PADDING;
    }

    /** 可转账的对象：排除自己 */
    private List<PlayerEntry> candidates() {
        List<PlayerEntry> all = ClientData.getLeaderboard();
        UUID self = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getUUID() : null;
        List<PlayerEntry> result = self == null
                ? List.copyOf(all)
                : all.stream().filter(e -> !self.equals(e.uuid())).toList();

        if (query == null || query.isBlank()) {
            return result;
        }
        String q = query.toLowerCase(java.util.Locale.ROOT);
        return result.stream()
                .filter(e -> e.name() != null && e.name().toLowerCase(java.util.Locale.ROOT).contains(q))
                .toList();
    }

    private void applyQuickAmount(double preset) {
        double amount = preset;
        if (preset < 0) {
            amount = ClientData.getPlayerBalance();
        }
        if (amount <= 0 || amountInput == null) {
            toast.show(Component.translatable("gui.marketbox.error.invalid_amount"), true);
            return;
        }
        amountInput.setValue(formatAmount(amount));
        amountInput.setFocused(true);
    }

    private void doTransfer() {
        if (selected == null) {
            toast.show(Component.translatable("gui.marketbox.transfer.error.no_target"), true);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountInput.getValue());
            if (amount <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            toast.show(Component.translatable("gui.marketbox.error.invalid_amount"), true);
            return;
        }
        if (amount > ClientData.getPlayerBalance()) {
            toast.show(Component.translatable("gui.marketbox.error.not_enough_balance"), true);
            return;
        }

        ClientNetwork.send(new TransferMoneyPacket(selected.uuid(), amount));
        amountInput.setValue("");
        this.selected = null;
        this.scrollOffset = Math.max(0, scrollOffset);
    }

    private static String formatAmount(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 界面自身不应用背景模糊：直接铺半透明底。
        // 这样无需依赖「关闭菜单模糊」开关，也不会受其他模组的全局设置影响。
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getBankBadgeCount());

        guiGraphics.fill(contentLeft, 0, this.width, contentHeight, 0xE6101010);

        Minecraft mc = Minecraft.getInstance();
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        // 标题与余额
        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.nav.transfer"), x, 12, 0xFFAA00, false);
        String balanceText = Component.translatable("gui.marketbox.transfer.my_balance").getString()
                + " " + MoneyUtils.formatValue(ClientData.getPlayerBalance());
        guiGraphics.drawString(mc.font, balanceText, x + w - mc.font.width(balanceText), 12, 0x55FF55, false);
        guiGraphics.fill(x, 26, x + w, 27, 0xFF3A3A3A);

        // 左：玩家列表
        int listW = (int) (contentWidth * 0.52f);
        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.rank.player"), x, 30, 0x888888, false);
        String wealthHeader = Component.translatable("gui.marketbox.rank.wealth").getString();
        guiGraphics.drawString(mc.font, wealthHeader, x + listW - mc.font.width(wealthHeader), 30, 0x888888, false);

        List<PlayerEntry> entries = candidates();
        int top = listTop();
        int height = listHeight();
        int max = maxScroll(entries.size());
        int offset = Math.min(scrollOffset, max);

        guiGraphics.enableScissor(contentLeft, top, contentLeft + listW + PADDING, top + height);
        if (entries.isEmpty()) {
            guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.rank.empty"), x, top + 4, 0x555555, false);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                int rowY = top + i * ROW_HEIGHT - offset;
                if (rowY + ROW_HEIGHT < top || rowY > top + height) {
                    continue;
                }
                PlayerEntry entry = entries.get(i);
                boolean hovered = mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT && mouseX <= contentLeft + listW + PADDING;

                if (selected != null && selected.uuid().equals(entry.uuid())) {
                    guiGraphics.fill(contentLeft, rowY, contentLeft + listW + PADDING, rowY + ROW_HEIGHT, 0x4455FF55);
                } else if (hovered) {
                    guiGraphics.fill(contentLeft, rowY, contentLeft + listW + PADDING, rowY + ROW_HEIGHT, 0x22FFFFFF);
                } else if (i % 2 == 0) {
                    guiGraphics.fill(contentLeft, rowY, contentLeft + listW + PADDING, rowY + ROW_HEIGHT, 0x10FFFFFF);
                }

                PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(entry.uuid()) : null;
                ResourceLocation skin = info != null ? info.getSkin().texture() : MarketBoxScreen.DEFAULT_SKIN;
                PlayerFaceRenderer.draw(guiGraphics, skin, x, rowY + (ROW_HEIGHT - AVATAR_SIZE) / 2, AVATAR_SIZE);

                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                guiGraphics.drawString(mc.font, mc.font.plainSubstrByWidth(entry.name(), listW - 100), x + 22, textY, 0xDDDDDD, false);
                String wealth = MoneyUtils.formatValue(entry.balance());
                guiGraphics.drawString(mc.font, wealth, x + listW - mc.font.width(wealth), textY, 0x55FF55, false);
            }
        }
        guiGraphics.disableScissor();

        // 右：操作面板
        int px = panelLeft();
        int pw = panelWidth();
        guiGraphics.fill(px, 46, px + pw, 138, 0x22FFFFFF);
        guiGraphics.renderOutline(px, 46, pw, 92, 0x44FFFFFF);

        String targetName = selected != null ? selected.name()
                : Component.translatable("gui.marketbox.transfer.no_target").getString();
        String targetLabel = Component.translatable("gui.marketbox.transfer.target").getString() + ": " + targetName;
        guiGraphics.drawString(mc.font, mc.font.plainSubstrByWidth(targetLabel, pw - PADDING * 2),
                px + PADDING, 52, selected != null ? 0xFFFFFF : 0x888888, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 确认按钮仅在选好收款人时可用
        if (confirmButton != null) {
            confirmButton.active = selected != null;
        }

        // 列表滚动条
        if (max > 0) {
            int barH = Math.max(20, height * height / (entries.size() * ROW_HEIGHT));
            int barY = top + (height - barH) * offset / max;
            guiGraphics.fill(contentLeft + listW + 3, top, contentLeft + listW + 6, top + height, 0x33FFFFFF);
            guiGraphics.fill(contentLeft + listW + 3, barY, contentLeft + listW + 6, barY + barH, 0xFFAAAAAA);
        }

        toast.render(guiGraphics, this.width, this.height);
    }

    /** 供网络反馈统一调用 */
    void showFeedback(Component message, boolean isError) {
        toast.show(message, isError);
    }

    private int maxScroll(int size) {
        return Math.max(0, size * ROW_HEIGHT - listHeight());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<PlayerEntry> entries = candidates();
            int top = listTop();
            int offset = Math.min(scrollOffset, maxScroll(entries.size()));
            int listW = (int) (contentWidth * 0.52f);

            // 必须限定在内容区左侧，否则侧边栏的点击会被列表误吞
            if (mouseX >= contentLeft && mouseX <= contentLeft + listW + PADDING) {
                for (int i = 0; i < entries.size(); i++) {
                    int rowY = top + i * ROW_HEIGHT - offset;
                    if (rowY + ROW_HEIGHT < top || rowY > top + listHeight()) {
                        continue;
                    }
                    if (mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                        this.selected = entries.get(i);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<PlayerEntry> entries = candidates();
        int max = maxScroll(entries.size());
        if (max > 0 && mouseY >= listTop()) {
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) (scrollY * ROW_HEIGHT)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            // 侧边栏各页共用一个窗口，ESC 直接关闭到游戏，而不是退回上一个页面
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
