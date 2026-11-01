package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.networking.packet.PurchaseMarketItemPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 购买弹窗：支持部分购买，数量上限取「挂单剩余」与「每人限购」的较小值。
 * 服务端仍会再次裁剪，客户端的数量只是意愿。
 */
public class PurchaseDialogScreen extends Screen {

    private static final int W = 300;
    private static final int H = 200;
    private static final int PAD = 10;

    private final Screen parent;
    private final MarketListing listing;
    private final int available;
    /** 可购买上限：有限购时取剩余与限购的较小值 */
    private final int maxSelectable;

    private int left;
    private int top;
    private int quantity;
    private QuantitySlider slider;

    public PurchaseDialogScreen(Screen parent, MarketListing listing) {
        super(Component.translatable("gui.marketbox.market.buy_dialog_title"));
        this.parent = parent;
        this.listing = listing;
        this.available = Math.max(1, listing.item().getCount());
        int limit = listing.perPlayerLimit();
        this.maxSelectable = limit > 0 ? Math.min(available, limit) : available;
        this.quantity = 1;
    }

    @Override
    protected void init() {
        super.init();
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;

        this.slider = new QuantitySlider(left + PAD, top + 104, W - PAD * 2, 16);
        this.addRenderableWidget(this.slider);

        int step = stepSize();
        int btnY = top + 126;
        int bw = 58;
        int gap = 4;
        this.addRenderableWidget(new StyledButton(left + PAD, btnY, bw, 16,
                Component.literal("-" + step), b -> setQuantity(quantity - step)));
        this.addRenderableWidget(new StyledButton(left + PAD + bw + gap, btnY, bw, 16,
                Component.literal("+" + step), b -> setQuantity(quantity + step)));
        this.addRenderableWidget(new StyledButton(left + PAD + (bw + gap) * 2, btnY, W - PAD * 2 - (bw + gap) * 2, 16,
                Component.translatable("gui.marketbox.market.qty_all"), b -> setQuantity(maxSelectable)));

        int bottomY = top + H - 30;
        this.addRenderableWidget(new StyledButton(left + W / 2 - 96, bottomY, 90, 20,
                Component.translatable("gui.marketbox.market.cancel"), b -> close()));
        this.addRenderableWidget(new StyledButton(left + W / 2 + 6, bottomY, 90, 20,
                Component.translatable("gui.marketbox.market.buy_confirm"), b -> submit()));
    }

    private int stepSize() {
        if (maxSelectable <= 8) {
            return 1;
        }
        if (maxSelectable <= 32) {
            return 8;
        }
        return 16;
    }

    private double unitPrice() {
        return listing.price() / available;
    }

    private void setQuantity(int value) {
        this.quantity = Mth.clamp(value, 1, maxSelectable);
        if (slider != null) {
            slider.syncFromQuantity();
        }
    }

    private void submit() {
        ClientNetwork.send(new PurchaseMarketItemPacket(listing.id(), quantity));
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

        guiGraphics.renderItem(listing.item(), left + PAD, top + 20);
        guiGraphics.drawString(font, listing.item().getHoverName(), left + PAD + 20, top + 24, 0xFFFFFF, false);

        String unit = Component.translatable("gui.marketbox.market.card.price",
                MoneyUtils.formatValue(unitPrice())).getString();
        guiGraphics.drawString(font, unit, left + PAD + 20, top + 36, 0xFFFF55, false);

        String remaining = Component.translatable("gui.marketbox.market.remaining",
                String.valueOf(available)).getString();
        guiGraphics.drawString(font, remaining, left + PAD, top + 54, 0x888888, false);

        if (listing.perPlayerLimit() > 0) {
            String limitText = Component.translatable("gui.marketbox.market.limit_label",
                    String.valueOf(listing.perPlayerLimit())).getString();
            guiGraphics.drawString(font, limitText, left + PAD, top + 66, 0xCC8844, false);
        }

        guiGraphics.drawString(font, Component.translatable("gui.marketbox.market.qty_label"),
                left + PAD, top + 84, 0xAAAAAA, false);
        String qtyText = quantity + " / " + maxSelectable;
        guiGraphics.drawString(font, qtyText, left + W - PAD - font.width(qtyText), top + 84, 0x55FF55, false);

        String total = Component.translatable("gui.marketbox.market.buy_total",
                MoneyUtils.formatValue(unitPrice() * quantity)).getString();
        guiGraphics.drawString(font, total, left + PAD, top + 150, 0xFFCC66, false);

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
            quantity = Mth.clamp(1 + (int) Math.round(value * (maxSelectable - 1)), 1, maxSelectable);
            updateMessage();
        }

        void syncFromQuantity() {
            this.value = maxSelectable > 1 ? (double) (quantity - 1) / (maxSelectable - 1) : 0.0;
            updateMessage();
        }
    }
}
