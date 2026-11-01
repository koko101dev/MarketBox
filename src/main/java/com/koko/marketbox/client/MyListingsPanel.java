package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.networking.packet.DelistMarketItemPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * 市场左栏的「我的挂单」列表。
 *
 * 交互：单击选中（高亮），行尾出现 × 按钮，点击 × 才真正下架。
 * 避免误触——下架会让物品进入待领取，重新上架要重新填价格。
 */
final class MyListingsPanel {

    private static final int ROW_HEIGHT = 28;
    private static final int HEADER_HEIGHT = 16;
    private static final int MAX_ROWS = 9;
    /** 行尾 × 按钮的宽度 */
    private static final int DELIST_BTN_WIDTH = 16;

    /** 当前选中的挂单 id，跨帧保持 */
    private static UUID selectedId = null;

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
        return screen.topPos + 66 + HEADER_HEIGHT;
    }

    static void render(MarketScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int x = listLeft(screen);
        int w = listWidth(screen);
        int top = listTop(screen);
        List<MarketListing> mine = mine();

        // 选中的挂单可能已被移除，及时清空
        if (selectedId != null && mine.stream().noneMatch(l -> l.id().equals(selectedId))) {
            selectedId = null;
        }

        guiGraphics.drawCenteredString(font,
                Component.translatable("gui.marketbox.market.my_listings", mine.size()),
                screen.leftPos + screen.sidebarWidth / 2, screen.topPos + 66, 0xDDDDDD);

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
            boolean selected = listing.id().equals(selectedId);
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

            int bg = selected ? 0x5533AA33 : (hovered ? 0x33FFFFFF : (i % 2 == 0 ? 0x18FFFFFF : 0x0AFFFFFF));
            guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT, bg);
            if (selected) {
                guiGraphics.renderOutline(x, rowY, w, ROW_HEIGHT, 0x88FFAA00);
            }

            guiGraphics.renderItem(listing.item(), x + 2, rowY + 6);

            String name = font.plainSubstrByWidth(listing.item().getHoverName().getString(), w - 26);
            guiGraphics.drawString(font, name, x + 22, rowY + 4, 0xEEEEEE, false);

            String price = MoneyUtils.formatValue(listing.price());
            guiGraphics.drawString(font, price, x + 22, rowY + 14, 0xFFFF55, false);

            long remainMs = listing.createdAt() + (long) listing.expiryMinutes() * 60_000L - System.currentTimeMillis();
            String remain = remainMs > 0
                    ? Component.translatable("gui.marketbox.market.remain_min", String.valueOf(remainMs / 60_000L)).getString()
                    : Component.translatable("gui.marketbox.market.remain_expired").getString();
            int remainX = x + w - font.width(remain) - (selected ? DELIST_BTN_WIDTH + 2 : 2);
            guiGraphics.drawString(font, remain, remainX, rowY + 14, 0x888888, false);

            // 选中后行尾出现 × 按钮
            if (selected) {
                int btnX = x + w - DELIST_BTN_WIDTH + 2;
                boolean btnHovered = mouseX >= btnX && mouseX <= x + w
                        && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
                int btnColor = btnHovered ? 0xFF5555 : 0xAA5555;
                if (btnHovered) {
                    guiGraphics.fill(btnX - 2, rowY + 2, x + w - 1, rowY + ROW_HEIGHT - 2, 0x44FF5555);
                }
                guiGraphics.drawString(font, "×", btnX + 3, rowY + 9, btnColor, false);
            }
        }

        if (mine.size() > MAX_ROWS) {
            String more = Component.translatable("gui.marketbox.market.more_listings",
                    String.valueOf(mine.size() - MAX_ROWS)).getString();
            guiGraphics.drawString(font, more, x, top + MAX_ROWS * ROW_HEIGHT + 2, 0x777777, false);
        }

        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.list_hint").getString(),
                x, top + shown * ROW_HEIGHT + 4, 0x666666, false);
    }

    /** 单击选中；点中选中行的 × 才下架 */
    static boolean handleClick(MarketScreen screen, double mouseX, double mouseY) {
        List<MarketListing> mine = mine();
        int x = listLeft(screen);
        int w = listWidth(screen);
        int top = listTop(screen);
        int shown = Math.min(mine.size(), MAX_ROWS);

        for (int i = 0; i < shown; i++) {
            int rowY = top + i * ROW_HEIGHT;
            if (mouseX < x || mouseX > x + w || mouseY < rowY || mouseY > rowY + ROW_HEIGHT) {
                continue;
            }

            MarketListing listing = mine.get(i);
            boolean isSelected = listing.id().equals(selectedId);

            if (isSelected && mouseX >= x + w - DELIST_BTN_WIDTH) {
                // 点中 × 按钮：真正下架
                ClientNetwork.send(new DelistMarketItemPacket(listing.id()));
                selectedId = null;
                return true;
            }

            // 其余位置：选中 / 取消选中
            selectedId = isSelected ? null : listing.id();
            return true;
        }
        return false;
    }
}
