package com.koko.marketbox.client;

import com.koko.marketbox.networking.packet.UpdateBlacklistPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 上架黑名单管理，仅管理员可见。
 *
 * 上半部分是搜索框：输入文字即时过滤物品（支持单字、物品 ID 片段），
 * 点搜索结果即加入名单；清空搜索后下半部分显示当前名单，点右侧移除。
 */
public class BlacklistScreen extends Screen implements FeedbackReceiver {

    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 24;
    private static final int SEARCH_MAX_RESULTS = 500;

    /** 搜索索引：物品注册表在启动后固定，只需构建一次 */
    private record IndexEntry(Item item, String id, String name) {
    }

    private static List<IndexEntry> INDEX;

    private static List<IndexEntry> index() {
        if (INDEX == null) {
            List<IndexEntry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                String name = new ItemStack(item).getHoverName().getString();
                list.add(new IndexEntry(item, id.toLowerCase(), name.toLowerCase()));
            }
            INDEX = list;
        }
        return INDEX;
    }

    /** 相关性打分：越小越靠前，完全匹配优先，其次是名称前缀、名称包含、ID 包含 */
    private static int relevance(IndexEntry entry, String query) {
        if (entry.name.equals(query)) {
            return 0;
        }
        if (entry.name.startsWith(query)) {
            return 1;
        }
        if (entry.name.contains(query)) {
            return 2;
        }
        return 3;
    }

    /** 物品所属命名空间，用于标出模组来源 */
    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon > 0 ? id.substring(0, colon) : "minecraft";
    }

    private final Toast toast = new Toast();

    private int contentLeft;
    private int contentWidth;
    private int resultsTop;
    private int resultsHeight;

    private EditBox searchBox;
    /** 每页条数：按可视高度计算，最多 20 条 */
    private static final int MAX_PAGE_SIZE = 20;
    /** 当前页，从 0 开始 */
    private int pageIndex = 0;
    /** 页内滚动偏移（像素）：一页 20 条可能超出一屏，需要在页内滚动 */
    private float scrollOffset = 0;
    private final List<Item> results = new ArrayList<>();
    /** 上次的搜索词，避免每帧重算 */
    private String lastQuery = "\u0000";

    public BlacklistScreen() {
        super(Component.translatable("gui.marketbox.blacklist.title"));
    }

    @Override
    protected void init() {
        super.init();
        int navWidth = Sidebar.width(this.width);
        this.contentLeft = navWidth;
        this.contentWidth = this.width - navWidth;
        this.resultsTop = 88;
        this.resultsHeight = this.height - resultsTop - PADDING - 26;

        Sidebar.buttons(this, Sidebar.Page.BLACKLIST).forEach(this::addRenderableWidget);

        this.searchBox = new EditBox(this.font, contentLeft + PADDING, 44,
                Math.min(320, contentWidth - PADDING * 2 - 100), 18,
                Component.translatable("gui.marketbox.blacklist.search"));
        this.searchBox.setMaxLength(40);
        this.searchBox.setHint(Component.translatable("gui.marketbox.blacklist.search"));
        this.searchBox.setResponder(text -> {
            if (!text.equals(lastQuery)) {
                lastQuery = text;
                pageIndex = 0;
                refreshResults();
            }
        });
        this.addRenderableWidget(this.searchBox);

        refreshResults();
    }

    /** 按搜索词重建结果列表；空查询时清空，界面转为显示当前名单 */
    private void refreshResults() {
        results.clear();
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase();
        if (query.isEmpty()) {
            return;
        }

        results.clear();
        index().stream()
                .filter(e -> e.id.contains(query) || e.name.contains(query))
                .sorted((a, b) -> Integer.compare(relevance(a, query), relevance(b, query)))
                .limit(SEARCH_MAX_RESULTS)
                .forEach(e -> results.add(e.item()));
    }

    /** 当前列表总条数 */
    private int totalSize() {
        return searching() ? results.size() : ClientData.getBlacklist().size();
    }

    /** 每页固定 20 条；显示不下时靠页内滚动查看 */
    private int pageSize() {
        return MAX_PAGE_SIZE;
    }

    /** 当前页内容的可滚动距离 */
    private int maxScroll() {
        int rows = Math.min(pageSize(), Math.max(0, totalSize() - pageStart()));
        return Math.max(0, rows * ROW_HEIGHT - resultsHeight);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        if (max > 0) {
            scrollOffset = Math.max(0, Math.min(max, scrollOffset - (float) (scrollY * ROW_HEIGHT)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int totalPages() {
        int size = totalSize();
        int per = pageSize();
        return Math.max(1, (size + per - 1) / per);
    }

    private int pageStart() {
        return pageIndex * pageSize();
    }

    private boolean searching() {
        return searchBox != null && !searchBox.getValue().trim().isEmpty();
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
                contentLeft + PADDING, 28, 0x777777, false);

        // 分隔线放在搜索框下方，避免与输入框边框重叠
        guiGraphics.fill(contentLeft + PADDING, 70, this.width - PADDING, 71, 0xFF3A3A3A);

        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        guiGraphics.enableScissor(contentLeft, resultsTop - 16, this.width, resultsTop + resultsHeight);
        if (searching()) {
            renderSearchResults(guiGraphics, x, w, mouseX, mouseY);
        } else {
            renderBlacklist(guiGraphics, x, w, mouseX, mouseY);
        }
        guiGraphics.disableScissor();

        // 页内滚动条：一页 20 条超出可视高度时出现
        int max = maxScroll();
        if (max > 0) {
            int barX = this.width - PADDING - 4;
            int barH = Math.max(20, resultsHeight * resultsHeight / (resultsHeight + max));
            int barY = resultsTop + (int) ((resultsHeight - barH) * (scrollOffset / max));
            guiGraphics.fill(barX, resultsTop, barX + 4, resultsTop + resultsHeight, 0x33FFFFFF);
            guiGraphics.fill(barX, barY, barX + 4, barY + barH, 0xFFCCCCCC);
        }

        renderPager(guiGraphics);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        toast.render(guiGraphics, this.width, this.height);
    }

    private void renderSearchResults(GuiGraphics guiGraphics, int x, int w, int mouseX, int mouseY) {
        var font = this.font;
        if (results.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.marketbox.blacklist.no_match"),
                    contentLeft + contentWidth / 2, resultsTop + 20, 0x666666);
            return;
        }

        guiGraphics.drawString(font,
                Component.translatable("gui.marketbox.blacklist.result_count", results.size()),
                x, resultsTop - 14, 0x888888, false);

        int start = pageStart();
        int last = Math.min(results.size(), start + pageSize());
        int visibleFirst = start + (int) (scrollOffset / ROW_HEIGHT);
        for (int i = start; i < last; i++) {
            int rowY = resultsTop + (i - start) * ROW_HEIGHT - (int) scrollOffset;
            // 只渲染可见范围，滚到页外的一律跳过
            if (i < visibleFirst - 1 || rowY + ROW_HEIGHT < resultsTop || rowY > resultsTop + resultsHeight) {
                continue;
            }
            Item item = results.get(i);
            ItemStack stack = new ItemStack(item);
            String id = BuiltInRegistries.ITEM.getKey(item).toString();

            boolean hovered = mouseX >= x && mouseX <= x + w
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            if (hovered) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x33FFFFFF);
            } else if (i % 2 == 0) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x12FFFFFF);
            }

            guiGraphics.renderItem(stack, x + 6, rowY + 3);
            guiGraphics.drawString(font, stack.getHoverName().getString(), x + 28, rowY + 3, 0xEEEEEE, false);

            // 非原版物品标出来源模组，便于区分同名的模组物品
            String ns = namespace(id);
            if (!"minecraft".equals(ns)) {
                String tag = "[" + ns + "]";
                guiGraphics.drawString(font, tag, x + 28, rowY + 13, 0x7A9CC6, false);
                guiGraphics.drawString(font, id, x + 34 + font.width(tag), rowY + 13, 0x666666, false);
            } else {
                guiGraphics.drawString(font, id, x + 28, rowY + 13, 0x666666, false);
            }

            boolean blocked = ClientData.getBlacklist().contains(id);
            String action = blocked
                    ? Component.translatable("gui.marketbox.blacklist.tag_blocked").getString()
                    : Component.translatable("gui.marketbox.blacklist.tag_add").getString();
            guiGraphics.drawString(font, action,
                    x + w - font.width(action) - 8, rowY + 8,
                    blocked ? 0xFF7777 : 0x77FF77, false);
        }
    }

    private void renderBlacklist(GuiGraphics guiGraphics, int x, int w, int mouseX, int mouseY) {
        var font = this.font;
        List<String> ids = ClientData.getBlacklist();

        if (ids.isEmpty()) {
            guiGraphics.drawCenteredString(font,
                    Component.translatable("gui.marketbox.blacklist.empty"),
                    contentLeft + contentWidth / 2, resultsTop + 20, 0x666666);
            return;
        }

        guiGraphics.drawString(font,
                Component.translatable("gui.marketbox.blacklist.count", ids.size()),
                x, resultsTop - 14, 0x888888, false);

        int start = pageStart();
        int last = Math.min(ids.size(), start + pageSize());
        int visibleFirst = start + (int) (scrollOffset / ROW_HEIGHT);
        for (int i = start; i < last; i++) {
            int rowY = resultsTop + (i - start) * ROW_HEIGHT - (int) scrollOffset;
            if (i < visibleFirst - 1 || rowY + ROW_HEIGHT < resultsTop || rowY > resultsTop + resultsHeight) {
                continue;
            }
            String id = ids.get(i);

            boolean hoverRemove = mouseX >= x + w - 60 && mouseX <= x + w
                    && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            if (hoverRemove) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x44FF5555);
            } else if (i % 2 == 0) {
                guiGraphics.fill(x, rowY, x + w, rowY + ROW_HEIGHT - 2, 0x12FFFFFF);
            }

            ResourceLocation key = ResourceLocation.tryParse(id);
            Item item = key == null ? null : BuiltInRegistries.ITEM.get(key);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item);
                guiGraphics.renderItem(stack, x + 6, rowY + 3);
                guiGraphics.drawString(font, stack.getHoverName().getString(), x + 28, rowY + 3, 0xEEEEEE, false);
                guiGraphics.drawString(font, id, x + 28, rowY + 13, 0x666666, false);
            } else {
                // 物品已不存在（模组被卸载，或 ID 手写错）：只把 ID 标红，避免整页渲染抛异常
                guiGraphics.drawString(font, id, x + 28, rowY + 6, 0xFF7777, false);
            }

            String remove = Component.translatable("gui.marketbox.blacklist.tag_remove").getString();
            guiGraphics.drawString(font, remove, x + w - font.width(remove) - 8, rowY + 8, 0xFF7777, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handlePagerClick(mouseX, mouseY)) {
            return true;
        }

        int x = contentLeft + PADDING;
        int w = contentWidth - PADDING * 2;

        if (button == 0 && mouseX >= x && mouseX <= x + w && mouseY >= resultsTop) {
            // row 是页内行号，index 是全局索引：用全局索引和「一页能显示几行」比较，
            // 会让第 2 页起的每一行都判为越界，整页点不动
            int row = (int) ((mouseY - resultsTop + scrollOffset) / ROW_HEIGHT);
            int maxRows = Math.max(1, resultsHeight / ROW_HEIGHT);
            if (row >= 0 && row < maxRows) {
                int index = pageStart() + row;
                if (searching()) {
                    if (index >= 0 && index < results.size()) {
                        addItem(results.get(index));
                    }
                } else {
                    List<String> ids = ClientData.getBlacklist();
                    // 只在右侧区域响应移除，避免误触
                    if (index >= 0 && index < ids.size() && mouseX >= x + w - 60) {
                        removeItem(ids.get(index));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addItem(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (ClientData.getBlacklist().contains(id)) {
            toast.show(Component.translatable("gui.marketbox.blacklist.already"), true);
            return;
        }
        ClientNetwork.send(new UpdateBlacklistPacket(id, true));
        toast.show(Component.translatable("gui.marketbox.blacklist.added",
                new ItemStack(item).getHoverName()), false);
    }

    private void removeItem(String id) {
        ClientNetwork.send(new UpdateBlacklistPacket(id, false));
        toast.show(Component.translatable("gui.marketbox.blacklist.removed"), false);
    }

    /** 底部翻页控件：上一页 / 页码 / 下一页 */
    private void renderPager(GuiGraphics guiGraphics) {
        int pages = totalPages();
        if (pages <= 1) {
            return;
        }

        var font = this.font;
        int y = this.height - PADDING - 20;
        int leftX = contentLeft + PADDING;
        int rightX = contentLeft + PADDING + 84;

        boolean canPrev = pageIndex > 0;
        boolean canNext = pageIndex < pages - 1;

        drawPagerButton(guiGraphics, leftX, y, 76, Component.translatable("gui.marketbox.blacklist.prev"), canPrev);
        drawPagerButton(guiGraphics, rightX, y, 76, Component.translatable("gui.marketbox.blacklist.next"), canNext);

        String label = Component.translatable("gui.marketbox.blacklist.page",
                pageIndex + 1, pages).getString();
        guiGraphics.drawString(font, label, rightX + 84, y + 6, 0xAAAAAA, false);
    }

    private void drawPagerButton(GuiGraphics guiGraphics, int x, int y, int w, Component label, boolean enabled) {
        guiGraphics.fill(x, y, x + w, y + 20, enabled ? 0xFF2A2A2A : 0xFF1A1A1A);
        guiGraphics.renderOutline(x, y, w, 20, enabled ? 0xFF666666 : 0xFF333333);
        guiGraphics.drawCenteredString(this.font, label, x + w / 2, y + 6,
                enabled ? 0xDDDDDD : 0x555555);
    }

    /** 翻页按钮的点击区域：返回 true 表示已处理 */
    private boolean handlePagerClick(double mouseX, double mouseY) {
        int pages = totalPages();
        if (pages <= 1) {
            return false;
        }
        int y = this.height - PADDING - 20;
        int leftX = contentLeft + PADDING;
        int rightX = contentLeft + PADDING + 84;

        if (mouseY < y || mouseY > y + 20) {
            return false;
        }
        if (mouseX >= leftX && mouseX <= leftX + 76 && pageIndex > 0) {
            pageIndex--;
            scrollOffset = 0;
            return true;
        }
        if (mouseX >= rightX && mouseX <= rightX + 76 && pageIndex < pages - 1) {
            pageIndex++;
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    @Override
    public void showFeedback(Component message, boolean isError) {
        toast.show(message, isError);
    }

    @Override
    public void onClose() {
        // 与侧边栏其他页一致：ESC 直接关闭，不沿历次切页的来路回退
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
