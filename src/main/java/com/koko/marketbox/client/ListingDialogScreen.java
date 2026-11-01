package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.networking.packet.ListMarketItemPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 上架弹窗。
 *
 * 数量用滑条拖动（可精确到任意整数），配合「步进按钮」快速调整；
 * 步长按物品价值自适应——贵的东西一次 1 个，便宜的一次 64 个，
 * 避免在钻石这种物品上误点一下就全挂出去。
 */
public class ListingDialogScreen extends Screen {

    private static final int W = 320;
    private static final int H = 266;
    private static final int PAD = 10;

    private final Screen parent;
    private final ItemStack item;
    /** 背包里同物品的总数 */
    private final int maxCount;

    private int left;
    private int top;
    private int quantity = 1;
    private int expiryMinutes = 60;

    private QuantitySlider slider;
    private EditBox priceBox;
    private EditBox limitBox;
    private StyledButton expiryButton;

    public ListingDialogScreen(Screen parent, ItemStack item, int maxCount) {
        super(Component.translatable("gui.marketbox.market.list_dialog_title"));
        this.parent = parent;
        this.item = item;
        this.maxCount = Math.max(1, maxCount);
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;

        // 数量滑条
        this.slider = new QuantitySlider(left + PAD, top + 62, W - PAD * 2, 16);
        this.addRenderableWidget(this.slider);

        // 步进按钮：步长按价值自适应
        int step = stepSize();
        int btnY = top + 84;
        int bw = 62;
        int gap = 4;
        this.addRenderableWidget(new StyledButton(left + PAD, btnY, bw, 16,
                Component.literal("-" + step), b -> setQuantity(quantity - step)));
        this.addRenderableWidget(new StyledButton(left + PAD + bw + gap, btnY, bw, 16,
                Component.literal("+" + step), b -> setQuantity(quantity + step)));
        this.addRenderableWidget(new StyledButton(left + PAD + (bw + gap) * 2, btnY, W - PAD * 2 - (bw + gap) * 2, 16,
                Component.translatable("gui.marketbox.market.qty_all"), b -> setQuantity(maxCount)));

        // 价格输入
        this.priceBox = new EditBox(this.font, left + PAD, top + 126, 180, 16, Component.empty());
        this.priceBox.setMaxLength(10);
        this.priceBox.setFilter(s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
        this.addRenderableWidget(this.priceBox);

        this.addRenderableWidget(new StyledButton(left + PAD + 186, top + 126, W - PAD * 2 - 186, 16,
                Component.translatable("gui.marketbox.market.follow_lowest"), b -> followLowest()));

        // 时长循环按钮
        this.expiryButton = new StyledButton(left + PAD + 42, top + 184, 110, 16,
                expiryLabel(), b -> cycleExpiry());
        this.addRenderableWidget(this.expiryButton);

        // 每人限购（0 = 不限）
        this.limitBox = new EditBox(this.font, left + PAD + 42, top + 208, 60, 16, Component.empty());
        this.limitBox.setMaxLength(4);
        this.limitBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        this.limitBox.setValue("0");
        this.addRenderableWidget(this.limitBox);

        // 底部按钮
        int bottomY = top + H - 32;
        this.addRenderableWidget(new StyledButton(left + W / 2 - 100, bottomY, 92, 20,
                Component.translatable("gui.marketbox.market.cancel"), b -> close()));
        this.addRenderableWidget(new StyledButton(left + W / 2 + 8, bottomY, 92, 20,
                Component.translatable("gui.marketbox.market.confirm_list"), b -> submit()));
    }

    /** 步长：价值越高步长越小，避免误操作 */
    private int stepSize() {
        if (maxCount <= 1) {
            return 1;
        }
        double unit = referenceUnitPrice();
        int step;
        if (unit > 0) {
            // 有市场价：按价值决定，越贵步长越小
            if (unit >= 1000) {
                step = 1;
            } else if (unit >= 100) {
                step = 4;
            } else if (unit >= 10) {
                step = 16;
            } else {
                step = 64;
            }
        } else {
            // 没有市场价：按可堆叠数量推断，避免新物品一步跳走大半
            if (maxCount <= 8) {
                step = 1;
            } else if (maxCount <= 32) {
                step = 8;
            } else {
                step = 16;
            }
        }
        return Math.max(1, Math.min(step, maxCount));
    }

    /** 参考单价：只用市场最低单价，没有挂单返回 -1 */
    private double referenceUnitPrice() {
        return lowestUnitPrice();
    }

    /** 同物品在市场上的最低单价；无挂单返回 -1 */
    private double lowestUnitPrice() {
        List<MarketListing> all = ClientData.getMarketListings();
        double min = -1;
        for (MarketListing listing : all) {
            if (!ItemStack.isSameItemSameComponents(listing.item(), item)) {
                continue;
            }
            int count = Math.max(1, listing.item().getCount());
            double unit = listing.price() / count;
            if (min < 0 || unit < min) {
                min = unit;
            }
        }
        return min;
    }

    private int sameItemListingCount() {
        List<MarketListing> all = ClientData.getMarketListings();
        int count = 0;
        for (MarketListing listing : all) {
            if (ItemStack.isSameItemSameComponents(listing.item(), item)) {
                count++;
            }
        }
        return count;
    }

    private void setQuantity(int value) {
        this.quantity = Mth.clamp(value, 1, maxCount);
        if (slider != null) {
            slider.syncFromQuantity();
        }
    }

    private void followLowest() {
        double lowest = lowestUnitPrice();
        if (lowest <= 0) {
            // 没有同类挂单时留空，让玩家自行定价
            return;
        }
        // 填的是单价：比市场最低再低 1%，抢在队列前面成交
        priceBox.setValue(String.format("%.2f", Math.max(0.01, lowest * 0.99)));
    }

    private Component expiryLabel() {
        return Component.translatable("gui.marketbox.market.expiry_value", String.valueOf(expiryMinutes));
    }

    private void cycleExpiry() {
        expiryMinutes = switch (expiryMinutes) {
            case 30 -> 60;
            case 60 -> 180;
            case 180 -> 360;
            default -> 30;
        };
        expiryButton.setMessage(expiryLabel());
    }

    private void submit() {
        double unitPrice;
        try {
            // 输入框里是单价，服务端存的是整单总价
            unitPrice = Double.parseDouble(priceBox.getValue());
        } catch (NumberFormatException e) {
            return;
        }
        if (unitPrice <= 0) {
            return;
        }
        int limit = 0;
        try {
            limit = Integer.parseInt(limitBox.getValue().isEmpty() ? "0" : limitBox.getValue());
        } catch (NumberFormatException ignored) {
        }
        double total = unitPrice * quantity;
        ClientNetwork.send(new ListMarketItemPacket(item, total, quantity, expiryMinutes, Math.max(0, limit)));
        close();
    }

    private void close() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.fill(left, top, left + W, top + H, 0xF0101010);
        guiGraphics.renderOutline(left, top, W, H, 0xFF666666);

        var font = this.font;
        guiGraphics.drawCenteredString(font, this.title, left + W / 2, top + 6, 0xFFAA00);

        // 物品图标与名称
        guiGraphics.renderItem(item, left + PAD, top + 18);
        guiGraphics.drawString(font, item.getHoverName(), left + PAD + 20, top + 22, 0xFFFFFF, false);
        String owned = Component.translatable("gui.marketbox.market.owned_count",
                String.valueOf(maxCount)).getString();
        guiGraphics.drawString(font, owned, left + PAD + 20, top + 34, 0x888888, false);

        // 数量
        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.qty_label"),
                left + PAD, top + 52, 0xAAAAAA, false);
        String qtyText = quantity + " / " + maxCount;
        guiGraphics.drawString(font, qtyText, left + W - PAD - font.width(qtyText), top + 52, 0x55FF55, false);

        // 价格
        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.unit_price_label"),
                left + PAD, top + 114, 0xAAAAAA, false);

        double lowest = lowestUnitPrice();
        String marketLine;
        if (lowest > 0) {
            marketLine = Component.translatable("gui.marketbox.market.market_lowest",
                    String.format("%.2f", lowest), String.valueOf(sameItemListingCount())).getString();
        } else {
            marketLine = Component.translatable("gui.marketbox.market.no_listing_yet").getString();
        }
        guiGraphics.drawString(font, marketLine, left + PAD, top + 148, 0x888888, false);

        // 合计与预计实收：单价 × 数量，再扣手续费
        double feePercent = ClientData.getMarketFeePercent();
        try {
            double unitPrice = Double.parseDouble(priceBox.getValue());
            if (unitPrice > 0) {
                double total = unitPrice * quantity;
                String totalText = Component.translatable("gui.marketbox.market.total_price",
                        String.valueOf(quantity), MoneyUtils.formatValue(total)).getString();
                guiGraphics.drawString(font, totalText, left + PAD, top + 160, 0xFFFF55, false);

                if (feePercent > 0) {
                    double netAmount = total * (1 - feePercent / 100.0);
                    String netText = Component.translatable("gui.marketbox.market.net_income",
                            MoneyUtils.formatValue(netAmount)).getString();
                    guiGraphics.drawString(font, netText, left + PAD, top + 172, 0x66AA66, false);
                }
            }
        } catch (NumberFormatException ignored) {
        }

        // 时长
        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.expiry_label"),
                left + PAD, top + 188, 0xAAAAAA, false);

        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.limit_label_short"),
                left + PAD, top + 212, 0xAAAAAA, false);
        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.no_limit_hint"),
                left + PAD + 108, top + 212, 0x777777, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 数量滑条：拖动即可选中 1~maxCount 之间的任意整数 */
    private class QuantitySlider extends AbstractSliderButton {
        QuantitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.literal("1"), 0.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.valueOf(quantity)));
        }

        @Override
        protected void applyValue() {
            quantity = Mth.clamp(1 + (int) Math.round(value * (maxCount - 1)), 1, maxCount);
            updateMessage();
        }

        /** 外部改数量后同步滑条位置 */
        void syncFromQuantity() {
            this.value = maxCount > 1 ? (double) (quantity - 1) / (maxCount - 1) : 0.0;
            updateMessage();
        }
    }
}
