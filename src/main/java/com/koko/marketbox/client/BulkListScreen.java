package com.koko.marketbox.client;

import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量上架：列出背包里可卖的物品，点某一项直接进入上架弹窗。
 *
 * 相比原来的「点背包物品 → 弹窗」，这里省去了打开背包、找物品的步骤，
 * 连着卖十几种东西时不必反复开关背包。上架完成回到本页可继续下一件。
 */
public class BulkListScreen extends Screen {

    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 34;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();

    private int contentLeft;
    private int contentWidth;
    private int listTop;
    private int listHeight;
    private float scrollOffset;

    /** 合并后的物品：同种物品汇总数量 */
    private record Entry(ItemStack stack, int count) {
    }

    public BulkListScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.market.bulk_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;
        this.listTop = HEADER_HEIGHT;
        this.listHeight = this.height - listTop - PADDING - 28;

        Sidebar.buttons(this, Sidebar.Page.MARKET).forEach(this::addRenderableWidget);
        this.addRenderableWidget(new StyledButton(
                this.width - PADDING - 60, this.height - PADDING - 20, 60, 18,
                Component.translatable("gui.done"), b -> this.onClose()));

        collectSellableItems();
    }

    /** 收集背包里可卖的物品，同类合并计数 */
    private void collectSellableItems() {
        entries.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        Map<ItemStack, Integer> merged = new LinkedHashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || MoneyUtils.isCurrency(stack)) {
                continue;
            }
            // 以单件作为 key，避免数量差异导致同类物品被拆成多行
            ItemStack key = stack.copy();
            key.setCount(1);
            merged.merge(key, stack.getCount(), Integer::sum);
        }

        merged.forEach((key, count) -> entries.add(new Entry(key, count)));
        entries.sort((a, b) -> Integer.compare(b.count(), a.count()));
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_HEIGHT - listHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 本模组界面统一不应用背景模糊
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }

        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getMarketBadgeCount());
        guiGraphics.fill(contentLeft, 0, this.width, this.height, 0xE6101010);

        var font = this.font;
        guiGraphics.drawString(font, this.title, contentLeft + PADDING, 12, 0xFFAA00, false);
        guiGraphics.drawString(font,
                Component.translatable("gui.marketbox.market.bulk_hint"),
                contentLeft + PADDING, 24, 0x777777, false);
        guiGraphics.fill(contentLeft + PADDING, HEADER_HEIGHT - 2,
                this.width - PADDING, HEADER_HEIGHT - 1, 0xFF3A3A3A);

        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        if (entries.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.marketbox.market.bulk_empty"),
                    x + w / 2, listTop + listHeight / 2 - 4, 0x666666);
        } else {
            guiGraphics.enableScissor(contentLeft, listTop, this.width, listTop + listHeight);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                int rowY = listTop + i * ROW_HEIGHT - (int) scrollOffset;
                if (rowY + ROW_HEIGHT < listTop || rowY > listTop + listHeight) {
                    continue;
                }

                boolean hovered = mouseX >= x && mouseX <= x + w
                        && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
                if (hovered) {
                    guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT, 0x33FFFFFF);
                } else if (i % 2 == 0) {
                    guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT, 0x12FFFFFF);
                }

                guiGraphics.renderItem(entry.stack(), x + 4, rowY + 4);

                String name = font.plainSubstrByWidth(entry.stack().getHoverName().getString(), w - 110);
                guiGraphics.drawString(font, name, x + 26, rowY + 8, 0xEEEEEE, false);

                String countText = "×" + entry.count();
                guiGraphics.drawString(font, countText,
                        x + w - font.width(countText) - 8, rowY + 8, 0xAAAAAA, false);

                if (hovered) {
                    String hint = Component.translatable("gui.marketbox.market.bulk_click").getString();
                    guiGraphics.drawString(font, hint,
                            x + w - font.width(hint) - 60, rowY + 8, 0xFFAA00, false);
                }
            }
            guiGraphics.disableScissor();
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        if (button == 0 && mouseX >= x && mouseX <= x + w
                && mouseY >= listTop && mouseY <= listTop + listHeight) {
            int index = (int) ((mouseY - listTop + scrollOffset) / ROW_HEIGHT);
            if (index >= 0 && index < entries.size()) {
                Entry entry = entries.get(index);
                // 直接复用现有上架弹窗，上架完回到本页继续下一件
                Minecraft.getInstance().setScreen(new ListingDialogScreen(
                        this, entry.stack().copy(), entry.count()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        if (max > 0) {
            scrollOffset = Mth.clamp((float) (scrollOffset - scrollY * 14), 0f, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 与 MC 的数学工具保持一致，避免额外依赖 */
    private static final class Mth {
        static float clamp(float value, float min, float max) {
            return value < min ? min : Math.min(value, max);
        }
    }
}
