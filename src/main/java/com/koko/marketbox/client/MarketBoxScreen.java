package com.koko.marketbox.client;

import com.koko.marketbox.client.ClientData;
import com.koko.marketbox.util.MoneyUtils;
import com.koko.marketbox.networking.packet.DepositMoneyPacket;
import com.koko.marketbox.networking.packet.WithdrawMoneyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;

/**
 * Main GUI screen for MarketBox
 */
public class MarketBoxScreen extends Screen {
    /** 1.21 默认皮肤贴图（宽版 Steve），用于玩家信息尚未加载时的占位 */
    static final ResourceLocation DEFAULT_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // GUI dimensions
    int guiWidth;
    int guiHeight;
    int leftPos;
    int topPos;
    
    int leftSectionWidth;
    
    enum MenuMode {
        NONE, DEPOSIT, WITHDRAW
    }
    
    MenuMode currentMode = MenuMode.NONE;
    EditBox amountInput;
    Button confirmButton;
    /** 快捷金额按钮行的 y 坐标，-1 表示当前不在输入模式 */
    int quickRowY = -1;
    /** 顶部操作提示条 */
    final Toast toast = new Toast();
    
    // Transfer related
    boolean isScrolling = false;
    
    // History scroll
    
    static final int AVATAR_SIZE = 24;
    static final int SMALL_AVATAR_SIZE = 12; // Small avatar for leaderboard
    static final int PADDING = 10;
    
    public MarketBoxScreen() {
        super(Component.translatable("gui.marketbox.title"));
    }

    @Override
    protected void init() {
        super.init();
        // Dynamic GUI size based on screen dimensions
        // 左侧为导航栏，内容区占其余全部空间
        int navWidth = Sidebar.width(this.width);
        this.guiWidth = this.width - navWidth;
        this.guiHeight = this.height;
        this.leftPos = navWidth;
        this.topPos = 0;

        Sidebar.buttons(this, Sidebar.Page.BANK).forEach(this::addRenderableWidget);

        // 分区宽度按比例，保证不同分辨率下比例一致
        this.leftSectionWidth = (int)(this.guiWidth * 0.30f);

        int buttonWidth = leftSectionWidth - PADDING * 2;
        int buttonHeight = 18;
        int buttonX = leftPos + PADDING;
        int buttonYStart = topPos + guiHeight - (buttonHeight * 2 + 4 + PADDING); 

        // 存入 / 取出：未进入输入模式时是两个按钮；
        // 进入后该区域变为「输入框 + 快捷金额行」，不额外占用版面
        boolean editing = currentMode == MenuMode.DEPOSIT || currentMode == MenuMode.WITHDRAW;
        if (editing) {
            setupInputField(buttonX, buttonYStart, buttonWidth, buttonHeight);
            addQuickAmountRow(buttonX, buttonYStart + buttonHeight + 4, buttonWidth, buttonHeight);
        } else {
            this.addRenderableWidget(new StyledButton(buttonX, buttonYStart, buttonWidth, buttonHeight,
                Component.translatable("gui.marketbox.button.feature1"), (button) -> {
                    this.currentMode = MenuMode.DEPOSIT;
                    this.rebuildWidgets();
                }));

            this.addRenderableWidget(new StyledButton(buttonX, buttonYStart + buttonHeight + 4, buttonWidth, buttonHeight,
                Component.translatable("gui.marketbox.button.feature2"), (button) -> {
                    this.currentMode = MenuMode.WITHDRAW;
                    this.rebuildWidgets();
                }));
        }

    }

    private void setupInputField(int x, int y, int width, int height) {
        // 创建输入框 - 调整位置以修复文字上移问题
        this.amountInput = new EditBox(this.font, x + 4, y + 5, width - 24, 10, Component.empty());
        this.amountInput.setCanLoseFocus(true);
        this.amountInput.setBordered(false);
        this.amountInput.setMaxLength(12);
        this.amountInput.setTextColor(0xFFFFFF);
        this.amountInput.setFocused(true);
        this.amountInput.setFilter(s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
        this.setFocused(this.amountInput);
        
        // 创建确认按钮 ✓ (内嵌在输入框右侧)
        // 使用透明模式，背景和外边框由 render 方法统一绘制
        this.confirmButton = new StyledButton(x + width - 18, y, 18, height, Component.literal("✓"), (btn) -> {
            handleAction();
        }, true, false);
        
        this.addRenderableWidget(this.amountInput);
        this.addRenderableWidget(this.confirmButton);
    }

    /** 整数金额不带小数点，小数保留两位 */
    private static String formatAmount(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    /** 输入模式下的一行快捷金额按钮：100 / 1千 / 1万 / 全部 */
    private void addQuickAmountRow(int x, int y, int width, int height) {
        this.quickRowY = y;
        int gap = 2;
        int btnW = (width - gap * 3) / 4;

        // 前三档来自配置，第四档固定为「全部」
        double[] values = {
                com.koko.marketbox.Config.getQuickAmount(1),
                com.koko.marketbox.Config.getQuickAmount(2),
                com.koko.marketbox.Config.getQuickAmount(3),
                -1
        };
        String[] labels = {
                formatAmount(values[0]),
                formatAmount(values[1]),
                formatAmount(values[2]),
                Component.translatable("gui.marketbox.amount.all").getString()
        };

        for (int i = 0; i < labels.length; i++) {
            final double preset = values[i];
            this.addRenderableWidget(new StyledButton(x + (btnW + gap) * i, y, btnW, height,
                    Component.literal(labels[i]), b -> applyQuickAmount(preset), false, false));
        }
    }

    /**
     * 快捷金额只负责填入输入框，真正执行仍由 ✓ 按钮触发，
     * 给玩家一次确认机会，避免误点直接动账。
     */
    private void applyQuickAmount(double preset) {
        double amount = preset;
        if (preset < 0) {
            if (currentMode == MenuMode.DEPOSIT) {
                LocalPlayer local = Minecraft.getInstance().player;
                amount = local != null ? MoneyUtils.getInventoryMoneyTotal(local) : 0;
            } else {
                amount = ClientData.getPlayerBalance();
            }
        }

        if (amount <= 0) {
            showFeedback(Component.translatable("gui.marketbox.error.invalid_amount"), true);
            return;
        }

        if (amountInput == null) {
            return;
        }
        // 整数不带小数位，小数保留两位
        String text = (amount == Math.floor(amount))
                ? String.valueOf((long) amount)
                : String.format("%.2f", amount);
        amountInput.setValue(text);
        amountInput.setFocused(true);
        this.toast.clear();
    }

    /** 判断点击是否落在快捷金额行内（该区域不算「点击外部」，不退出输入模式） */
    private boolean isInQuickAmountRow(double mouseX, double mouseY) {
        if (quickRowY < 0) {
            return false;
        }
        int x = leftPos + PADDING;
        int w = leftSectionWidth - PADDING * 2;
        return mouseX >= x && mouseX <= x + w && mouseY >= quickRowY && mouseY <= quickRowY + 18;
    }

    private void handleAction() {
        String text = amountInput.getValue();
        try {
            double amount = Double.parseDouble(text);
            if (amount <= 0) throw new NumberFormatException();

            if (currentMode == MenuMode.DEPOSIT) {
                ClientNetwork.send(new DepositMoneyPacket(amount));
            } else if (currentMode == MenuMode.WITHDRAW) {
                if (ClientData.getPlayerBalance() >= amount) {
                    ClientNetwork.send(new WithdrawMoneyPacket(amount));
                } else {
                    this.toast.show(Component.translatable("gui.marketbox.error.not_enough_balance"), true);
                    return;
                }
            }

            // 成功后恢复状态
            this.currentMode = MenuMode.NONE;
            this.toast.clear();
            this.rebuildWidgets();
        } catch (NumberFormatException e) {
            this.toast.show(Component.translatable("gui.marketbox.error.invalid_amount"), true);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (currentMode != MenuMode.NONE && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            handleAction();
            return true;
        }
        
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (KeyInit.OPEN_MARKETBOX_GUI.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        return false;
    }

    /**
     * Shows an error message in the GUI
     */
    public void showError(Component message) {
        showFeedback(message, true);
    }

    /** 统一提示入口：isError 决定颜色，两条路径都带淡出 */
    void showFeedback(Component message, boolean isError) {
        this.toast.show(message, isError);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // 如果点击了输入框或确认按钮之外的区域，且当前处于输入模式，则恢复默认
        if (currentMode != MenuMode.NONE) {
            boolean clickedInput = amountInput != null && amountInput.isMouseOver(mouseX, mouseY);
            boolean clickedConfirm = confirmButton != null && confirmButton.isMouseOver(mouseX, mouseY);
            
            if (!clickedInput && !clickedConfirm && !isInQuickAmountRow(mouseX, mouseY)) {

                this.currentMode = MenuMode.NONE;
                this.toast.clear();
                this.rebuildWidgets();
                // 不再直接 return true，让 super.mouseClicked 继续处理，这样可以直接触发其他按钮的点击
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Render dark background
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 左侧导航栏（三个界面共用）
        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getBankBadgeCount());
        
        
        // 2. Render Main GUI Background
        guiGraphics.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, 0xCC101010);
        
        // 3. Render Section Dividers
        MarketBoxScreenRenderer.renderSections(this, guiGraphics);
        
        // 4. Render Player Info in the Left Section
        MarketBoxScreenRenderer.renderPlayerInfo(this, guiGraphics);
        
        // 5. Render Middle Section Content
        MarketBoxScreenRenderer.renderMiddleSection(this, guiGraphics, mouseX, mouseY);
        
        // 排行榜与收支统计已拆分到独立页面，这里不再绘制

        // 如果处于输入模式，渲染输入框背景（内嵌效果）
        if (currentMode != MenuMode.NONE && amountInput != null) {
            // 重新计算背景位置，确保覆盖整个输入区域
            int buttonHeight = 18;
            // 与 init 中的 buttonYStart 保持一致：输入框固定占据第一个按钮的位置
            int buttonYStart = topPos + guiHeight - (buttonHeight * 2 + 4 + PADDING);
            int x, y;

            // 存入与取出共用同一位置，下方一行是快捷金额按钮
            x = leftPos + PADDING;
            y = buttonYStart;
            int w = leftSectionWidth - PADDING * 2;
            int h = buttonHeight;
            
            // 绘制深色背景方块
            guiGraphics.fill(x, y, x + w, y + h, 0xFF050505);
            // 绘制边框
            guiGraphics.renderOutline(x, y, w, h, 0xFF555555);
            // 绘制输入框与确认按钮之间的分隔线
            guiGraphics.fill(x + w - 18, y, x + w - 17, y + h, 0xFF555555);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 操作提示条：置于最上层，避免被组件遮挡
        toast.render(guiGraphics, this.width, this.height);


    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


