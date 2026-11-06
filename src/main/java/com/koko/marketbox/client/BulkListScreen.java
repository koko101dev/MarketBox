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
public class BulkListScreen extends Screen implements FeedbackReceiver {

    private static final int PADDING = 10;
    /** 单行横向布局：勾选 / 图标 / 名称 / 数量 / 单价 / 小计 一行排完 */
    private static final int ROW_HEIGHT = 34;
    /** 每行高度固定，最多能显示的行数随窗口高度变化 */
    private int maxRows() {
        // 不再强制至少 3 行：矮窗口里第 3 行会压到底部按钮甚至屏幕外
        return Math.max(1, (this.height - listTop - PADDING - 28) / ROW_HEIGHT);
    }
    /** 当前滚动到第几行（按行号，不按像素） */
    private int scrollRow = 0;
    private static final int CHECKBOX_SIZE = 12;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();

    private int contentLeft;
    private int contentWidth;
    private int listTop;
    /** 勾选的物品 ID 集合：界面重建后据此恢复勾选状态，避免填了一半全被清空 */
    private final java.util.Set<String> selectedItems = new java.util.HashSet<>();
    /** 本地校验提示：未勾选、数量或价格无效时给出反馈 */
    private final Toast toast = new Toast();

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
        // 三个按钮从右往左依次排开，每个都扣掉前一个的宽度：
        // 之前后两个共用同一个基准点，导致「返回市场」和「返回」完全重叠
        int submitW = 90;
        int backMarketW = 70;
        int backW = 60;
        int gap = 8;
        int submitX = this.width - PADDING - submitW;
        int backMarketX = submitX - gap - backMarketW;
        int backX = backMarketX - gap - backW;

        this.addRenderableWidget(new StyledButton(submitX, bottomY, submitW, 20,
                Component.translatable("gui.marketbox.market.bulk_submit"), b -> submit()));
        // 二级页面：返回市场首页
        this.addRenderableWidget(new StyledButton(backMarketX, bottomY, backMarketW, 20,
                Component.translatable("gui.marketbox.market.bulk_back_market"), b -> this.onClose()));
        // 叫「返回」而不是「完成」：这个按钮只关窗口，不做提交
        this.addRenderableWidget(new StyledButton(backX, bottomY, backW, 20,
                Component.translatable("gui.marketbox.market.bulk_back"), b -> this.onClose()));
    }

    /** 收集背包里可卖的物品，同类合并，按数量排序（全部收集，超出可视行数靠滚动查看） */
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
                .forEach(e -> {
                    Row row = new Row(e.getKey(), e.getValue());
                    row.selected = selectedItems.contains(e.getKey().getItem().toString());
                    rows.add(row);
                });
    }

    /** 为每一行创建数量与单价输入框 */
    private void buildRowWidgets() {
        // 窗口缩放会重建界面：物品数量变少时，旧的滚动位置可能已经越界
        scrollRow = Math.max(0, Math.min(scrollRow, Math.max(0, rows.size() - maxRows())));
        int x = contentLeft + PADDING;
        int rowW = contentWidth - PADDING * 2;

        // 右对齐排布：写死 x + rowW - 286 在窄窗口会把数量框推进侧边栏
        int priceW = 62;
        int qtyW = 46;
        int priceX = x + rowW - priceW - 8;
        int qtyX = priceX - qtyW - 6;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rowY = listTop + (i - scrollRow) * ROW_HEIGHT;
            boolean visible = i >= scrollRow && i < scrollRow + maxRows();

            EditBox qty = new EditBox(this.font, qtyX, rowY + 9, qtyW, 16, Component.empty());
            qty.setMaxLength(6);
            qty.setFilter(s -> s.isEmpty() || s.matches("\\d{0,6}"));
            // 重建时保留用户已经填过的值
            qty.setValue(row.qtyBox != null ? row.qtyBox.getValue() : String.valueOf(row.available));
            qty.setHint(Component.translatable("gui.marketbox.market.bulk_qty"));
            qty.visible = visible;
            row.qtyBox = qty;
            this.addRenderableWidget(qty);

            EditBox price = new EditBox(this.font, priceX, rowY + 9, priceW, 16, Component.empty());
            price.setMaxLength(12);
            price.setFilter(s -> s.isEmpty() || s.matches("\\d{0,9}(\\.\\d{0,2})?"));
            price.setValue(row.priceBox != null ? row.priceBox.getValue() : "1.00");
            price.setHint(Component.translatable("gui.marketbox.market.bulk_price"));
            price.visible = visible;
            row.priceBox = price;
            this.addRenderableWidget(price);
        }
    }

    /** 滚动后只调整控件位置与可见性，不重建界面：重建会把已填好的数量单价清掉 */
    private void applyScroll() {
        int limit = maxRows();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean visible = i >= scrollRow && i < scrollRow + limit;
            int y = listTop + (i - scrollRow) * ROW_HEIGHT + 9;
            if (row.qtyBox != null) {
                row.qtyBox.visible = visible;
                row.qtyBox.setY(y);
            }
            if (row.priceBox != null) {
                row.priceBox.visible = visible;
                row.priceBox.setY(y);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxRow = Math.max(0, rows.size() - maxRows());
        int target = Math.max(0, Math.min(maxRow, scrollRow - (int) Math.signum(scrollY)));
        if (target != scrollRow) {
            scrollRow = target;
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

        // 分页提示：物品太多时可以滚轮翻看，不再静默丢弃
        if (rows.size() > maxRows()) {
            String page = (scrollRow / maxRows() + 1) + " / " + ((rows.size() + maxRows() - 1) / maxRows());
            guiGraphics.drawString(font, page, x + w - font.width(page), listTop - 12, 0x777777, false);
        }

        for (int i = 0; i < rows.size(); i++) {
            if (i < scrollRow || i >= scrollRow + maxRows()) {
                continue;
            }
            Row row = rows.get(i);
            int rowY = listTop + (i - scrollRow) * ROW_HEIGHT;

            if (i % 2 == 0) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x12FFFFFF);
            }
            if (row.selected) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x2255FF55);
            }

            int boxX = x + 8;
            int boxY = rowY + 11;
            guiGraphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, 0xFF2A2A2A);
            guiGraphics.renderOutline(boxX, boxY, CHECKBOX_SIZE, CHECKBOX_SIZE, 0xFF888888);
            if (row.selected) {
                // 选中时整格填满，只保留 2px 边框
                guiGraphics.fill(boxX + 2, boxY + 2,
                        boxX + CHECKBOX_SIZE - 2, boxY + CHECKBOX_SIZE - 2, 0xFF55FF55);
            }

            guiGraphics.renderItem(row.stack, x + 30, rowY + 9);

            // 名称宽度按输入框实际位置截断，窄窗口不会压到数量框上
            int nameRight = row.qtyBox != null ? row.qtyBox.getX() - 6 : x + 214;
            String name = font.plainSubstrByWidth(row.stack.getHoverName().getString(),
                    Math.max(40, nameRight - (x + 54)));
            guiGraphics.drawString(font, name, x + 54, rowY + 13, 0xEEEEEE, false);
            guiGraphics.drawString(font, "×" + row.available,
                    x + 54 + font.width(name) + 6, rowY + 13, 0x888888, false);

            // 小计挪到名称下方：输入框改右对齐后，行尾已经没有它的位置
            if (row.selected) {
                String sub = MoneyUtils.formatValue(row.subtotal());
                guiGraphics.drawString(font, sub, x + 54, rowY + 23, 0xFFFF55, false);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(font,
                Component.translatable("gui.marketbox.market.expiry_fixed", ClientData.getListingExpiryMinutes()),
                contentLeft + PADDING, this.height - PADDING - 14, 0x777777, false);

        String summary = Component.translatable("gui.marketbox.market.bulk_summary",
                selectedCount(), MoneyUtils.formatValue(selectedTotal())).getString();
        guiGraphics.drawString(font, summary, contentLeft + PADDING + 140,
                this.height - PADDING - 14, 0xAAAAAA, false);

        toast.render(guiGraphics, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        // 纵向也要有上界：列表下方是按钮区，滚动后仅靠 index 范围可能命中不可见的行
        if (button == 0 && mouseX >= x && mouseX <= x + w
                && mouseY >= listTop && mouseY < listTop + maxRows() * ROW_HEIGHT) {
            // 加上滚动行号：否则滚到第二屏后点中的还是第一屏的条目
            int index = (int) ((mouseY - listTop) / ROW_HEIGHT) + scrollRow;
            if (index >= 0 && index < rows.size()) {
                // 只有点最左侧的勾选框才切换，避免点输入框时误触
                if (mouseX <= x + 8 + CHECKBOX_SIZE) {
                    Row row = rows.get(index);
                    row.selected = !row.selected;
                    String key = row.stack.getItem().toString();
                    if (row.selected) {
                        selectedItems.add(key);
                    } else {
                        selectedItems.remove(key);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 把勾选的行打包提交 */
    private void submit() {
        if (rows.stream().noneMatch(r -> r.selected)) {
            toast.show(Component.translatable("gui.marketbox.market.bulk_nothing_selected"), true);
            return;
        }

        List<BulkListMarketItemPacket.Entry> entries = new ArrayList<>();
        int invalidIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!row.selected) {
                continue;
            }
            // 空输入按 0 处理并提示，避免用户清空后误以「全部数量 + 单价 1」提交
            if (row.qtyBox.getValue().trim().isEmpty() || row.priceBox.getValue().trim().isEmpty()) {
                invalidIndex = i;
                break;
            }
            int qty = row.quantity();
            double price = row.price();
            if (qty <= 0 || price <= 0) {
                invalidIndex = i;
                break;
            }
            ItemStack stack = row.stack.copy();
            stack.setCount(qty);
            // price 为挂单总价：单条上架也是 price*quantity 后提交
            // 传单价会让买家以 1/N 的价格买走整组物品
            entries.add(new BulkListMarketItemPacket.Entry(stack, price * qty, qty));
        }

        if (entries.isEmpty()) {
            toast.show(Component.translatable("gui.marketbox.market.bulk_invalid_input"), true);
            return;
        }
        if (invalidIndex >= 0) {
            // 直接指出哪一行有问题，而不是笼统说「有 N 项无效」
            toast.show(Component.translatable("gui.marketbox.market.bulk_row_invalid", invalidIndex + 1), true);
            return;
        }
        ClientNetwork.send(new BulkListMarketItemPacket(entries, ClientData.getListingExpiryMinutes()));
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

    @Override
    public void showFeedback(Component message, boolean isError) {
        toast.show(message, isError);
    }
}
