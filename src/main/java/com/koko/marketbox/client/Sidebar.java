package com.koko.marketbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 三个界面共用的左侧导航栏。
 * 只负责绘制与创建导航按钮，具体界面由点击回调跳转，避免各界面重复实现。
 */
final class Sidebar {

    /** 当前所在页面，用于高亮 */
    enum Page {
        BANK, TRANSFER, MARKET, STATS, RANKING, ECONOMY, SETTINGS, BLACKLIST
    }

    private Sidebar() {
    }

    /** 侧边栏宽度：按屏幕宽度取十分之一，并夹在 72~110 之间 */
    static int width(int screenWidth) {
        // 下限降到 56：GUI 缩放较大时（逻辑宽度约 320）仍需给内容区留出足够空间
        return Math.max(56, Math.min(110, screenWidth / 10));
    }

    /** 导航按钮的基准行距与起始高度 */
    private static final int STEP = 22;
    private static final int NAV_TOP = 52;
    /** 底部给退出按钮预留的空间 */
    private static final int BOTTOM_RESERVE = 34;

    /**
     * 导航行距。窗口过矮时压缩，保证七（八）项排完之后，
     * 底部还放得下退出按钮，不压住最后一项。
     */
    static int stepFor(int screenHeight, boolean admin) {
        int count = admin ? 8 : 7;
        int available = screenHeight - NAV_TOP - BOTTOM_RESERVE;
        if (count * STEP <= available) {
            return STEP;
        }
        return Math.max(18, available / count);
    }

    /**
     * 侧边栏底部的退出按钮。
     *
     * 只由一级页面（侧边栏那七项 + 黑名单）调用。定期存款、批量上架这类二级页面不要加：
     * 它们的关闭本来就是「返回上一级」，再多一个退出入口容易误关。
     */
    static StyledButton exitButton(Screen host) {
        int navWidth = width(host.width);
        int buttonHeight = 20;
        int y = host.height - buttonHeight - 6;
        StyledButton button = new StyledButton(6, y, navWidth - 12, buttonHeight,
                Component.translatable("gui.marketbox.nav.exit"),
                b -> Minecraft.getInstance().setScreen(null));

        int count = ClientData.isAdmin() ? 8 : 7;
        int lastBottom = NAV_TOP + stepFor(host.height, ClientData.isAdmin()) * (count - 1) + buttonHeight;
        // 极端矮窗口下导航本身已经排到底部：这时不画，避免按钮压在导航项上
        if (y < lastBottom + 2) {
            button.visible = false;
        }
        return button;
    }

    /** 生成导航按钮；由各界面自行 addRenderableWidget（该方法为 protected） */
    static java.util.List<StyledButton> buttons(Screen host, Page current) {
        int navWidth = width(host.width);
        int btnWidth = navWidth - 12;
        int x = 6;
        int y = NAV_TOP;
        int step = stepFor(host.height, ClientData.isAdmin());

        java.util.List<StyledButton> list = new java.util.ArrayList<>();
        list.add(nav(host, x, y, btnWidth, "bank", current == Page.BANK,
                () -> open(host, new BankScreen())));
        list.add(nav(host, x, y + step, btnWidth, "transfer", current == Page.TRANSFER,
                () -> open(host, new TransferScreen(host))));
        list.add(nav(host, x, y + step * 2, btnWidth, "market", current == Page.MARKET,
                () -> open(host, new MarketScreen(host))));
        list.add(nav(host, x, y + step * 3, btnWidth, "stats", current == Page.STATS,
                () -> open(host, new StatsScreen(host))));
        list.add(nav(host, x, y + step * 4, btnWidth, "ranking", current == Page.RANKING,
                () -> open(host, new RankingScreen(host))));
        list.add(nav(host, x, y + step * 5, btnWidth, "economy", current == Page.ECONOMY,
                () -> open(host, new EconomyScreen(host))));
        list.add(nav(host, x, y + step * 6, btnWidth, "settings", current == Page.SETTINGS,
                () -> open(host, new ConfigScreen(host))));

        // 黑名单只有管理员可见，普通玩家看不到这一项
        if (ClientData.isAdmin()) {
            list.add(nav(host, x, y + step * 7, btnWidth, "blacklist", current == Page.BLACKLIST,
                    () -> open(host, new BlacklistScreen())));
        }
        return list;
    }

    private static StyledButton nav(Screen host, int x, int y, int width, String key,
                                    boolean highlighted, Runnable action) {
        return new StyledButton(x, y, width, 20,
                Component.translatable("gui.marketbox.nav." + key),
                b -> action.run(), false, highlighted);
    }

    /** 切换界面；当前已是目标界面时忽略，避免无意义重建 */
    private static void open(Screen host, Screen target) {
        if (host.getClass() == target.getClass()) {
            return;
        }
        Minecraft.getInstance().setScreen(target);
    }

    /** 绘制侧边栏背景与标题 */
    static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int marketBadge) {
        int navWidth = width(screenWidth);

        // 底板与右侧分隔线
        guiGraphics.fill(0, 0, navWidth, screenHeight, 0xFF0B0B0B);
        guiGraphics.fill(navWidth - 1, 0, navWidth, screenHeight, 0xFF2A2A2A);

        // 顶部标题
        guiGraphics.drawCenteredString(Minecraft.getInstance().font,
                Component.translatable("gui.marketbox.nav.title"),
                navWidth / 2, 18, 0xFFAA00);

        // 标题下的分隔线
        guiGraphics.fill(8, 34, navWidth - 8, 35, 0xFF3A3A3A);

        // 市场入口角标：退回物品与待领取收益都归属市场
        if (marketBadge > 0) {
            String badge = marketBadge > 9 ? "9+" : String.valueOf(marketBadge);
            int btnWidth = navWidth - 12;
            int textX = 6 + btnWidth - Minecraft.getInstance().font.width(badge) - 3;
            // 市场按钮位于第三个：y = 起始高度 + 行距 * 2
            int badgeY = NAV_TOP + stepFor(screenHeight, ClientData.isAdmin()) * 2 + 4;
            guiGraphics.drawString(Minecraft.getInstance().font, badge, textX, badgeY, 0xFFFF5555, true);
        }
    }
}
