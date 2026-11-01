package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import java.util.List;

/**
 * MarketScreen 的渲染辅助：只做绘制，不持有状态。
 * 拆出原因：MarketScreen 超过 500 行规范上限。
 */
final class MarketScreenRenderer {
    private MarketScreenRenderer() {
    }

    static void renderLeftSection(MarketScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = screen.leftPos + screen.PADDING;
        int startY = screen.topPos + 45;
        
        // Item selection box
        int boxSize = 40;
        int boxX = screen.leftPos + (screen.sidebarWidth - boxSize) / 2;
        int boxY = startY;
        
        boolean hovered = mouseX >= boxX && mouseX <= boxX + boxSize && mouseY >= boxY && mouseY <= boxY + boxSize;
        int borderColor = screen.isSelectingItem ? 0xFF00FF00 : (hovered ? 0xFFFFFFFF : 0xFF555555);
        
        guiGraphics.fill(boxX, boxY, boxX + boxSize, boxY + boxSize, 0x44000000);
        guiGraphics.renderOutline(boxX, boxY, boxSize, boxSize, borderColor);
        
        if (!screen.selectedItem.isEmpty()) {
            int itemSize = 16;
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(boxX + (boxSize - itemSize * 2) / 2, boxY + (boxSize - itemSize * 2) / 2, 0);
            guiGraphics.pose().scale(2.0f, 2.0f, 2.0f);
            guiGraphics.renderItem(screen.selectedItem, 0, 0);
            guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, screen.selectedItem, 0, 0);
            guiGraphics.pose().popPose();
        } else {
            String text = screen.isSelectingItem ? "..." : "+";
            guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, text, boxX + boxSize / 2, boxY + (boxSize - 8) / 2, 0xAAAAAA);
        }
        
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.add_item"), screen.leftPos + screen.sidebarWidth / 2, boxY - 12, 0xDDDDDD);
        
        // Price and Quantity labels
        int totalWidth = screen.sidebarWidth - screen.PADDING * 2;
        int fieldWidth = totalWidth / 3;
        
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.price_label"), screen.leftPos + screen.PADDING + fieldWidth / 2, screen.priceInput.getY() - 17, 0xAAAAAA);
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.quantity_label"), screen.leftPos + screen.PADDING + fieldWidth + fieldWidth / 2, screen.quantityInput.getY() - 17, 0xAAAAAA);
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.duration_label"), screen.leftPos + screen.PADDING + fieldWidth * 2 + fieldWidth / 2, screen.durationInput.getY() - 17, 0xAAAAAA);
        
        // Input backgrounds
        int bgY = screen.priceInput.getY() - 7;
        int bgHeight = 20;
        
        // Combined BG for all inputs
        guiGraphics.fill(screen.leftPos + screen.PADDING, bgY, screen.leftPos + screen.PADDING + totalWidth, bgY + bgHeight, 0xFF050505);
        guiGraphics.renderOutline(screen.leftPos + screen.PADDING, bgY, totalWidth, bgHeight, 0xFF555555);
        
        // Middle divider lines
        int dividerX1 = screen.leftPos + screen.PADDING + fieldWidth;
        int dividerX2 = screen.leftPos + screen.PADDING + fieldWidth * 2;
        guiGraphics.fill(dividerX1, bgY + 2, dividerX1 + 1, bgY + bgHeight - 2, 0xFF444444);
        guiGraphics.fill(dividerX2, bgY + 2, dividerX2 + 1, bgY + bgHeight - 2, 0xFF444444);
    }

    static void renderReturnedItemsSection(MarketScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = screen.leftPos + screen.PADDING;
        int startY = screen.claimButton.getY() + screen.claimButton.getHeight() + 2; // buttonGap = 2
        int width = screen.sidebarWidth - screen.PADDING * 2;
        int height = (screen.topPos + screen.guiHeight - screen.PADDING) - startY;
        
        // Background
        guiGraphics.fill(startX, startY, startX + width, startY + height, 0xFF050505);
        guiGraphics.renderOutline(startX, startY, width, height, 0xFF555555);
        
        List<ItemStack> items = ClientData.getReturnedItems();
        
        if (items.isEmpty()) {
            guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.no_returned_items_hint"), startX + width / 2, startY + height / 2 - 4, 0x555555);
            screen.returnedItemsScrollOffset = 0;
        } else {
            // Render items in a list
            int itemHeight = 22;
            int totalContentHeight = items.size() * itemHeight;
            
            // Clamp scroll offset
            int maxScroll = Math.max(0, totalContentHeight - (height - 4));
            screen.returnedItemsScrollOffset = Mth.clamp(screen.returnedItemsScrollOffset, 0, maxScroll);
            
            guiGraphics.enableScissor(startX, startY + 1, startX + width, startY + height - 1);
            
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                int itemY = startY + 2 + i * itemHeight - (int)screen.returnedItemsScrollOffset;
                
                if (itemY + itemHeight < startY || itemY > startY + height) continue;
                
                // Item background
                boolean hovered = mouseX >= startX + 2 && mouseX <= startX + width - 2 && mouseY >= itemY && mouseY <= itemY + itemHeight - 2;
                guiGraphics.fill(startX + 2, itemY, startX + width - 2, itemY + itemHeight - 2, hovered ? 0x22FFFFFF : 0x11FFFFFF);
                
                // Item Icon
                guiGraphics.renderItem(stack, startX + 4, itemY + 2);
                guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, startX + 4, itemY + 2);
                
                // Item Name
                String name = stack.getHoverName().getString();
                float scale = 0.8f;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(startX + 24, itemY + 6, 0);
                guiGraphics.pose().scale(scale, scale, scale);
                guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, truncateString(screen, name, (int)((width - 28) / scale)), 0, 0, 0xDDDDDD, false);
                guiGraphics.pose().popPose();
                
                if (hovered) {
                    screen.activeTooltip = stack.getHoverName();
                }
            }
            
            guiGraphics.disableScissor();
            
            // Scrollbar for returned items
            if (totalContentHeight > height - 4) {
                int sbWidth = 1;
                int sbX = startX + width - 2;
                int sbHeight = (int)((float)(height - 4) / totalContentHeight * (height - 4));
                int sbY = startY + 2 + (int)(screen.returnedItemsScrollOffset / (totalContentHeight - (height - 4)) * ((height - 4) - sbHeight));
                guiGraphics.fill(sbX, startY + 2, sbX + sbWidth, startY + height - 2, 0x22FFFFFF);
                guiGraphics.fill(sbX, sbY, sbX + sbWidth, sbY + sbHeight, 0xAAFFFFFF);
            }
        }
    }

    static void renderRightSection(MarketScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startX = screen.leftPos + screen.sidebarWidth + screen.PADDING;
        int startY = screen.topPos + 45; // Start below categories
        int viewWidth = screen.mainWidth - screen.PADDING * 2;
        int viewHeight = screen.guiHeight - 55; // Adjust height for new startY
        
        List<MarketListing> allListings = ClientData.getMarketListings();
        String filter = screen.searchBox.getValue().toLowerCase();
        List<MarketListing> listings = allListings.stream()
                .filter(l -> l.item().getHoverName().getString().toLowerCase().contains(filter))
                .filter(l -> screen.currentCategory.matches(l.item())) // Add category filter
                .toList();
        
        // Grid calculations - Dynamic cards per row based on view width
        int cardPadding = 5;
        int cardsPerRow = Math.max(1, viewWidth / (screen.CARD_WIDTH + cardPadding));
        int totalRows = (int) Math.ceil((double) listings.size() / cardsPerRow);
        int contentHeight = totalRows * (screen.CARD_HEIGHT + cardPadding);
        
        // Scissors to clip content
        guiGraphics.enableScissor(startX, startY, startX + viewWidth, startY + viewHeight);
        
        for (int i = 0; i < listings.size(); i++) {
            MarketListing listing = listings.get(i);
            int row = i / cardsPerRow;
            int col = i % cardsPerRow;
            
            int x = startX + col * (screen.CARD_WIDTH + cardPadding);
            int y = startY + row * (screen.CARD_HEIGHT + cardPadding) - (int)screen.scrollOffset;
            
            if (y + screen.CARD_HEIGHT < startY || y > startY + viewHeight) continue;
            
            renderListingCard(screen, guiGraphics, listing, x, y, mouseX, mouseY);
        }
        
        guiGraphics.disableScissor();
        
        // Scrollbar
        if (contentHeight > viewHeight) {
            int sbWidth = 2;
            int sbX = screen.leftPos + screen.guiWidth - screen.PADDING + 2;
            int sbHeight = (int)((float)viewHeight / contentHeight * viewHeight);
            int sbY = startY + (int)(screen.scrollOffset / (contentHeight - viewHeight) * (viewHeight - sbHeight));
            guiGraphics.fill(sbX, startY, sbX + sbWidth, startY + viewHeight, 0x22FFFFFF);
            guiGraphics.fill(sbX, sbY, sbX + sbWidth, sbY + sbHeight, 0xAAFFFFFF);
        }
    }

    static String truncateString(MarketScreen screen, String text, int maxWidth) {
        if (net.minecraft.client.Minecraft.getInstance().font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = net.minecraft.client.Minecraft.getInstance().font.width(ellipsis);
        if (maxWidth < ellipsisWidth) return "...";
        
        return net.minecraft.client.Minecraft.getInstance().font.plainSubstrByWidth(text, maxWidth - ellipsisWidth) + ellipsis;
    }

    static void renderListingCard(MarketScreen screen, GuiGraphics guiGraphics, MarketListing listing, int x, int y, int mouseX, int mouseY) {
        boolean isMouseOverCard = mouseX >= x && mouseX <= x + screen.CARD_WIDTH && mouseY >= y && mouseY <= y + screen.CARD_HEIGHT;
        
        // Background
        guiGraphics.fill(x, y, x + screen.CARD_WIDTH, y + screen.CARD_HEIGHT, 0x66000000);
        guiGraphics.renderOutline(x, y, screen.CARD_WIDTH, screen.CARD_HEIGHT, 0xFF444444);
        
        // Item Icon - Centered at top
        int itemSize = 16;
        float iconScale = 1.8f;
        int scaledSize = (int)(itemSize * iconScale);
        guiGraphics.pose().pushPose();
        // Use a small Z offset to stay within standard GUI depth but be above card background
        guiGraphics.pose().translate(x + (screen.CARD_WIDTH - scaledSize) / 2.0, y + 6, 1.0);
        guiGraphics.pose().scale(iconScale, iconScale, iconScale);
        guiGraphics.renderItem(listing.item(), 0, 0);
        // Removed renderItemDecorations to hide quantity number
        guiGraphics.pose().popPose();
        
        // Text alignment
        int textX = x + 4;
        int textY = y + 38;
        float textScale = 0.75f;
        int lineSpacing = 12;

        guiGraphics.pose().pushPose();
        // Use a slightly higher Z offset for text
        guiGraphics.pose().translate(textX, textY, 2.0); 
        guiGraphics.pose().scale(textScale, textScale, textScale);

        // Line 1: Item Name
        String name = listing.item().getHoverName().getString();
        int maxTextWidth = (int)((screen.CARD_WIDTH - 8) / textScale);
        
        // Calculate width for prefix
        int namePrefixWidth = net.minecraft.client.Minecraft.getInstance().font.width(Component.translatable("gui.marketbox.market.card.item_name", ""));
        String truncatedName = truncateString(screen, name, maxTextWidth - namePrefixWidth);
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.card.item_name", truncatedName), 0, 0, 0xFFFFFF, false);
        
        // If truncated and hovered, set tooltip
        if (isMouseOverCard && !name.equals(truncatedName)) {
            screen.activeTooltip = Component.literal(name);
        }

        // Line 2: Price
        String priceText = MoneyUtils.formatValue(listing.price());
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.card.price", priceText), 0, lineSpacing, 0xFFFF55, false);
        
        // Line 3: Quantity
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.card.quantity", listing.item().getCount()), 0, lineSpacing * 2, 0xAAAAAA, false);
        
        // Line 4: Seller
        String seller = listing.sellerName();
        int sellerPrefixWidth = net.minecraft.client.Minecraft.getInstance().font.width(Component.translatable("gui.marketbox.market.card.seller", ""));
        String truncatedSeller = truncateString(screen, seller, maxTextWidth - sellerPrefixWidth);
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.card.seller", truncatedSeller), 0, lineSpacing * 3, 0x888888, false);

        // If truncated and hovered (and item name didn't already set a tooltip), set tooltip
        if (isMouseOverCard && screen.activeTooltip == null && !seller.equals(truncatedSeller)) {
            screen.activeTooltip = Component.literal(seller);
        }

        // Line 5: Countdown
        long now = System.currentTimeMillis();
        long expiryTimeMs = (long) listing.expiryMinutes() * 60 * 1000;
        long remaining = (listing.createdAt() + expiryTimeMs) - now;
        
        String timeStr;
        if (remaining <= 0) {
            timeStr = "00:00";
        } else {
            long totalSecs = remaining / 1000;
            long mins = totalSecs / 60;
            long secs = totalSecs % 60;
            timeStr = String.format("%02d:%02d", mins, secs);
        }
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.card.countdown", timeStr), 0, lineSpacing * 4, 0xAAAAAA, false);

        guiGraphics.pose().popPose();

        // Buy/Delist Button at bottom of card
        int btnW = screen.CARD_WIDTH - 8;
        int btnH = 14;
        int btnX = x + 4;
        int btnY = y + screen.CARD_HEIGHT - btnH - 4;
        
        Player player = Minecraft.getInstance().player;
        boolean isOwner = player != null && listing.sellerUUID().equals(player.getUUID());
        
        if (isOwner) {
            // Split button into Buy and Delist
            int halfW = (btnW - 2) / 2;
            
        // Buy half
        boolean buyHovered = !screen.isSelectingItem && mouseX >= btnX && mouseX <= btnX + halfW && mouseY >= btnY && mouseY <= btnY + btnH;
        guiGraphics.fill(btnX, btnY, btnX + halfW, btnY + btnH, buyHovered ? 0xFF55FF55 : 0xFF33AA33);
        guiGraphics.renderOutline(btnX, btnY, halfW, btnH, 0xFFAAAAAA);
        
        Component buyText = Component.translatable("gui.marketbox.market.buy_button");
        renderButtonText(screen, guiGraphics, buyText, btnX, btnY, halfW, btnH);
        
        // Delist half
        int delistX = btnX + halfW + 2;
        boolean delistHovered = !screen.isSelectingItem && mouseX >= delistX && mouseX <= delistX + halfW && mouseY >= btnY && mouseY <= btnY + btnH;
        guiGraphics.fill(delistX, btnY, delistX + halfW, btnY + btnH, delistHovered ? 0xFFFF5555 : 0xFFAA3333);
        guiGraphics.renderOutline(delistX, btnY, halfW, btnH, 0xFFAAAAAA);
        
        Component delistText = Component.translatable("gui.marketbox.market.delist_button");
        renderButtonText(screen, guiGraphics, delistText, delistX, btnY, halfW, btnH);
    } else {
        // Full width Buy button
        boolean btnHovered = !screen.isSelectingItem && mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHovered ? 0xFF55FF55 : 0xFF33AA33);
        guiGraphics.renderOutline(btnX, btnY, btnW, btnH, 0xFFAAAAAA);
            
            Component buyText = Component.translatable("gui.marketbox.market.buy_button");
            renderButtonText(screen, guiGraphics, buyText, btnX, btnY, btnW, btnH);
        }
    }

    static void renderButtonText(MarketScreen screen, GuiGraphics guiGraphics, Component text, int x, int y, int w, int h) {
        int textW = net.minecraft.client.Minecraft.getInstance().font.width(text);
        float scale = 0.7f;
        // If text is still too wide, scale it down more
        if (textW * scale > w - 2) {
            scale = (w - 2) / (float)textW;
        }
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + (w - textW * scale) / 2.0, y + (h - 8 * scale) / 2.0, 3.0);
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text, 0, 0, 0xFFFFFF, true);
        guiGraphics.pose().popPose();
    }

    static void renderPlayerInventory(MarketScreen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // 1. Render background dimming at a base level
        guiGraphics.pose().pushPose();
        // Move to a much higher Z level to ensure text and items are above everything
        guiGraphics.pose().translate(0, 0, 400); 
        guiGraphics.fill(0, 0, screen.width, screen.height, 0x88000000);
        
        int invW = 18 * 9 + 20;
        int invH = 18 * 4 + 30; 
        int invX = (screen.width - invW) / 2;
        int invY = (screen.height - invH) / 2;
        
        // 2. Render Modal background
        guiGraphics.fill(invX, invY, invX + invW, invY + invH, 0xFF101010);
        guiGraphics.renderOutline(invX, invY, invW, invH, 0xFFFFFFFF);
        
        guiGraphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, Component.translatable("gui.marketbox.market.your_inventory"), invX + invW / 2, invY + 8, 0xFFFFFF);
        
        int slotStartX = invX + 10;
        int slotStartY = invY + 22;
        
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < 36; i++) {
            int row = i / 9;
            int col = i % 9;
            int x = slotStartX + col * 18;
            int y = slotStartY + row * 18;
            
            // Map visual slot to inventory slot:
            // Visual Rows 0, 1, 2 -> Inventory Slots 9-17, 18-26, 27-35 (Main Inventory)
            // Visual Row 3 -> Inventory Slots 0-8 (Hotbar)
            int slotIdx;
            if (row < 3) {
                slotIdx = 9 + row * 9 + col;
            } else {
                slotIdx = col;
            }
            
            ItemStack stack = player.getInventory().getItem(slotIdx);
            boolean hovered = mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18;
            
            boolean isCurrency = MoneyUtils.isCurrency(stack);
            
            if (isCurrency) {
                guiGraphics.fill(x, y, x + 18, y + 18, 0x44FF0000); // Red tint for currency
            } else {
                guiGraphics.fill(x, y, x + 18, y + 18, hovered ? 0x66FFFFFF : 0x22000000);
            }
            guiGraphics.renderOutline(x, y, 18, 18, 0xFF444444);
            
            if (!stack.isEmpty()) {
                if (isCurrency) {
                    guiGraphics.setColor(1.0f, 0.5f, 0.5f, 0.8f); // Dim and redden
                }
                guiGraphics.renderItem(stack, x + 1, y + 1);
                guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, x + 1, y + 1);
                guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f); // Reset color
                
                if (hovered) {
                    hoveredStack = stack;
                }
            }
        }

        // 3. Render tooltip at the very end of the elevated pose
        if (!hoveredStack.isEmpty()) {
            // Tooltips need even higher Z to be on top of everything
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            guiGraphics.renderTooltip(net.minecraft.client.Minecraft.getInstance().font, hoveredStack, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }

        guiGraphics.pose().popPose();
    }
}
