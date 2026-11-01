package com.koko.marketbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Custom button class with flat style.
 */
public class StyledButton extends Button {
    private boolean isHighlighted = false;
    private boolean transparent = false; // 是否透明（不绘制背景和边框）

    public StyledButton(int x, int y, int width, int height, Component label, Button.OnPress onPress) {
        super(x, y, width, height, label, onPress, Button.DEFAULT_NARRATION);
    }

    public StyledButton(int x, int y, int width, int height, Component label, Button.OnPress onPress, boolean transparent, boolean highlighted) {
        super(x, y, width, height, label, onPress, Button.DEFAULT_NARRATION);
        this.transparent = transparent;
        this.isHighlighted = highlighted;
    }

    public void setHighlighted(boolean highlighted) {
        this.isHighlighted = highlighted;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) return;

        Font font = Minecraft.getInstance().font;

        if (!transparent) {
            // Determine colors based on state
            int bgColor;
            int borderColor;
            
            if (isHighlighted) {
                bgColor = 0xEE404040; 
                borderColor = 0xFFFFFFFF; 
            } else {
                bgColor = this.isHovered() ? 0xEE303030 : 0xCC151515;
                borderColor = this.isHovered() ? 0xFF666666 : 0xFF444444;
            }
            
            // Draw background
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            
            // Draw border
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);
        } else if (this.isHovered()) {
            // 透明模式下仅在悬停时绘制微弱背景
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x44FFFFFF);
        }
        
        int textColor = this.active ? (this.isHovered() || isHighlighted ? 0xFFFFFF : 0xDDDDDD) : 0x666666;

        // Draw text (centered)
        int textX = this.getX() + this.width / 2;
        int textY = this.getY() + (this.height - 8) / 2;
        
        // Scale text if it's too long
        int labelWidth = font.width(this.getMessage());
        float scale = 1.0f;
        if (labelWidth > this.width - 4) {
            scale = (float)(this.width - 4) / labelWidth;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(textX, textY, 0);
        guiGraphics.pose().scale(scale, scale, scale);
        guiGraphics.drawCenteredString(font, this.getMessage(), 0, 0, textColor);
        guiGraphics.pose().popPose();
    }
}
