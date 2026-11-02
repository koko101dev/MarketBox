package com.koko.marketbox.client;

import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * 富豪榜页：按总资产排序的玩家列表，含头像、名次与金额。
 * 从银行主界面拆出，避免主界面右栏空间不足。
 */
public class RankingScreen extends Screen {

    private static final int PADDING = 12;
    private static final int ROW_HEIGHT = 28;
    private static final int AVATAR_SIZE = 16;
    /** 前三名配色：金 / 银 / 铜 */
    private static final int[] MEDAL_COLORS = {0xFFD700, 0xC8C8C8, 0xCD7F32};

    private final Screen parent;
    private int contentLeft;
    private int contentWidth;
    private int contentHeight;
    private int scrollOffset = 0;

    public RankingScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.nav.ranking"));
        this.parent = parent;
    }

    /** 0=总资产 1=存款 2=收入 3=支出 */
    private int sortMode = 0;
    private static final String[] SORT_KEYS = {"total", "deposit", "income", "expense"};
    private final java.util.List<StyledButton> sortButtons = new java.util.ArrayList<>();

    /** 按当前维度取用于排序与展示的数值 */
    private double valueOf(PlayerEntry entry) {
        return switch (sortMode) {
            case 1 -> entry.deposits();
            case 2 -> entry.income();
            case 3 -> entry.expense();
            default -> entry.balance();
        };
    }

    @Override
    protected void init() {
        super.init();
        // 订阅排行榜更新：服务端只在有人查看时推送，避免全服广播
        ClientNetwork.send(new com.koko.marketbox.networking.packet.RequestLeaderboardPacket(true));
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;
        this.contentHeight = this.height;
        Sidebar.buttons(this, Sidebar.Page.RANKING).forEach(this::addRenderableWidget);
        this.addRenderableWidget(Sidebar.exitButton(this));

        // 分类切换：总资产 / 存款 / 收入 / 支出
        sortButtons.clear();
        // 宽度随可用空间收缩，避免窗口较窄时四个按钮排到容器外
        int tabWidth = Math.max(52, Math.min(68, (contentWidth - 16) / 4));
        int tabHeight = 16;
        int tabY = 44;
        int tabGap = 4;
        int tabTotal = tabWidth * 4 + tabGap * 3;
        int tabStartX = contentLeft + (contentWidth - tabTotal) / 2;
        for (int i = 0; i < 4; i++) {
            final int mode = i;
            StyledButton btn = new StyledButton(tabStartX + i * (tabWidth + tabGap), tabY,
                    tabWidth, tabHeight,
                    Component.translatable("gui.marketbox.rank." + SORT_KEYS[i]),
                    b -> {
                        sortMode = mode;
                        for (int k = 0; k < sortButtons.size(); k++) {
                            sortButtons.get(k).setHighlighted(k == mode);
                        }
                    });
            btn.setHighlighted(i == sortMode);
            sortButtons.add(btn);
            this.addRenderableWidget(btn);
        }
    }

    private int listTop() {
        return 76;
    }

    private int listHeight() {
        return contentHeight - listTop() - PADDING;
    }

    private int maxScroll(int size) {
        return Math.max(0, size * ROW_HEIGHT - listHeight());
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
        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getMarketBadgeCount());

        guiGraphics.fill(contentLeft, 0, this.width, contentHeight, 0xE6101010);

        Minecraft mc = Minecraft.getInstance();
        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.rank.title"), x, 12, 0xFFAA00, false);
        guiGraphics.fill(x, 26, x + w, 27, 0xFF3A3A3A);

        // 表头
        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.rank.rank"), x, 42, 0x888888, false);
        guiGraphics.drawString(mc.font, Component.translatable("gui.marketbox.rank.player"), x + 34, 42, 0x888888, false);
        String wealthHeader = Component.translatable("gui.marketbox.rank.wealth").getString();
        guiGraphics.drawString(mc.font, wealthHeader, x + w - mc.font.width(wealthHeader), 42, 0x888888, false);

        List<PlayerEntry> entries = new java.util.ArrayList<>(ClientData.getLeaderboard());
        if (sortMode != 0) {
            entries.sort((a, b) -> Double.compare(valueOf(b), valueOf(a)));
        }
        int top = listTop();
        int height = listHeight();
        int max = maxScroll(entries.size());
        int offset = Math.min(scrollOffset, max);
        UUID selfId = mc.player != null ? mc.player.getUUID() : null;

        guiGraphics.enableScissor(contentLeft, top, this.width, top + height);
        if (entries.isEmpty()) {
            guiGraphics.drawCenteredString(mc.font, Component.translatable("gui.marketbox.rank.empty"),
                    contentLeft + contentWidth / 2, top + height / 2 - 4, 0x555555);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                int rowY = top + i * ROW_HEIGHT - offset;
                if (rowY + ROW_HEIGHT < top || rowY > top + height) {
                    continue;
                }
                PlayerEntry entry = entries.get(i);

                // 隔行底色；自己所在行额外高亮
                boolean hovered = mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT
                        && mouseX >= contentLeft;
                if (selfId != null && selfId.equals(entry.uuid())) {
                    guiGraphics.fill(contentLeft, rowY, this.width, rowY + ROW_HEIGHT, 0x2A55FF55);
                } else if (hovered) {
                    // 悬停高亮：提示该行可点击发起转账
                    guiGraphics.fill(contentLeft, rowY, this.width, rowY + ROW_HEIGHT, 0x33FFFFFF);
                } else if (i % 2 == 0) {
                    guiGraphics.fill(contentLeft, rowY, this.width, rowY + ROW_HEIGHT, 0x14FFFFFF);
                }

                int rankColor = i < MEDAL_COLORS.length ? MEDAL_COLORS[i] : 0x888888;
                String rankText = String.valueOf(i + 1);
                guiGraphics.drawString(mc.font, rankText, x + 12 - mc.font.width(rankText) / 2,
                        rowY + (ROW_HEIGHT - 8) / 2, rankColor, false);

                // 头像
                PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(entry.uuid()) : null;
                ResourceLocation skin = info != null ? info.getSkin().texture() : ClientData.DEFAULT_SKIN;
                PlayerFaceRenderer.draw(guiGraphics, skin, x + 24, rowY + (ROW_HEIGHT - AVATAR_SIZE) / 2, AVATAR_SIZE);

                // 名字
                String name = mc.font.plainSubstrByWidth(entry.name(), w - 200);
                guiGraphics.drawString(mc.font, name, x + 46, rowY + (ROW_HEIGHT - 8) / 2,
                        i < MEDAL_COLORS.length ? 0xFFFFFF : 0xDDDDDD, false);

                // 金额：按当前排序维度显示
                String wealth = MoneyUtils.formatValue(valueOf(entry));
                guiGraphics.drawString(mc.font, wealth, x + w - mc.font.width(wealth),
                        rowY + (ROW_HEIGHT - 8) / 2, 0x55FF55, false);
            }
        }
        guiGraphics.disableScissor();

        if (max > 0) {
            int barH = Math.max(20, height * height / (entries.size() * ROW_HEIGHT));
            int barY = top + (height - barH) * offset / max;
            guiGraphics.fill(this.width - 5, top, this.width - 2, top + height, 0x33FFFFFF);
            guiGraphics.fill(this.width - 5, barY, this.width - 2, barY + barH, 0xFFAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<PlayerEntry> entries = new java.util.ArrayList<>(ClientData.getLeaderboard());
        if (sortMode != 0) {
            entries.sort((a, b) -> Double.compare(valueOf(b), valueOf(a)));
        }
            int top = listTop();
            int offset = Math.min(scrollOffset, maxScroll(entries.size()));

            for (int i = 0; i < entries.size(); i++) {
                int rowY = top + i * ROW_HEIGHT - offset;
                if (rowY + ROW_HEIGHT < top || rowY > top + listHeight()) {
                    continue;
                }
                if (mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT && mouseX >= contentLeft) {
                    PlayerEntry entry = entries.get(i);
                    // 不能给自己转账
                    if (this.minecraft != null && this.minecraft.player != null
                            && entry.uuid().equals(this.minecraft.player.getUUID())) {
                        return true;
                    }
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new TransferScreen(this.parent, entry));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll(ClientData.getLeaderboard().size());
        if (max > 0 && mouseY >= listTop()) {
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) (scrollY * ROW_HEIGHT)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        // 侧边栏切页走 setScreen，只会触发 removed 而不会触发 onClose：
        // 退订放在这里，否则从排行榜点去别的页面后，服务端会一直给这个玩家推榜单
        ClientNetwork.send(new com.koko.marketbox.networking.packet.RequestLeaderboardPacket(false));
        super.removed();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            // 侧边栏各页共用一个窗口，ESC 直接关闭到游戏，而不是退回上一个页面
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
