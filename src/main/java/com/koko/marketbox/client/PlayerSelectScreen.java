package com.koko.marketbox.client;

import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 玩家选择器：带搜索框的可滚动玩家列表。
 * 用于设置界面里的管理员操作，避免人多时只能左右逐个切换。
 */
public class PlayerSelectScreen extends Screen {

    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 24;
    private static final int AVATAR_SIZE = 14;

    private final Screen parent;
    private final Consumer<PlayerEntry> onSelect;
    private final Toast toast = new Toast();

    private EditBox searchBox;
    private String query = "";
    private int scrollOffset = 0;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public PlayerSelectScreen(Screen parent, Consumer<PlayerEntry> onSelect) {
        super(Component.translatable("gui.marketbox.player_select.title"));
        this.parent = parent;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        super.init();
        this.panelLeft = (this.width - PANEL_WIDTH) / 2;
        this.panelHeight = Math.min(260, this.height - 60);
        this.panelTop = (this.height - panelHeight) / 2;

        this.searchBox = new EditBox(this.font, panelLeft + 8, panelTop + 22, PANEL_WIDTH - 16, 16,
                Component.translatable("gui.marketbox.player_select.search"));
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.translatable("gui.marketbox.player_select.search"));
        this.searchBox.setResponder(text -> {
            this.query = text == null ? "" : text;
            this.scrollOffset = 0;
        });
        this.searchBox.setValue(query);
        this.addRenderableWidget(this.searchBox);
    }

    private int listTop() {
        return panelTop + 44;
    }

    private int listHeight() {
        return panelHeight - 52;
    }

    /** 按搜索词过滤；为空时返回全部 */
    private List<PlayerEntry> filtered() {
        List<PlayerEntry> all = ClientData.getLeaderboard();
        if (query == null || query.isBlank()) {
            return all;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(e -> e.name() != null && e.name().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }

    private int maxScroll(int size) {
        return Math.max(0, size * ROW_HEIGHT - listHeight());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 居中面板
        guiGraphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + panelHeight, 0xF0101010);
        guiGraphics.renderOutline(panelLeft, panelTop, PANEL_WIDTH, panelHeight, 0xFF555555);
        guiGraphics.drawCenteredString(this.font, this.title, panelLeft + PANEL_WIDTH / 2, panelTop + 6, 0xFFAA00);

        List<PlayerEntry> entries = filtered();
        int top = listTop();
        int height = listHeight();
        int max = maxScroll(entries.size());
        int offset = Math.min(scrollOffset, max);

        guiGraphics.enableScissor(panelLeft + 1, top, panelLeft + PANEL_WIDTH - 1, top + height);
        if (entries.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("gui.marketbox.player_select.empty"),
                    panelLeft + PANEL_WIDTH / 2, top + height / 2 - 4, 0x666666);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                int rowY = top + i * ROW_HEIGHT - offset;
                if (rowY + ROW_HEIGHT < top || rowY > top + height) {
                    continue;
                }
                PlayerEntry entry = entries.get(i);
                boolean hovered = mouseX >= panelLeft && mouseX <= panelLeft + PANEL_WIDTH
                        && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

                if (hovered) {
                    guiGraphics.fill(panelLeft + 1, rowY, panelLeft + PANEL_WIDTH - 1, rowY + ROW_HEIGHT, 0x33FFFFFF);
                } else if (i % 2 == 0) {
                    guiGraphics.fill(panelLeft + 1, rowY, panelLeft + PANEL_WIDTH - 1, rowY + ROW_HEIGHT, 0x10FFFFFF);
                }

                int x = panelLeft + 8;
                PlayerInfo info = this.minecraft != null && this.minecraft.getConnection() != null
                        ? this.minecraft.getConnection().getPlayerInfo(entry.uuid()) : null;
                ResourceLocation skin = info != null ? info.getSkin().texture() : MarketBoxScreen.DEFAULT_SKIN;
                PlayerFaceRenderer.draw(guiGraphics, skin, x, rowY + (ROW_HEIGHT - AVATAR_SIZE) / 2, AVATAR_SIZE);

                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                guiGraphics.drawString(this.font, this.font.plainSubstrByWidth(entry.name(), 150),
                        x + AVATAR_SIZE + 6, textY, 0xDDDDDD, false);

                String wealth = MoneyUtils.formatValue(entry.balance());
                guiGraphics.drawString(this.font, wealth,
                        panelLeft + PANEL_WIDTH - 10 - this.font.width(wealth), textY, 0x55FF55, false);
            }
        }
        guiGraphics.disableScissor();

        if (max > 0) {
            int barH = Math.max(16, height * height / (entries.size() * ROW_HEIGHT));
            int barY = top + (height - barH) * offset / max;
            guiGraphics.fill(panelLeft + PANEL_WIDTH - 5, top, panelLeft + PANEL_WIDTH - 2, top + height, 0x33FFFFFF);
            guiGraphics.fill(panelLeft + PANEL_WIDTH - 5, barY, panelLeft + PANEL_WIDTH - 2, barY + barH, 0xFFAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        toast.render(guiGraphics, this.width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<PlayerEntry> entries = filtered();
            int top = listTop();
            int offset = Math.min(scrollOffset, maxScroll(entries.size()));

            if (mouseX >= panelLeft && mouseX <= panelLeft + PANEL_WIDTH) {
                for (int i = 0; i < entries.size(); i++) {
                    int rowY = top + i * ROW_HEIGHT - offset;
                    if (rowY + ROW_HEIGHT < top || rowY > top + listHeight()) {
                        continue;
                    }
                    if (mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
                        onSelect.accept(entries.get(i));
                        closeToParent();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll(filtered().size());
        if (max > 0 && mouseY >= listTop()) {
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) (scrollY * ROW_HEIGHT)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
