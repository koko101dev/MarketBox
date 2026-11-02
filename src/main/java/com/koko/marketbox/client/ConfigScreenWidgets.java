package com.koko.marketbox.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

/**
 * 设置页的控件构建。
 *
 * 从 ConfigScreen 拆出：AGENTS.txt 四.2 要求单个 Java 文件不超过 500 行。
 */
final class ConfigScreenWidgets {

    private ConfigScreenWidgets() {
    }

    /** 单个货币面额的启用开关 */
    static void addDenominationToggle(ConfigScreen screen, int y, double denom) {
        int controlX = screen.contentLeft + screen.contentWidth - ConfigScreen.PADDING - ConfigScreen.TOGGLE_WIDTH;
        int controlCenterY = y + screen.rowHeight / 2 - screen.controlHeight / 2;
        boolean enabled = screen.tempEnabledDenominations.contains(denom);

        screen.registerWidget(new StyledButton(controlX, controlCenterY,
                ConfigScreen.TOGGLE_WIDTH, screen.controlHeight,
                Component.translatable(enabled ? "gui.marketbox.config.on" : "gui.marketbox.config.off"),
                button -> {
                    if (screen.tempEnabledDenominations.contains(denom)) {
                        screen.tempEnabledDenominations.remove(denom);
                    } else {
                        screen.tempEnabledDenominations.add(denom);
                    }
                    screen.refreshWidgets();
                }, false, enabled));
    }

    /** 内容区右侧的定宽输入框，并登记到界面的 editBoxes */
    static EditBox createEditBox(ConfigScreen screen, int y, String defaultValue, Predicate<String> filter) {
        int controlX = screen.contentLeft + screen.contentWidth - ConfigScreen.PADDING - ConfigScreen.CONTROL_WIDTH;
        int controlCenterY = y + screen.rowHeight / 2 - screen.controlHeight / 2;
        EditBox box = new EditBox(screen.font(), controlX, controlCenterY,
                ConfigScreen.CONTROL_WIDTH, screen.controlHeight, Component.empty());
        box.setMaxLength(10);
        box.setFilter(filter);
        box.setValue(defaultValue);
        box.setCanLoseFocus(true);
        screen.registerWidget(box);
        screen.editBoxes.add(box);
        return box;
    }
}
