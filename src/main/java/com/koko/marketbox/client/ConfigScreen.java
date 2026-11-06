package com.koko.marketbox.client;

import com.koko.marketbox.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 模组配置界面。
 * 宽度铺满屏幕，内容列限宽居中；行高随窗口高度自适应，小窗口下不会溢出。
 * 渲染细节见 {@link ConfigScreenRenderer}，脏检测见 {@link ConfigScreenState}。
 */
public class ConfigScreen extends Screen implements FeedbackReceiver {

    static final int PADDING = 12;
    /** 基准行高，实际使用 rowHeight */
    static final int ROW_HEIGHT = 28;
    private static final int SECTION_HEADER_HEIGHT = 28;
    static final int CONTROL_WIDTH = 140;
    private static final int CONTROL_HEIGHT = 20;
    static final int TOGGLE_WIDTH = 60;
    static final int BOTTOM_BAR_HEIGHT = 44;

    int guiWidth;
    int guiHeight;
    int leftPos;
    int topPos;
    /** 内容列（限宽居中），避免超宽屏下标签与控件被拉到两端 */
    int contentLeft;
    int contentWidth;
    /** 当前生效的行高与控件高度，随窗口高度自适应 */
    int rowHeight = ROW_HEIGHT;
    int controlHeight = CONTROL_HEIGHT;
    /** 内容区滚动偏移；窗口放不下全部配置项时可滚动查看 */
    int scrollOffset = 0;
    int maxScroll = 0;

    // 临时值（未保存前只改内存）
    double tempInterestRate;
    int tempInterestIntervalMinutes;
    boolean tempShowHud;
    boolean tempShowMarketPriceTooltip;
    boolean tempDisableMenuBlur;
    Config.HudPosition tempHudPosition;
    Config.HudTextStyle tempHudTextStyle;
    double tempHudScale;
    int tempHudXOffset;
    int tempHudYOffset;
    double tempMarketFee;
    /** 市场限制项（管理员可改） */
    double tempMaxListings;
    double tempMaxPrice;
    double tempMaxTx;
    boolean tempTransferEnabled;
    /** 启用的面额（管理员可勾选），禁用后不再发放该面额 */
    java.util.List<Double> tempEnabledDenominations = new java.util.ArrayList<>();
    java.util.List<Double> savedEnabledDenominations = new java.util.ArrayList<>();
    /** 全部可选面额，从大到小 */
    static final double[] ALL_DENOMINATIONS = {100, 50, 20, 10, 5, 1, 0.1, 0.01};
    double tempQuick1;
    double tempQuick2;
    double tempQuick3;

    // 控件
    final List<EditBox> editBoxes = new ArrayList<>();
    EditBox interestRateBox;
    EditBox interestIntervalBox;
    EditBox hudScaleBox;
    EditBox hudXOffsetBox;
    EditBox hudYOffsetBox;
    EditBox feeBox;
    EditBox maxListingsBox;
    EditBox maxPriceBox;
    EditBox maxTxBox;
    EditBox quick1Box;
    EditBox quick2Box;
    EditBox quick3Box;
    StyledButton saveButton;
    /** 底栏按钮：渲染时单独处理，不参与内容区的裁剪 */
    private final java.util.List<net.minecraft.client.gui.components.AbstractWidget> bottomBarWidgets =
            new java.util.ArrayList<>();
    /** 是否处于按键监听状态（点击按键按钮后，等待下一次按键） */
    boolean listeningForKey = false;

    // 状态提示

    // 已保存的基线值，用于脏检测
    double savedInterestRate;
    int savedInterestIntervalMinutes;
    boolean savedShowHud;
    boolean savedShowMarketPriceTooltip;
    boolean savedDisableMenuBlur;
    Config.HudPosition savedHudPosition;
    Config.HudTextStyle savedHudTextStyle;
    double savedHudScale;
    int savedHudXOffset;
    int savedHudYOffset;
    double savedMarketFee;
    double savedMaxListings;
    double savedMaxPrice;
    double savedMaxTx;
    boolean savedTransferEnabled;
    double savedQuick1;
    double savedQuick2;
    double savedQuick3;

    /** 返回时回到的上一级界面（从 Mod Menu 进入时为模组列表，热键进入时为 null） */
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("gui.marketbox.config.title"));
        this.parent = parent;
        loadCurrentValues();
    }

    private void loadCurrentValues() {
        tempInterestRate = Config.getInterestRate();
        tempInterestIntervalMinutes = Config.getInterestIntervalMinutes();
        tempShowHud = Config.isShowHud();
        tempShowMarketPriceTooltip = Config.isShowMarketPriceTooltip();
        tempDisableMenuBlur = Config.isDisableMenuBlur();
        tempHudPosition = Config.getHudPosition();
        tempHudTextStyle = Config.getHudTextStyle();
        tempHudScale = Config.getHudScale();
        tempHudXOffset = Config.getHudXOffset();
        tempHudYOffset = Config.getHudYOffset();
        tempMarketFee = ClientData.getMarketFeePercent();
        tempMaxListings = ClientData.getMaxListings();
        tempMaxPrice = ClientData.getMaxListingPrice();
        tempMaxTx = ClientData.getMaxTransactionAmount();
        tempTransferEnabled = ClientData.isTransferEnabled();
        tempEnabledDenominations = new java.util.ArrayList<>(ClientData.getEnabledDenominations());
        tempQuick1 = Config.getQuickAmount(1);
        tempQuick2 = Config.getQuickAmount(2);
        tempQuick3 = Config.getQuickAmount(3);

        savedInterestRate = tempInterestRate;
        savedInterestIntervalMinutes = tempInterestIntervalMinutes;
        savedShowHud = tempShowHud;
        savedShowMarketPriceTooltip = tempShowMarketPriceTooltip;
        savedDisableMenuBlur = tempDisableMenuBlur;
        savedHudPosition = tempHudPosition;
        savedHudTextStyle = tempHudTextStyle;
        savedHudScale = tempHudScale;
        savedHudXOffset = tempHudXOffset;
        savedHudYOffset = tempHudYOffset;
        savedMarketFee = tempMarketFee;
        savedMaxListings = tempMaxListings;
        savedMaxPrice = tempMaxPrice;
        savedMaxTx = tempMaxTx;
        savedTransferEnabled = tempTransferEnabled;
        savedEnabledDenominations = new java.util.ArrayList<>(tempEnabledDenominations);
        savedQuick1 = tempQuick1;
        savedQuick2 = tempQuick2;
        savedQuick3 = tempQuick3;
    }

    @Override
    protected void init() {
        super.init();

        int navWidth = Sidebar.width(this.width);
        this.guiWidth = this.width - navWidth;
        this.leftPos = navWidth;

        Sidebar.buttons(this, Sidebar.Page.SETTINGS).forEach(this::addRenderableWidget);
        StyledButton exitButton = Sidebar.exitButton(this);
        this.addRenderableWidget(exitButton);
        this.contentWidth = Math.min(this.guiWidth, 620);
        this.contentLeft = this.leftPos + (this.guiWidth - this.contentWidth) / 2;

        // 金钱相关项仅管理员可见：利息 2 行 + 手续费 1 行为管理区块，其余所有人可见
        // 两档权限：等级 2 起可用管理员操作，等级 4（服主）才能改全服经济参数
        boolean owner = ClientData.isOwner();
        int contentRows = 9 + 3;
        int sectionCount = 2;
        if (owner) {
            contentRows += 2 + 5 + ALL_DENOMINATIONS.length;   // 利息 2 行 + 市场 5 行 + 面额 8 行
            sectionCount += 3;
        }
        if (owner) {
            contentRows += AdminPanel.rowCount();
            sectionCount += 1;
        }

        this.guiHeight = this.height;
        this.topPos = 0;
        this.rowHeight = ROW_HEIGHT;
        this.controlHeight = CONTROL_HEIGHT;

        int contentHeight = PADDING * 2 + sectionCount * SECTION_HEADER_HEIGHT + contentRows * rowHeight;
        int visibleHeight = this.guiHeight - BOTTOM_BAR_HEIGHT;
        // 多留 10px：滚到底时最后一行不会紧贴按钮栏
        this.maxScroll = Math.max(0, contentHeight - visibleHeight + 10);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScroll));

        editBoxes.clear();
        // 内容起点包含滚动偏移，滚动时通过 rebuildWidgets 重排控件
        int y = topPos + PADDING - scrollOffset;

        // === 利息设置（仅服主）===
        if (owner) {
            y += SECTION_HEADER_HEIGHT;
            interestRateBox = ConfigScreenWidgets.createEditBox(this, y, String.format("%.2f", tempInterestRate),
                    s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
            y += rowHeight;
            interestIntervalBox = ConfigScreenWidgets.createEditBox(this, y, String.valueOf(tempInterestIntervalMinutes),
                    s -> s.isEmpty() || s.matches("\\d+"));
            y += rowHeight;
        }

        // === HUD 设置 ===
        y += SECTION_HEADER_HEIGHT;
        addToggleButtons(y, "showHud", tempShowHud);
        y += rowHeight;
        addToggleButtons(y, "showMarketPriceTooltip", tempShowMarketPriceTooltip);
        y += rowHeight;
        addCycleButton(y, "hudPosition", tempHudPosition.name(), Config.HudPosition.values());
        y += rowHeight;
        addCycleButton(y, "hudTextStyle", tempHudTextStyle.name(), Config.HudTextStyle.values());
        y += rowHeight;
        hudScaleBox = ConfigScreenWidgets.createEditBox(this, y, String.format("%.1f", tempHudScale),
                s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,1})?"));
        y += rowHeight;
        hudXOffsetBox = ConfigScreenWidgets.createEditBox(this, y, String.valueOf(tempHudXOffset),
                s -> s.isEmpty() || s.matches("-?\\d+"));
        y += rowHeight;
        hudYOffsetBox = ConfigScreenWidgets.createEditBox(this, y, String.valueOf(tempHudYOffset),
                s -> s.isEmpty() || s.matches("-?\\d+"));
        y += rowHeight;
        addToggleButtons(y, "disableMenuBlur", tempDisableMenuBlur);
        y += rowHeight;
        addKeyBindRow(y);
        y += rowHeight;

        // === 市场设置（仅服主）===
        if (owner) {
            y += SECTION_HEADER_HEIGHT;
            feeBox = ConfigScreenWidgets.createEditBox(this, y, String.format("%.2f", tempMarketFee),
                    s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
            y += rowHeight;
            maxListingsBox = ConfigScreenWidgets.createEditBox(this, y, String.valueOf((int) tempMaxListings),
                    s -> s.isEmpty() || s.matches("\\d{0,3}"));
            y += rowHeight;
            maxPriceBox = ConfigScreenWidgets.createEditBox(this, y, formatAmount(tempMaxPrice),
                    s -> s.isEmpty() || s.matches("\\d{0,12}"));
            y += rowHeight;
            maxTxBox = ConfigScreenWidgets.createEditBox(this, y, formatAmount(tempMaxTx),
                    s -> s.isEmpty() || s.matches("\\d{0,12}"));
            y += rowHeight;
            addToggleButtons(y, "transferEnabled", tempTransferEnabled);
            y += rowHeight;
        }

        // === 银行设置：快捷金额三档（第四个按钮固定为「全部」）===
        y += SECTION_HEADER_HEIGHT;
        quick1Box = ConfigScreenWidgets.createEditBox(this, y, formatAmount(tempQuick1), s -> s.isEmpty() || s.matches("\\d+"));
        y += rowHeight;
        quick2Box = ConfigScreenWidgets.createEditBox(this, y, formatAmount(tempQuick2), s -> s.isEmpty() || s.matches("\\d+"));
        y += rowHeight;
        quick3Box = ConfigScreenWidgets.createEditBox(this, y, formatAmount(tempQuick3), s -> s.isEmpty() || s.matches("\\d+"));
        y += rowHeight;

        // === 管理员操作（仅服主）：等价于 /marketbox set|add|reset ===
        // 服务端 AdminBalancePacket 要求权限 4，界面对齐，避免显示了却保存失败
        if (owner) {
            y += SECTION_HEADER_HEIGHT;
            AdminPanel.build(this, y, rowHeight, controlHeight,
                    contentLeft + contentWidth - PADDING - CONTROL_WIDTH, CONTROL_WIDTH,
                    w -> this.addRenderableWidget(w));
            y += rowHeight * AdminPanel.rowCount();
        }

        // === 货币面额（仅服主）：勾选启用的面额 ===
        if (owner) {
            y += SECTION_HEADER_HEIGHT;
            for (double denom : ALL_DENOMINATIONS) {
                ConfigScreenWidgets.addDenominationToggle(this, y, denom);
                y += rowHeight;
            }
        }

        // === 底部按钮 ===
        int btnY = topPos + guiHeight - BOTTOM_BAR_HEIGHT + 10;
        int btnWidth = 80;
        int btnHeight = 24;
        int totalBtnWidth = btnWidth * 3 + 8 * 2;
        int startX = leftPos + (guiWidth - totalBtnWidth) / 2;

        bottomBarWidgets.clear();

        var resetBtn = new StyledButton(startX, btnY, btnWidth, btnHeight,
                Component.translatable("gui.marketbox.config.reset_defaults"), b -> resetToDefaults());
        this.addRenderableWidget(resetBtn);
        bottomBarWidgets.add(resetBtn);

        StyledButton saveBtn = new StyledButton(startX + btnWidth + 8, btnY, btnWidth, btnHeight,
                Component.translatable("gui.marketbox.config.save"), b -> ConfigScreenState.save(this));
        this.addRenderableWidget(saveBtn);
        bottomBarWidgets.add(saveBtn);
        saveButton = saveBtn;
        saveButton.active = false;

        var cancelBtn = new StyledButton(startX + (btnWidth + 8) * 2, btnY, btnWidth, btnHeight,
                Component.translatable("gui.marketbox.config.cancel"), b -> this.onClose());
        this.addRenderableWidget(cancelBtn);
        bottomBarWidgets.add(cancelBtn);
        // 退出按钮也落在底部栏区域内，不登记会被内容区 scissor 裁成隐形按钮
        bottomBarWidgets.add(exitButton);
    }

    /** 按键绑定行 */
    private void addKeyBindRow(int y) {
        int controlX = contentLeft + contentWidth - PADDING - CONTROL_WIDTH;
        int controlCenterY = y + rowHeight / 2 - controlHeight / 2;
        this.addRenderableWidget(ConfigKeyBind.createButton(this, controlX, controlCenterY,
                CONTROL_WIDTH, controlHeight));
    }

    // 供拆出去的工具类使用：font 与 addRenderableWidget 都是 protected
    net.minecraft.client.gui.Font font() {
        return this.font;
    }

    void registerWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
        this.addRenderableWidget(widget);
    }

    /** 供 ConfigKeyBind 触发布局刷新（rebuildWidgets 为 protected） */
    void refreshWidgets() {
        // 重建前先回收输入框内容，否则刚敲的值会被 init 里的 temp* 覆盖
        ConfigScreenState.captureEdits(this);
        this.rebuildWidgets();
    }
    void rebuildWithoutCapture() { super.rebuildWidgets(); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ConfigKeyBind.handleKeyPressed(this, keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addToggleButtons(int y, String configKey, boolean currentValue) {
        int controlX = contentLeft + contentWidth - PADDING - TOGGLE_WIDTH;
        int controlCenterY = y + rowHeight / 2 - controlHeight / 2;

        StyledButton toggleBtn = new StyledButton(controlX, controlCenterY, TOGGLE_WIDTH, controlHeight,
                Component.translatable(currentValue ? "gui.marketbox.config.on" : "gui.marketbox.config.off"),
                button -> {
                    boolean newVal = !currentValue;
                    if (configKey.equals("showHud")) tempShowHud = newVal;
                    else if (configKey.equals("showMarketPriceTooltip")) tempShowMarketPriceTooltip = newVal;
                    else if (configKey.equals("disableMenuBlur")) tempDisableMenuBlur = newVal;
                    else if (configKey.equals("transferEnabled")) tempTransferEnabled = newVal;
                    this.refreshWidgets();
                }, false, currentValue);
        this.addRenderableWidget(toggleBtn);
    }

    private <T extends Enum<T>> void addCycleButton(int y, String configKey, String currentValue, T[] values) {
        int controlX = contentLeft + contentWidth - PADDING - CONTROL_WIDTH;
        int controlCenterY = y + rowHeight / 2 - controlHeight / 2;

        Component displayText = Component.translatable(
                "gui.marketbox.config." + configKey + "." + currentValue.toLowerCase());

        StyledButton cycleBtn = new StyledButton(controlX, controlCenterY, CONTROL_WIDTH, controlHeight,
                displayText, button -> {
            T current = null;
            for (T v : values) {
                if (v.name().equals(currentValue)) {
                    current = v;
                    break;
                }
            }
            if (current == null) current = values[0];
            T next = values[(current.ordinal() + 1) % values.length];

            if (configKey.equals("hudPosition")) tempHudPosition = (Config.HudPosition) next;
            else if (configKey.equals("hudTextStyle")) tempHudTextStyle = (Config.HudTextStyle) next;
            this.refreshWidgets();
        });
        this.addRenderableWidget(cycleBtn);
    }

    private void resetToDefaults() {
        ConfigScreenState.resetToDefaults(this);
    }

    /** 整数金额显示为无小数点；输入非法时回落到原值 */
    static String formatAmount(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    /** 提示条：与其他界面共用同一套 Toast，不再本地画一条会自己消失的文字 */
    private final Toast toast = new Toast();

    void showStatus(Component message, boolean isError) {
        toast.show(message, isError);
    }

    @Override
    public void showFeedback(Component message, boolean isError) {
        toast.show(message, isError);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 界面自身不应用背景模糊：直接铺半透明底。
        // 这样无需依赖「关闭菜单模糊」开关，也不会受其他模组的全局设置影响。
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0101010);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 左侧导航栏（三个界面共用）
        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getMarketBadgeCount());

        guiGraphics.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, 0xDD101010);
        guiGraphics.renderOutline(leftPos, topPos, guiWidth, guiHeight, 0xFF555555);

        // 内容区裁剪：滚动出去的行不能画到下方按钮栏上
        int contentBottom = topPos + guiHeight - BOTTOM_BAR_HEIGHT;
        // 左边界从 0 开始：侧边栏按钮位于 x=6~79，若从 leftPos 裁剪会被整块切掉
        guiGraphics.enableScissor(0, topPos, leftPos + guiWidth, contentBottom);

        int y = topPos + PADDING - scrollOffset;

        ConfigScreenRenderer.renderConfigRows(this, guiGraphics, topPos + PADDING - scrollOffset);

        // 内容区的控件也要裁剪：否则滚动时输入框会盖在底栏上。
        // 先只渲染非底栏控件，再单独渲染底栏。
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && w.visible && !bottomBarWidgets.contains(w)) {
                w.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        guiGraphics.disableScissor();

        // 底部按钮栏：实底 + 顶部分隔线，避免被内容透视
        guiGraphics.fill(leftPos, contentBottom, leftPos + guiWidth, topPos + guiHeight, 0xF0101010);
        guiGraphics.fill(leftPos, contentBottom, leftPos + guiWidth, contentBottom + 1, 0xFF3A3A3A);

        bottomBarWidgets.stream().filter(w -> w.visible)
                .forEach(w -> w.render(guiGraphics, mouseX, mouseY, partialTick));

        if (saveButton != null) {
            saveButton.active = ConfigScreenState.isDirty(this);
        }

        ConfigScreenRenderer.renderInlineValidation(this, guiGraphics);

        ConfigScreenRenderer.renderScrollBar(this, guiGraphics, contentBottom);

        toast.render(guiGraphics, this.width, this.height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            int delta = (int) (-scrollY * 14);
            int newOffset = Math.max(0, Math.min(maxScroll, scrollOffset + delta));
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                // 控件位置依赖滚动偏移，重排一次（先回收输入框内容）
                this.refreshWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        // 从 Mod Menu 进入时返回模组列表；从游戏内进入则直接关闭
        boolean fromModMenu = this.parent != null
                && this.parent.getClass().getName().contains("ModsScreen");
        this.minecraft.setScreen(fromModMenu ? this.parent : null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
