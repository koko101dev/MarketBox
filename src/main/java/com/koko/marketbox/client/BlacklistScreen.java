package com.koko.marketbox.client;

import com.koko.marketbox.networking.packet.UpdateBlacklistPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 上架黑名单管理，仅管理员可见。
 *
 * 黑名单里的物品无法上架市场，用于屏蔽刷物品机产出的东西
 * （刷怪笼、附魔金苹果之类）。点「添加手持物品」把当前主手物品加入名单。
 */
public class BlacklistScreen extends Screen implements FeedbackReceiver {

    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 30;

    private final Screen parent;
    private final Toast toast = new Toast();

    private int contentLeft;
    private int contentWidth;
    private int listTop;

    public BlacklistScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.blacklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;
        this.listTop = 56;

        Sidebar.buttons(this, Sidebar.Page.BLACKLIST).forEach(this::addRenderableWidget);

        int bottomY = this.height - PADDING - 20;
        this.addRenderableWidget(new StyledButton(contentLeft + PADDING, bottomY, 140, 20,
                Component.translatable("gui.marketbox.blacklist.add_held"), b -> addHeldItem()));
        this.addRenderableWidget(new StyledButton(this.width - PADDING - 70, bottomY, 70, 20,
                Component.translatable("gui.marketbox.blacklist.back"), b -> this.onClose()));
    }

    /** 把主手物品加入黑名单 */
    private void addHeldItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            toast.show(Component.translatable("gui.marketbox.blacklist.empty_hand"), true);
            return;
        }
        String id = UpdateBlacklistPacket.itemId(held);
        if (ClientData.getBlacklist().contains(id)) {
            toast.show(Component.translatable("gui.marketbox.blacklist.already"), true);
            return;
        }
        ClientNetwork.send(new UpdateBlacklistPacket(id, true));
        toast.show(Component.translatable("gui.marketbox.blacklist.added", held.getHoverName()), false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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
                Component.translatable("gui.marketbox.blacklist.hint"),
                contentLeft + PADDING, 26, 0x777777, false);
        guiGraphics.fill(contentLeft + PADDING, 42, this.width - PADDING, 43, 0xFF3A3A3A);

        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;
        List<String> ids = ClientData.getBlacklist();

        if (ids.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.marketbox.blacklist.empty"),
                    contentLeft + contentWidth / 2, listTop + 30, 0x666666);
        }

        int maxRows = Math.max(1, (this.height - listTop - PADDING - 28) / ROW_HEIGHT);
        for (int i = 0; i < Math.min(ids.size(), maxRows); i++) {
            String id = ids.get(i);
            int rowY = listTop + i * ROW_HEIGHT;

            if (i % 2 == 0) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x12FFFFFF);
            }

            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            ItemStack stack = new ItemStack(item);
            guiGraphics.renderItem(stack, x + 6, rowY + 7);

            String name = stack.getHoverName().getString();
            guiGraphics.drawString(font, name, x + 28, rowY + 4, 0xEEEEEE, false);
            guiGraphics.drawString(font, id, x + 28, rowY + 15, 0x666666, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        toast.render(guiGraphics, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点在某一行的右侧区域 = 移除该物品
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;
        List<String> ids = ClientData.getBlacklist();

        if (button == 0 && mouseX >= x && mouseX <= x + w && mouseY >= listTop) {
            int index = (int) ((mouseY - listTop) / ROW_HEIGHT);
            if (index >= 0 && index < Math.min(ids.size(), 20) && mouseX >= x + w - 60) {
                String id = ids.get(index);
                ClientNetwork.send(new UpdateBlacklistPacket(id, false));
                toast.show(Component.translatable("gui.marketbox.blacklist.removed"), false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void showFeedback(Component message, boolean isError) {
        toast.show(message, isError);
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
