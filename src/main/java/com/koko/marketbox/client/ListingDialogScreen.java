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
    private static final int H = 268;
    private static final int PAD = 10;

    private final Screen parent;
    private final ItemStack item;
    /** 背包里同物品的总数 */
    private final int maxCount;

    private int left;
    private int top;
    private int quantity = 1;

    private QuantitySlider slider;
    private EditBox priceBox;
    private EditBox limitBox;

    public ListingDialogScreen(Screen parent, ItemStack item, int maxCount) {
        super(Component.translatable("gui.marketbox.market.list_dialog_title"));
        this.parent = parent;
        this.item = item;
        this.maxCount = Math.max(1, maxCount);
    }

    @Override
    protected void init() {
        super.init();
        this.left = Math.max(2, (this.width - W) / 2);
        // 窗口比弹窗还矮时，至少保证顶部（标题与数量滑条）可见，不被推到屏幕外
        this.top = Math.max(2, (this.height - H) / 2);

        // 只有 1 个可卖时，滑条与步进按钮都没有意义，直接留白
        if (maxCount > 1) {
            this.slider = new QuantitySlider(left + PAD, top + 62, W - PAD * 2, 16);
            this.addRenderableWidget(this.slider);

            int step = stepSize();
            int btnY = top + 84;
            int bw = 62;
            int gap = 4;
            this.addRenderableWidget(new StyledButton(left + PAD, btnY, bw, 16,
                    Component.literal("-" + step), b -> setQuantity(quantity - step)));
            this.addRenderableWidget(new StyledButton(left + PAD + bw + gap, btnY, bw, 16,
                    Component.literal("+" + step), b -> setQuantity(quantity + step)));
            this.addRenderableWidget(new StyledButton(left + PAD + (bw + gap) * 2, btnY,
                    W - PAD * 2 - (bw + gap) * 2, 16,
                    Component.translatable("gui.marketbox.market.qty_all"), b -> setQuantity(maxCount)));
        }

        // 价格输入
        this.priceBox = new EditBox(this.font, left + PAD, top + 126, 180, 16, Component.empty());
        this.priceBox.setMaxLength(10);
        this.priceBox.setFilter(s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
        this.addRenderableWidget(this.priceBox);

        this.addRenderableWidget(new StyledButton(left + PAD + 186, top + 126, W - PAD * 2 - 186, 16,
                Component.translatable("gui.marketbox.market.follow_lowest"), b -> followLowest()));

        // 时长循环按钮

        // 底部按钮 y 先算出来：限购行要跟着它上移。窗口比弹窗矮时
        //（自动 GUI 缩放到 426×240 很常见）贴屏幕底，否则按钮会落到屏幕外
        int bottomY = Math.min(top + H - 32, this.height - 24);
        // 每人限购（0 = 不限）
        int limitY = Math.min(top + 214, bottomY - 24);
        this.limitBox = new EditBox(this.font, left + PAD + 42, limitY, 60, 16, Component.empty());
        this.limitBox.setMaxLength(4);
        this.limitBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        this.limitBox.setValue("0");
        this.addRenderableWidget(this.limitBox);

        // 底部按钮
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
        return Component.translatable("gui.marketbox.market.expiry_fixed", ClientData.getListingExpiryMinutes());
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
        // 上限由服主配置并同步到客户端，不能写死 100 万：
        // 调高时会把合法上架静默拒掉，调低时又放行服务端必然拒绝的价格
        if (total > ClientData.getMaxListingPrice()) {
            return;
        }
        ClientNetwork.send(new ListMarketItemPacket(item, total, quantity, ClientData.getListingExpiryMinutes(), Math.max(0, limit)));
        close();
    }

    private void close() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 界面自身不应用背景模糊：直接铺半透明底。
        // 这样无需依赖「关闭菜单模糊」开关，也不会受其他模组的全局设置影响。
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }

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

                if (total > ClientData.getMaxListingPrice()) {
                    guiGraphics.drawString(font,
                            Component.translatable("gui.marketbox.market.price_too_high").getString(),
                            left + PAD, top + 158, 0xFF5555, false);
                } else {
                    // 用一句完整描述代替分散的数字，先说卖什么、怎么卖
                    String itemName = font.plainSubstrByWidth(item.getHoverName().getString(), W - PAD * 2 - 60);
                    String sellLine = Component.translatable("gui.marketbox.market.preview_sell",
                            MoneyUtils.formatValue(unitPrice),
                            String.valueOf(quantity),
                            itemName).getString();
                    guiGraphics.drawString(font, sellLine, left + PAD, top + 158, 0xFFFF55, false);

                    // 再说谁付多少、自己实收多少
                    double feeAmount = total * feePercent / 100.0;
                    String moneyLine = feePercent > 0
                            ? Component.translatable("gui.marketbox.market.preview_money_fee",
                                    MoneyUtils.formatValue(total),
                                    MoneyUtils.formatValue(total - feeAmount),
                                    MoneyUtils.formatValue(feeAmount)).getString()
                            : Component.translatable("gui.marketbox.market.preview_money",
                                    MoneyUtils.formatValue(total)).getString();
                    guiGraphics.drawString(font, moneyLine, left + PAD, top + 172, 0x66AA66, false);
                }
            }
        } catch (NumberFormatException ignored) {
        }

        // 时长：由服主统一设定，这里只做说明；跟着限购行一起上移，矮窗口不会重叠
        guiGraphics.drawString(font, expiryLabel(), left + PAD, limitBox.getY() - 20, 0xAAAAAA, false);

        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.limit_label_short"),
                left + PAD, limitBox.getY() + 4, 0xAAAAAA, false);
        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.no_limit_hint"),
                left + PAD + 108, limitBox.getY() + 4, 0x777777, false);

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
