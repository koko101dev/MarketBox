package com.koko.marketbox.client;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.networking.packet.ClaimReturnedItemsPacket;
import com.koko.marketbox.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MarketScreen extends Screen {
    final Screen lastScreen;
    
    // GUI dimensions
    int guiWidth;
    int guiHeight;
    int leftPos;
    int topPos;
    
    int sidebarWidth;
    int mainWidth;
    static final int PADDING = 10;
    
    // Left side components
    ItemStack selectedItem = ItemStack.EMPTY;
    StyledButton claimButton;
    boolean isSelectingItem = false;
    StyledButton listItemButton;
    
    // Right side components
    EditBox searchBox;
    float scrollOffset = 0;
    float returnedItemsScrollOffset = 0;
    static final int CARD_WIDTH = 72;
    static final int CARD_HEIGHT = 105;

    /** 顶部操作提示条 */
    final Toast toast = new Toast();
    
    // Category system
    Category currentCategory = Category.ALL;
    final List<StyledButton> categoryButtons = new java.util.ArrayList<>();

    /**
     * 判断物品是否属于金属/宝石/矿石类资源。
     * 1.21 原版没有统一的 INGOTS / GEMS / ORES 标签，这里按物品路径做归类判断。
     */
    
    // Tooltip for truncated names
    Component activeTooltip = null;
    
    public MarketScreen(Screen lastScreen) {
        super(Component.translatable("gui.marketbox.market.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        super.init();
        
        // 左侧为导航栏，内容区占其余全部空间
        int navWidth = Sidebar.width(this.width);
        this.guiWidth = this.width - navWidth;
        this.guiHeight = this.height;
        this.leftPos = navWidth;
        this.topPos = 0;

        Sidebar.buttons(this, Sidebar.Page.MARKET).forEach(this::addRenderableWidget);

        this.sidebarWidth = (int)(this.guiWidth * 0.22f);
        this.mainWidth = this.guiWidth - this.sidebarWidth;


        // 左栏改为「我的挂单」列表（渲染见 MyListingsPanel），这里只留底部的领取按钮
        int inputX = leftPos + PADDING;
        int totalWidth = sidebarWidth - PADDING * 2;
        int claimY = topPos + guiHeight - 42;

        // 上架入口：左栏顶部的显式按钮（原先是一个看不见的 40x40 方框）
        this.listItemButton = new StyledButton(inputX, topPos + 40, totalWidth, 20,
                Component.translatable("gui.marketbox.market.list_item_button"), b -> {
            this.isSelectingItem = true;
            this.rebuildWidgets();
        });
        this.addRenderableWidget(this.listItemButton);

        // Claim button（固定左栏底部）
        this.claimButton = new StyledButton(inputX, claimY, totalWidth, 20, Component.translatable("gui.marketbox.market.claim_button"), (btn) -> {
            ClientNetwork.send(new ClaimReturnedItemsPacket());
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        });
        
        // Initial highlight state
        this.claimButton.setHighlighted(ClientData.hasReturnedItems());
        this.addRenderableWidget(this.claimButton);

        // Search Box - Top right of market section
        int searchWidth = 80;
        int searchHeight = 12;
        int searchX = leftPos + guiWidth - searchWidth - PADDING;
        int searchY = topPos + 5;
        this.searchBox = new EditBox(this.font, searchX, searchY, searchWidth, searchHeight, Component.translatable("gui.marketbox.market.search_hint"));
        this.searchBox.setBordered(true);
        this.searchBox.setMaxLength(32);
        this.searchBox.setResponder(s -> this.scrollOffset = 0);
        this.addRenderableWidget(this.searchBox);

        // Category Buttons
        int catStartX = leftPos + sidebarWidth + PADDING;
        int catY = topPos + 25;
        int catBtnHeight = 16;
        int catBtnGap = 2;
        int availableWidth = mainWidth - PADDING * 2;
        int btnWidth = (availableWidth - (Category.values().length - 1) * catBtnGap) / Category.values().length;
        
        this.categoryButtons.clear();
        for (int i = 0; i < Category.values().length; i++) {
            Category cat = Category.values()[i];
            StyledButton btn = new StyledButton(catStartX + i * (btnWidth + catBtnGap), catY, btnWidth, catBtnHeight, cat.getDisplayName(), (b) -> {
                this.currentCategory = cat;
                this.scrollOffset = 0;
                MarketScreenActions.updateCategoryButtons(this);
            });
            if (cat == currentCategory) {
                btn.setHighlighted(true);
            }
            this.addRenderableWidget(btn);
            this.categoryButtons.add(btn);
        }
    }


    /** 统计背包中与该物品完全相同的总数 */
    private int countInInventory(ItemStack target) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return target.getCount();
        }
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, target)) {
                total += stack.getCount();
            }
        }
        return Math.max(1, total);
    }

    public void showError(Component message) {
        showFeedback(message, true);
    }

    /** 统一提示入口：isError 决定颜色 */
    void showFeedback(Component message, boolean isError) {
        this.toast.show(message, isError);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 左侧导航栏（三个界面共用）
        Sidebar.render(guiGraphics, this.width, this.height, ClientData.getBankBadgeCount());
        
        // Reset active tooltip each frame
        this.activeTooltip = null;
        
        // Main Background
        guiGraphics.fill(leftPos, topPos, leftPos + guiWidth, topPos + guiHeight, 0xCC101010);
        
        // Section Dividers
        int dividerX = leftPos + sidebarWidth;
        guiGraphics.fill(dividerX, topPos + PADDING, dividerX + 1, topPos + guiHeight - PADDING, 0x44FFFFFF);
        
        // Title - Centered in the market (right) section
        int titleX = leftPos + sidebarWidth + mainWidth / 2;
        guiGraphics.drawCenteredString(this.font, this.title, titleX, topPos + 5, 0xFFFFFF);

        // 成交手续费提示：成交额从卖家实收中扣除
        double feePercent = ClientData.getMarketFeePercent();
        if (feePercent > 0) {
            String feeHint = Component.translatable("gui.marketbox.market.fee_hint",
                    String.format("%.1f", feePercent)).getString();
            int fx = titleX + this.font.width(this.title) / 2 + 10;
            int fw = this.font.width(feeHint);
            // 加底框提升可读性，原先的暗棕色几乎看不见
            guiGraphics.fill(fx - 3, topPos + 3, fx + fw + 3, topPos + 15, 0x66000000);
            guiGraphics.renderOutline(fx - 3, topPos + 3, fw + 6, 12, 0x66FFAA00);
            guiGraphics.drawString(this.font, feeHint, fx, topPos + 6, 0xFFCC66, false);
        }
        
        MarketScreenRenderer.renderLeftSection(this, guiGraphics, mouseX, mouseY);
        MarketScreenRenderer.renderRightSection(this, guiGraphics, mouseX, mouseY);
        MarketScreenRenderer.renderReturnedItemsSection(this, guiGraphics, mouseX, mouseY);
        
        // Update claim button highlight in real-time
        if (this.claimButton != null) {
            this.claimButton.setHighlighted(ClientData.hasReturnedItems());
        }
        
        // Update widget states based on modal
        MarketScreenActions.updateWidgetStates(this);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render manual hint for search box if empty, visible and not focused
        if (this.searchBox.visible && this.searchBox.getValue().isEmpty() && !this.searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("gui.marketbox.market.search_hint"), 
                this.searchBox.getX() + 4, this.searchBox.getY() + (this.searchBox.getHeight() - 8) / 2, 0xFFAAAAAA, false);
        }
        
        // Render Inventory at bottom for selection - Move to very end for proper layering
        if (isSelectingItem) {
            MarketScreenRenderer.renderPlayerInventory(this, guiGraphics, mouseX, mouseY);
        }
        
        // 操作提示条
        toast.render(guiGraphics, this.width, this.height);

        // Render tooltip if active
        if (activeTooltip != null) {
            guiGraphics.renderTooltip(this.font, activeTooltip, mouseX, mouseY);
        }
    }








    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        if (KeyInit.OPEN_MARKETBOX_GUI.matches(keyCode, scanCode)) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // First, let widgets handle the click (focus, buttons, etc.)
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 左栏「我的挂单」：点击任意一行即下架
        if (button == 0 && !isSelectingItem && MyListingsPanel.handleClick(this, mouseX, mouseY)) {
            return true;
        }

        // If no widget was clicked, we manually unfocus our input boxes
        this.searchBox.setFocused(false);

        // Check if clicking inventory while selecting
        if (isSelectingItem) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                int invW = 18 * 9 + 20;
                int invH = 18 * 4 + 30;
                int invX = (this.width - invW) / 2;
                int invY = (this.height - invH) / 2;
                
                int slotStartX = invX + 10;
                int slotStartY = invY + 22;
                
                boolean clickedInsideModal = mouseX >= invX && mouseX <= invX + invW && mouseY >= invY && mouseY <= invY + invH;
                
                if (clickedInsideModal) {
                    for (int i = 0; i < 36; i++) {
                        int row = i / 9;
                        int col = i % 9;
                        int x = slotStartX + col * 18;
                        int y = slotStartY + row * 18;
                        
                        if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
                            // Map visual slot to inventory slot for clicking too
                            int slotIdx;
                            if (row < 3) {
                                slotIdx = 9 + row * 9 + col;
                            } else {
                                slotIdx = col;
                            }
                            
                            ItemStack stack = player.getInventory().getItem(slotIdx);
                            if (!stack.isEmpty()) {
                                if (MoneyUtils.isCurrency(stack)) {
                                    showError(Component.translatable("gui.marketbox.market.error.cannot_list_currency"));
                                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                                } else {
                                    // 点击背包物品直接打开上架弹窗
                                    this.selectedItem = stack.copy();
                                    this.isSelectingItem = false;
                                    Minecraft.getInstance().setScreen(
                                            new ListingDialogScreen(this, stack.copy(), countInInventory(stack)));
                                    return true;
                                }
                            }
                            return true;
                        }
                    }
                } else {
                    // Clicked outside modal, close selection
                    this.isSelectingItem = false;
                    return true;
                }
            }
        } else {
            // Only handle market listing clicks if NOT selecting item
            int startX = leftPos + sidebarWidth + PADDING;
            int startY = topPos + 45; // Updated to match renderRightSection
            int viewWidth = mainWidth - PADDING * 2;
            int viewHeight = guiHeight - 55; // Updated to match renderRightSection

            if (mouseX >= startX && mouseX <= startX + viewWidth && mouseY >= startY && mouseY <= startY + viewHeight) {
                List<MarketListing> listings = ClientData.getMarketListings();
                // Apply search filter and category filter for click detection too
                String filter = searchBox.getValue().toLowerCase();
                List<MarketListing> filteredListings = listings.stream()
                        .filter(l -> l.item().getHoverName().getString().toLowerCase().contains(filter))
                        .filter(l -> currentCategory.matches(l.item())) // Add category filter
                        .toList();

                int cardPadding = 5;
                int cardsPerRow = Math.max(1, viewWidth / (CARD_WIDTH + cardPadding));
                
                for (int i = 0; i < filteredListings.size(); i++) {
                    MarketListing listing = filteredListings.get(i);
                    int row = i / cardsPerRow;
                    int col = i % cardsPerRow;
                    
                    int x = startX + col * (CARD_WIDTH + cardPadding);
                    int y = startY + row * (CARD_HEIGHT + cardPadding) - (int)scrollOffset;
                    
                    if (y + CARD_HEIGHT < startY || y > startY + viewHeight) continue;
                    
                    // Check if clicking the "Buy" button specifically
                    int btnW = CARD_WIDTH - 8;
                    int btnH = 14;
                    int btnX = x + 4;
                    int btnY = y + CARD_HEIGHT - btnH - 4;

                    Player player = Minecraft.getInstance().player;
                    boolean isOwner = player != null && listing.sellerUUID().equals(player.getUUID());

                    if (isOwner) {
                        int halfW = (btnW - 2) / 2;
                        // Buy button
                        if (mouseX >= btnX && mouseX <= btnX + halfW && mouseY >= btnY && mouseY <= btnY + btnH) {
                            Minecraft.getInstance().setScreen(new PurchaseDialogScreen(this, listing));
                            return true;
                        }
                        // Delist button
                        int delistX = btnX + halfW + 2;
                        if (mouseX >= delistX && mouseX <= delistX + halfW && mouseY >= btnY && mouseY <= btnY + btnH) {
                            ClientNetwork.send(new com.koko.marketbox.networking.packet.DelistMarketItemPacket(listing.id()));
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                            return true;
                        }
                    } else {
                        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                            Minecraft.getInstance().setScreen(new PurchaseDialogScreen(this, listing));
                            return true;
                        }
                    }
                }
            } else {
                // Clicked outside market cards and widgets - return to default state (clear search)
                if (!this.searchBox.getValue().isEmpty()) {
                    this.searchBox.setValue("");
                }
            }
        }
        
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Left side - Returned Items
        int leftStartX = leftPos + PADDING;
        int leftStartY = claimButton.getY() + claimButton.getHeight() + 2;
        int leftWidth = sidebarWidth - PADDING * 2;
        int leftHeight = (topPos + guiHeight - PADDING) - leftStartY;
        
        if (mouseX >= leftStartX && mouseX <= leftStartX + leftWidth && mouseY >= leftStartY && mouseY <= leftStartY + leftHeight) {
            List<ItemStack> items = ClientData.getReturnedItems();
            int itemHeight = 22;
            int totalContentHeight = items.size() * itemHeight;
            
            if (totalContentHeight > leftHeight - 4) {
                returnedItemsScrollOffset -= scrollY * 15;
                returnedItemsScrollOffset = Mth.clamp(returnedItemsScrollOffset, 0, totalContentHeight - (leftHeight - 4));
                return true;
            }
        }

        // Right side - Market Listings
        int startX = leftPos + sidebarWidth + PADDING;
        int startY = topPos + 45; // Updated to match renderRightSection
        int viewWidth = mainWidth - PADDING * 2;
        int viewHeight = guiHeight - 55; // Updated to match renderRightSection
        
        if (mouseX >= startX && mouseX <= startX + viewWidth && mouseY >= startY && mouseY <= startY + viewHeight) {
            List<MarketListing> listings = ClientData.getMarketListings();
            String filter = searchBox.getValue().toLowerCase();
            List<MarketListing> filteredListings = listings.stream()
                    .filter(l -> l.item().getHoverName().getString().toLowerCase().contains(filter))
                    .filter(l -> currentCategory.matches(l.item())) // Add category filter
                    .toList();
                    
            int cardPadding = 5;
            int cardsPerRow = Math.max(1, viewWidth / (CARD_WIDTH + cardPadding));
            int totalRows = (int) Math.ceil((double) filteredListings.size() / cardsPerRow);
            int contentHeight = totalRows * (CARD_HEIGHT + cardPadding);
            
            if (contentHeight > viewHeight) {
                scrollOffset -= scrollY * 20;
                scrollOffset = Mth.clamp(scrollOffset, 0, contentHeight - viewHeight);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        // 侧边栏各页共用一个窗口，ESC 直接关闭到游戏
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
