package com.koko.marketbox.client;

import com.koko.marketbox.networking.packet.BulkListMarketItemPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量上架：勾选多种物品，各自填数量与单价，一次提交全部。
 *
 * 与逐个上架的区别：
 * - 一屏内同时设置多件物品，不必反复开关弹窗
 * - 数量与单价直接在行内填写，实时显示小计
 * - 提交时合并成一个网络包，否则单条包的限流会把后面的拒掉
 */
public class BulkListScreen extends Screen {

    private static final int PADDING = 10;
    /** 行高 44：名称一行 + 输入框一行 + 标签一行，40 高时标签会压到下一行 */
    private static final int ROW_HEIGHT = 44;
    /** 一屏能放的条数，超出部分不显示（按数量从多到少排） */
    private static final int MAX_ROWS = 7;
    private static final int CHECKBOX_SIZE = 12;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();

    private int contentLeft;
    private int contentWidth;
    private int listTop;
    private int expiryMinutes = 60;
    private StyledButton expiryButton;

    /** 单行状态：物品、可卖数量、是否勾选、数量与单价的输入框 */
    private static final class Row {
        final ItemStack stack;
        final int available;
        boolean selected;
        EditBox qtyBox;
        EditBox priceBox;

        Row(ItemStack stack, int available) {
            this.stack = stack;
            this.available = available;
        }

        int quantity() {
            try {
                int v = Integer.parseInt(qtyBox == null ? "" : qtyBox.getValue().trim());
                return Math.max(0, Math.min(v, available));
            } catch (Exception e) {
                return available;
            }
        }

        double price() {
            try {
                double v = Double.parseDouble(priceBox == null ? "" : priceBox.getValue().trim());
                return v > 0 && Double.isFinite(v) ? v : 1.0;
            } catch (Exception e) {
                return 1.0;
            }
        }

        double subtotal() {
            return price() * quantity();
        }
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
        this.listTop = 44;

        Sidebar.buttons(this, Sidebar.Page.MARKET).forEach(this::addRenderableWidget);

        collectSellableItems();
        buildRowWidgets();

        int bottomY = this.height - PADDING - 20;
        this.expiryButton = new StyledButton(contentLeft + PADDING, bottomY, 90, 20,
                expiryLabel(), b -> {
            expiryMinutes = switch (expiryMinutes) {
                case 30 -> 60;
                case 60 -> 180;
                case 180 -> 360;
                default -> 30;
            };
            expiryButton.setMessage(expiryLabel());
        });
        this.addRenderableWidget(expiryButton);

        this.addRenderableWidget(new StyledButton(this.width - PADDING - 90, bottomY, 90, 20,
                Component.translatable("gui.marketbox.market.bulk_submit"), b -> submit()));
        this.addRenderableWidget(new StyledButton(this.width - PADDING - 90 - 64, bottomY, 60, 20,
                Component.translatable("gui.done"), b -> this.onClose()));
    }

    private Component expiryLabel() {
        return Component.translatable("gui.marketbox.market.bulk_expiry", expiryMinutes);
    }

    /** 收集背包里可卖的物品，同类合并，按数量排序后取前若干条 */
    private void collectSellableItems() {
        rows.clear();
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
            ItemStack key = stack.copy();
            key.setCount(1);
            merged.merge(key, stack.getCount(), Integer::sum);
        }

        merged.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(MAX_ROWS)
                .forEach(e -> rows.add(new Row(e.getKey(), e.getValue())));
    }

    /** 为每一行创建数量与单价输入框 */
    private void buildRowWidgets() {
        int x = contentLeft + PADDING;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = listTop + i * ROW_HEIGHT;

            EditBox qty = new EditBox(this.font, x + 68, rowY + 18, 46, 15, Component.empty());
            qty.setMaxLength(6);
            qty.setFilter(s -> s.isEmpty() || s.matches("\\d{0,6}"));
            qty.setValue(String.valueOf(row.available));
            row.qtyBox = qty;
            this.addRenderableWidget(qty);

            EditBox price = new EditBox(this.font, x + 152, rowY + 18, 62, 15, Component.empty());
            price.setMaxLength(12);
            price.setFilter(s -> s.isEmpty() || s.matches("\\d{0,9}(\\.\\d{0,2})?"));
            price.setValue("1.00");
            row.priceBox = price;
            this.addRenderableWidget(price);
        }
    }

    private int selectedCount() {
        return (int) rows.stream().filter(r -> r.selected).count();
    }

    private double selectedTotal() {
        return rows.stream().filter(r -> r.selected).mapToDouble(Row::subtotal).sum();
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
                contentLeft + PADDING, 26, 0x777777, false);
        guiGraphics.fill(contentLeft + PADDING, 40, this.width - PADDING, 41, 0xFF3A3A3A);

        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.marketbox.market.bulk_empty"),
                    contentLeft + contentWidth / 2, listTop + 40, 0x666666);
        }

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = listTop + i * ROW_HEIGHT;

            if (i % 2 == 0) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x12FFFFFF);
            }
            if (row.selected) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x2255FF55);
            }

            int boxX = x + 4;
            int boxY = rowY + 6;
            guiGraphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF333333);
            guiGraphics.renderOutline(boxX, boxY, CHECKBOX_SIZE, CHECKBOX_SIZE, 0xFF888888);
            if (row.selected) {
                // 画一个勾，比整块填绿更像复选框
                guiGraphics.fill(boxX + 2, boxY + 6, boxX + 5, boxY + 9, 0xFF55FF55);
                guiGraphics.fill(boxX + 4, boxY + 7, boxX + 9, boxY + 4, 0xFF55FF55);
            }

            guiGraphics.renderItem(row.stack, x + 22, rowY + 5);

            String name = font.plainSubstrByWidth(row.stack.getHoverName().getString(), 110);
            guiGraphics.drawString(font, name, x + 44, rowY + 8, 0xEEEEEE, false);
            guiGraphics.drawString(font, "×" + row.available,
                    x + 44 + font.width(name) + 6, rowY + 8, 0x888888, false);

            if (row.selected) {
                String sub = MoneyUtils.formatValue(row.subtotal());
                guiGraphics.drawString(font, sub,
                        x + w - font.width(sub) - 6, rowY + 8, 0xFFFF55, false);
            }

            // 标签与输入框左缘对齐：数量框在 x+68、单价框在 x+152
            guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.bulk_qty"),
                    x + 69, rowY + 34, 0x666666, false);
            guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.bulk_price"),
                    x + 153, rowY + 34, 0x666666, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        String summary = Component.translatable("gui.marketbox.market.bulk_summary",
                selectedCount(), MoneyUtils.formatValue(selectedTotal())).getString();
        guiGraphics.drawString(font, summary, contentLeft + PADDING + 100,
                this.height - PADDING - 14, 0xAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        if (button == 0 && mouseX >= x && mouseX <= x + w && mouseY >= listTop) {
            int index = (int) ((mouseY - listTop) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                // 只有点最左侧的勾选框才切换，避免点输入框时误触
                if (mouseX <= x + 4 + CHECKBOX_SIZE + 2) {
                    Row row = rows.get(index);
                    row.selected = !row.selected;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 把勾选的行打包提交 */
    private void submit() {
        List<BulkListMarketItemPacket.Entry> entries = new ArrayList<>();
        for (Row row : rows) {
            if (!row.selected) {
                continue;
            }
            int qty = row.quantity();
            double price = row.price();
            if (qty <= 0 || price <= 0) {
                continue;
            }
            ItemStack stack = row.stack.copy();
            stack.setCount(qty);
            entries.add(new BulkListMarketItemPacket.Entry(stack, price, qty));
        }

        if (entries.isEmpty()) {
            return;
        }
        ClientNetwork.send(new BulkListMarketItemPacket(entries, expiryMinutes));
        Minecraft.getInstance().setScreen(parent);
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
