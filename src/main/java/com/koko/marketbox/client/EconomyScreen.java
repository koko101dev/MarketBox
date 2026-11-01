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
        y += 8;

        // ---- 本次会话 ----
        y = section(guiGraphics, font, x, w, y, "gui.marketbox.economy.section_session");
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.interest",
                "+" + MoneyUtils.formatValue(ClientData.getEconomyInterest()), 0xFF8866);
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.admin",
                "+" + MoneyUtils.formatValue(ClientData.getEconomyAdmin()), 0xFF8866);
        y = row(guiGraphics, font, x, w, y, "gui.marketbox.economy.fee_destroyed",
                "-" + MoneyUtils.formatValue(ClientData.getEconomyFeeDestroyed()), 0x66CC66);

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

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        toast.render(guiGraphics, this.width);
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
