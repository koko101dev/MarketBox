package com.koko.marketbox.client;

import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 经济概览页：全服货币存量、本次会话的增减、市场活跃度。
 *
 * 数据由服务端统计后下发（只含聚合值），打开时请求一次，可手动刷新。
 */
public class EconomyScreen extends Screen {

    private static final int PADDING = 16;

    private final Screen parent;
    private final Toast toast = new Toast();
    /** 金库拨款的目标玩家（仅服主可用） */
    private com.koko.marketbox.market.PlayerEntry grantTarget;
    private net.minecraft.client.gui.components.EditBox amountBox;
    private int contentLeft;
    private int contentWidth;
    private int contentHeight;

    public EconomyScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.nav.economy"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;
        this.contentHeight = this.height;

        Sidebar.buttons(this, Sidebar.Page.ECONOMY).forEach(this::addRenderableWidget);

        // 打开即拉取一次最新数据
        ClientNetwork.send(new com.koko.marketbox.networking.packet.RequestEconomyPacket());

        this.addRenderableWidget(new StyledButton(
                contentLeft + contentWidth - PADDING - 60, 12, 60, 16,
                Component.translatable("gui.marketbox.economy.refresh"),
                b -> ClientNetwork.send(new com.koko.marketbox.networking.packet.RequestEconomyPacket())));

        // 金库操作仅服主（等级 4）可见
        if (ClientData.isAdmin() && this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.hasPermissions(4)) {
            buildAdminControls();
        }
    }

    /** 金库拨款：选人 + 金额 + 拨款 */
    private void buildAdminControls() {
        int x = contentLeft + PADDING;
        int y = contentHeight - 96;

        this.addRenderableWidget(new StyledButton(x, y, 110, 18,
                Component.literal(grantTarget != null ? grantTarget.name()
                        : Component.translatable("gui.marketbox.transfer.no_target").getString()),
                b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new PlayerSelectScreen(this, entry -> {
                            this.grantTarget = entry;
                            this.rebuildWidgets();
                        }));
                    }
                }));

        this.amountBox = new net.minecraft.client.gui.components.EditBox(this.font, x + 116, y, 90, 18, Component.empty());
        this.amountBox.setMaxLength(12);
        this.amountBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit)
                || (s.chars().filter(c -> c == '.').count() == 1 && s.matches("[0-9]+\\.[0-9]{0,2}")));
        this.addRenderableWidget(this.amountBox);

        this.addRenderableWidget(new StyledButton(x + 212, y, 70, 18,
                Component.translatable("gui.marketbox.economy.withdraw"), b -> submitGrant()));

        this.addRenderableWidget(new StyledButton(x + 288, y, 70, 18,
                Component.translatable("gui.marketbox.economy.goto_settings"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ConfigScreen(this));
            }
        }));
    }

    private void submitGrant() {
        if (grantTarget == null || amountBox == null) {
            toast.show(Component.translatable("gui.marketbox.transfer.error.no_target"), true);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountBox.getValue());
        } catch (NumberFormatException e) {
            toast.show(Component.translatable("gui.marketbox.error.invalid_amount"), true);
            return;
        }
        if (amount <= 0) {
            toast.show(Component.translatable("gui.marketbox.error.invalid_amount"), true);
            return;
        }
        ClientNetwork.send(new com.koko.marketbox.networking.packet.TreasuryGrantPacket(grantTarget.uuid(), amount));
        amountBox.setValue("");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getBankBadgeCount());

        guiGraphics.fill(contentLeft, 0, this.width, contentHeight, 0xE6101010);

        var font = this.font;
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;
        int y = 14;

        guiGraphics.drawString(font, this.title, x, y, 0xFFAA00, false);
        y += 18;
        guiGraphics.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
        y += 12;

        // ---- 货币存量 ----
        y = section(guiGraphics, font, x, w, y, "gui.marketbox.economy.section_holdings");
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.online_total",
                MoneyUtils.formatValue(ClientData.getEconomyOnlineTotal())
                        + "（" + ClientData.getEconomyOnlineCount() + " 人）", 0xFFFFFF);
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.treasury",
                MoneyUtils.formatValue(ClientData.getTreasuryBalance()), 0xFFCC66);
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.fee_mode",
                Component.translatable(ClientData.isFeeToTreasury()
                        ? "gui.marketbox.config.fee_mode.treasury"
                        : "gui.marketbox.config.fee_mode.destroy").getString(),
                ClientData.isFeeToTreasury() ? 0x66CC66 : 0x888888);
        y += 8;

        // ---- 本次会话 ----
        y = section(guiGraphics, font, x, w, y, "gui.marketbox.economy.section_session");
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.interest",
                "+" + MoneyUtils.formatValue(ClientData.getEconomyInterest()), 0xFF8866);
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.admin",
                "+" + MoneyUtils.formatValue(ClientData.getEconomyAdmin()), 0xFF8866);
        if (ClientData.isFeeToTreasury()) {
            y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.fee_to_treasury",
                    "+" + MoneyUtils.formatValue(ClientData.getEconomyFeeToTreasury()), 0xCCCC66);
        } else {
            y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.fee_destroyed",
                    "-" + MoneyUtils.formatValue(ClientData.getEconomyFeeDestroyed()), 0x66CC66);
        }

        double net = ClientData.getEconomyInterest() + ClientData.getEconomyAdmin()
                - ClientData.getEconomyFeeDestroyed();
        int netColor = net > 0 ? 0xFF5555 : (net < 0 ? 0x55FF55 : 0xAAAAAA);
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.net",
                (net > 0 ? "+" : "") + MoneyUtils.formatValue(net), netColor);
        y += 8;

        // ---- 市场 ----
        y = section(guiGraphics, font, x, w, y, "gui.marketbox.economy.section_market");
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.listings",
                String.valueOf(ClientData.getEconomyListingCount()), 0xFFFFFF);
        y += 12;

        // ---- 提示 ----
        Component hint = net > 0
                ? Component.translatable("gui.marketbox.economy.hint_inflation")
                : Component.translatable("gui.marketbox.economy.hint_stable");
        guiGraphics.drawString(font, hint, x, y, net > 0 ? 0xCC8844 : 0x777777, false);

        // 金库操作区（仅服主可见）
        if (ClientData.isAdmin() && this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.hasPermissions(4)) {
            int boxTop = contentHeight - 120;
            guiGraphics.fill(x, boxTop, x + w, boxTop + 108, 0x22FFFFFF);
            guiGraphics.renderOutline(x, boxTop, w, 108, 0x33FFAA00);
            guiGraphics.drawString(font, Component.translatable("gui.marketbox.economy.section_admin"),
                    x + 6, boxTop + 6, 0xFFAA00, false);
            guiGraphics.drawString(font, Component.translatable("gui.marketbox.economy.admin_hint"),
                    x + 6, boxTop + 80, 0x777777, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        toast.render(guiGraphics, this.width, this.height);
    }

    /** 区块标题，返回新的 y */
    private int section(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font,
                        int x, int w, int y, String key) {
        guiGraphics.drawString(font, Component.translatable(key), x, y, 0xFFAA00, false);
        guiGraphics.fill(x, y + 10, x + w, y + 11, 0x33FFFFFF);
        return y + 18;
    }

    /** 一行「标签 …… 值」 */
    private int row(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font,
                    int x, int w, int y, String key, String value, int valueColor) {
        guiGraphics.drawString(font, Component.translatable(key), x + 6, y, 0xAAAAAA, false);
        guiGraphics.drawString(font, value, x + w - font.width(value), y, valueColor, false);
        return y + 14;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
