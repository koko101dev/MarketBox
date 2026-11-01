package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.networking.packet.DelistMarketItemPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 市场左栏的「我的挂单」列表。
 *
 * 替代原先的价格/数量/时长输入框：上架改由点击背包物品弹窗完成，
 * 左栏改为展示自己当前的挂单，点击任意一条即可下架。
 */
final class MyListingsPanel {

    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 16;
    private static final int MAX_ROWS = 9;

    private MyListingsPanel() {
    }

    /** 当前玩家的挂单，按上架时间倒序 */
    static List<MarketListing> mine() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        return ClientData.getMarketListings().stream()
                .filter(l -> player.getUUID().equals(l.sellerUUID()))
                .sorted((a, b) -> Long.compare(b.createdAt(), a.createdAt()))
                .toList();
    }

    private static int listLeft(MarketScreen screen) {
        return screen.leftPos + screen.PADDING;
    }

    private static int listWidth(MarketScreen screen) {
        return screen.sidebarWidth - screen.PADDING * 2;
    }

    private static int listTop(MarketScreen screen) {
        return screen.topPos + 45 + HEADER_HEIGHT;
    }

    static void render(MarketScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int x = listLeft(screen);
        int w = listWidth(screen);
        int top = listTop(screen);
        List<MarketListing> mine = mine();

        // 标题
        guiGraphics.drawCenteredString(font,
                Component.translatable("gui.marketbox.market.my_listings", mine.size()),
                screen.leftPos + screen.sidebarWidth / 2, screen.topPos + 45, 0xDDDDDD);

        if (mine.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.marketbox.market.my_listings_empty"),
                    screen.leftPos + screen.sidebarWidth / 2, top + 16, 0x777777);
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.marketbox.market.click_to_list"),
                    screen.leftPos + screen.sidebarWidth / 2, top + 32, 0x666666);
            return;
        }

        int shown = Math.min(mine.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            MarketListing listing = mine.get(i);
            int rowY = top + i * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

            guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT,
                    hovered ? 0x44FFFFFF : (i % 2 == 0 ? 0x18FFFFFF : 0x0AFFFFFF));

            guiGraphics.renderItem(listing.item(), x + 2, rowY + 6);

            String name = font.plainSubstrByWidth(listing.item().getHoverName().getString(), w - 26);
            guiGraphics.drawString(font, name, x + 22, rowY + 4, 0xEEEEEE, false);

            String price = MoneyUtils.formatValue(listing.price());
            guiGraphics.drawString(font, price, x + 22, rowY + 14, 0xFFFF55, false);

            // 剩余时间
            long remainMs = listing.createdAt() + (long) listing.expiryMinutes() * 60_000L - System.currentTimeMillis();
            String remain = remainMs > 0
                    ? Component.translatable("gui.marketbox.market.remain_min", String.valueOf(remainMs / 60_000L)).getString()
                    : Component.translatable("gui.marketbox.market.remain_expired").getString();
            guiGraphics.drawString(font, remain, x + w - font.width(remain) - 2, rowY + 14, 0x888888, false);
        }

        if (mine.size() > MAX_ROWS) {
            String more = Component.translatable("gui.marketbox.market.more_listings",
                    String.valueOf(mine.size() - MAX_ROWS)).getString();
            guiGraphics.drawString(font, more, x, top + MAX_ROWS * ROW_HEIGHT + 2, 0x777777, false);
        }
    }

    /** 点击任意一行即下架该挂单；返回 true 表示点击被消费 */
    static boolean handleClick(MarketScreen screen, double mouseX, double mouseY) {
        List<MarketListing> mine = mine();
        int x = listLeft(screen);
        int w = listWidth(screen);
        int top = listTop(screen);
        int shown = Math.min(mine.size(), MAX_ROWS);

        for (int i = 0; i < shown; i++) {
            int rowY = top + i * ROW_HEIGHT;
            if (mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                ClientNetwork.send(new DelistMarketItemPacket(mine.get(i).id()));
                return true;
            }
        }
        return false;
    }
}
