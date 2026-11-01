package com.koko.marketbox.client;

import com.koko.marketbox.networking.packet.ListMarketItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;

/**
 * MarketScreen 的按钮状态与交易动作。拆出以降低单文件行数，业务逻辑未改动。
 */
final class MarketScreenActions {
    private MarketScreenActions() {
    }

    static void updateCategoryButtons(MarketScreen screen) {
        for (int i = 0; i < screen.categoryButtons.size(); i++) {
            StyledButton btn = screen.categoryButtons.get(i);
            Category cat = Category.values()[i];
            btn.setHighlighted(cat == screen.currentCategory);
        }
    }

    static void updateWidgetStates(MarketScreen screen) {
        boolean active = !screen.isSelectingItem;
        screen.priceInput.visible = true; // Keep visible
        screen.priceInput.setEditable(active);
        screen.quantityInput.visible = true; // Keep visible
        screen.quantityInput.setEditable(active);
        screen.durationInput.visible = true; // Keep visible
        screen.durationInput.setEditable(active);
        screen.sellButton.visible = true; // Keep visible
        screen.sellButton.active = active;
        screen.searchBox.visible = true; // Keep visible
        screen.searchBox.setEditable(active);
        
        // Also find and update the claim button and back button
        screen.children().forEach(r -> {
            if (r instanceof StyledButton btn) {
                // Keep everything visible but control active state
                btn.visible = true;
                // 侧边栏导航始终可用，其余按钮跟随选择状态
                if (!isNavButton(btn.getMessage())) {
                    btn.active = active;
                }
            }
        });
    }

    /** 判断按钮是否为侧边栏导航项（这些按钮不随物品选择状态禁用） */
    private static boolean isNavButton(Component message) {
        return message.equals(Component.translatable("gui.marketbox.nav.bank"))
                || message.equals(Component.translatable("gui.marketbox.nav.market"))
                || message.equals(Component.translatable("gui.marketbox.nav.settings"));
    }

    static void handleSell(MarketScreen screen) {
        if (screen.selectedItem.isEmpty()) return;
        String priceText = screen.priceInput.getValue();
        String quantityText = screen.quantityInput.getValue();
        String durationText = screen.durationInput.getValue();
        
        if (priceText.isEmpty() || quantityText.isEmpty()) return;
        
        // If duration is empty, show error
        if (durationText.isEmpty()) {
            screen.showError(Component.translatable("gui.marketbox.market.error.empty_duration"));
            return;
        }
        
        try {
            double price = Double.parseDouble(priceText);
            int quantity = Integer.parseInt(quantityText);
            int duration = Integer.parseInt(durationText);
            
            if (price <= 0) {
                screen.showError(Component.translatable("gui.marketbox.market.error.invalid_price"));
                return;
            }
            if (quantity <= 0) {
                screen.showError(Component.translatable("gui.marketbox.market.error.invalid_quantity"));
                return;
            }
            if (duration <= 0 || duration > 60) {
                screen.showError(Component.translatable("gui.marketbox.market.error.invalid_duration"));
                return;
            }
            
            ClientNetwork.send(new ListMarketItemPacket(screen.selectedItem, price, quantity, duration));
            
            // Play click sound
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            
            screen.selectedItem = ItemStack.EMPTY;
            screen.priceInput.setValue("");
            screen.quantityInput.setValue("");
            screen.durationInput.setValue(""); // Reset to empty
        } catch (NumberFormatException ignored) {}
    }
}
