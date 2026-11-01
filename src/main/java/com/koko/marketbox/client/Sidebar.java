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
        BANK, TRANSFER, MARKET, STATS, RANKING, ECONOMY, SETTINGS
    }

    private Sidebar() {
    }

    /** 侧边栏宽度：按屏幕宽度取十分之一，并夹在 72~110 之间 */
    static int width(int screenWidth) {
        // 下限降到 56：GUI 缩放较大时（逻辑宽度约 320）仍需给内容区留出足够空间
        return Math.max(56, Math.min(110, screenWidth / 10));
    }

    /** 生成导航按钮；由各界面自行 addRenderableWidget（该方法为 protected） */
    static java.util.List<StyledButton> buttons(Screen host, Page current) {
        int navWidth = width(host.width);
        int btnWidth = navWidth - 12;
        int x = 6;
        int y = 52;
        int step = 22;

        return java.util.List.of(
                nav(host, x, y, btnWidth, "bank", current == Page.BANK,
                        () -> open(host, new MarketBoxScreen())),
                nav(host, x, y + step, btnWidth, "transfer", current == Page.TRANSFER,
                        () -> open(host, new TransferScreen(host))),
                nav(host, x, y + step * 2, btnWidth, "market", current == Page.MARKET,
                        () -> open(host, new MarketScreen(host))),
                nav(host, x, y + step * 3, btnWidth, "stats", current == Page.STATS,
                        () -> open(host, new StatsScreen(host))),
                nav(host, x, y + step * 4, btnWidth, "ranking", current == Page.RANKING,
                        () -> open(host, new RankingScreen(host))),
                nav(host, x, y + step * 5, btnWidth, "economy", current == Page.ECONOMY,
                        () -> open(host, new EconomyScreen(host))),
                nav(host, x, y + step * 6, btnWidth, "settings", current == Page.SETTINGS,
                        () -> open(host, new ConfigScreen(host)))
        );
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
    static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight, int bankBadge) {
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

        // 银行入口角标：有待领取物品/收益时提示数量
        if (bankBadge > 0) {
            String badge = bankBadge > 9 ? "9+" : String.valueOf(bankBadge);
            int btnWidth = navWidth - 12;
            int textX = 6 + btnWidth - Minecraft.getInstance().font.width(badge) - 3;
            guiGraphics.drawString(Minecraft.getInstance().font, badge, textX, 56, 0xFFFF5555, true);
        }
    }
}
