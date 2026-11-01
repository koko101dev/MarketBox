package com.koko.marketbox.client;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.networking.packet.UpdateCommonConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * ConfigScreen 的状态判定：检测配置是否被修改（用于启用/禁用保存按钮）。
 * 拆出原因：ConfigScreen 需要控制在 500 行规范上限内。
 */
final class ConfigScreenState {
    private ConfigScreenState() {
    }

    /** 恢复默认值；放在这里是为了让 ConfigScreen 保持在 500 行以内 */
    static void resetToDefaults(ConfigScreen screen) {
        // 直接取 Config 里的字段默认值，避免默认值在两处重复定义导致不一致
        Config.Common commonDefaults = new Config.Common();
        Config.Client clientDefaults = new Config.Client();

        screen.tempInterestRate = commonDefaults.interestRate;
        screen.tempInterestIntervalMinutes = commonDefaults.interestIntervalMinutes;
        screen.tempMarketFee = commonDefaults.marketFeePercent;

        screen.tempShowHud = clientDefaults.showHud;
        screen.tempShowMarketPriceTooltip = clientDefaults.showMarketPriceTooltip;
        screen.tempDisableMenuBlur = clientDefaults.disableMenuBlur;
        screen.tempHudPosition = clientDefaults.hudPosition;
        screen.tempHudTextStyle = clientDefaults.hudTextStyle;
        screen.tempHudScale = clientDefaults.hudScale;
        screen.tempHudXOffset = clientDefaults.hudXOffset;
        screen.tempHudYOffset = clientDefaults.hudYOffset;

        screen.tempQuick1 = clientDefaults.quickAmount1;
        screen.tempQuick2 = clientDefaults.quickAmount2;
        screen.tempQuick3 = clientDefaults.quickAmount3;

        screen.refreshWidgets();
        screen.showStatus(Component.translatable("gui.marketbox.config.reset_done"), false);
    }

    static boolean isDirty(ConfigScreen screen) {
        // 开关与循环按钮
        if (screen.tempShowHud != screen.savedShowHud) return true;
        if (screen.tempShowMarketPriceTooltip != screen.savedShowMarketPriceTooltip) return true;
        if (screen.tempDisableMenuBlur != screen.savedDisableMenuBlur) return true;
        if (screen.tempHudPosition != screen.savedHudPosition) return true;
        if (screen.tempHudTextStyle != screen.savedHudTextStyle) return true;

        // 输入框（空值视为未修改，避免误判）
        try {
            if (screen.interestRateBox != null && !screen.interestRateBox.getValue().isEmpty()) {
                if (Math.abs(Double.parseDouble(screen.interestRateBox.getValue()) - screen.savedInterestRate) > 0.001) {
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            if (screen.interestIntervalBox != null && !screen.interestIntervalBox.getValue().isEmpty()) {
                if (Integer.parseInt(screen.interestIntervalBox.getValue()) != screen.savedInterestIntervalMinutes) {
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            if (screen.hudScaleBox != null && !screen.hudScaleBox.getValue().isEmpty()) {
                if (Math.abs(Double.parseDouble(screen.hudScaleBox.getValue()) - screen.savedHudScale) > 0.01) {
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            if (screen.hudXOffsetBox != null && !screen.hudXOffsetBox.getValue().isEmpty()) {
                if (Integer.parseInt(screen.hudXOffsetBox.getValue()) != screen.savedHudXOffset) {
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            if (screen.hudYOffsetBox != null && !screen.hudYOffsetBox.getValue().isEmpty()) {
                if (Integer.parseInt(screen.hudYOffsetBox.getValue()) != screen.savedHudYOffset) {
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        // 手续费
        try {
            if (screen.feeBox != null && !screen.feeBox.getValue().isEmpty()) {
                if (Math.abs(Double.parseDouble(screen.feeBox.getValue()) - screen.savedMarketFee) > 0.001) return true;
            }
        } catch (NumberFormatException ignored) {
        }

        // 快捷金额
        try {
            if (screen.quick1Box != null && !screen.quick1Box.getValue().isEmpty()) {
                if (Math.abs(Double.parseDouble(screen.quick1Box.getValue()) - screen.savedQuick1) > 0.001) return true;
            }
            if (screen.quick2Box != null && !screen.quick2Box.getValue().isEmpty()) {
                if (Math.abs(Double.parseDouble(screen.quick2Box.getValue()) - screen.savedQuick2) > 0.001) return true;
            }
            if (screen.quick3Box != null && !screen.quick3Box.getValue().isEmpty()) {
                if (Math.abs(Double.parseDouble(screen.quick3Box.getValue()) - screen.savedQuick3) > 0.001) return true;
            }
        } catch (NumberFormatException ignored) {
        }

        return false;
    }

    static void save(ConfigScreen screen) {
        double rate;
        int interval;
        double scale;
        int xOffset;
        int yOffset;
        try {
            rate = Double.parseDouble(screen.interestRateBox.getValue());
            interval = Integer.parseInt(screen.interestIntervalBox.getValue());
            scale = Double.parseDouble(screen.hudScaleBox.getValue());
            xOffset = Integer.parseInt(screen.hudXOffsetBox.getValue());
            yOffset = Integer.parseInt(screen.hudYOffsetBox.getValue());
        } catch (NumberFormatException e) {
            screen.showStatus(Component.translatable("gui.marketbox.config.error.invalid_number"), true);
            return;
        }

        if (rate < 0.0 || rate > 100.0) {
            screen.showStatus(Component.translatable("gui.marketbox.config.error.rate_range", "0", "100"), true);
            return;
        }
        if (interval < 1) {
            screen.showStatus(Component.translatable("gui.marketbox.config.error.interval_min", "1"), true);
            return;
        }
        if (scale < 0.5) {
            screen.showStatus(Component.translatable("gui.marketbox.config.error.scale_min", "0.5"), true);
            return;
        }
        if (scale > 3.0) {
            screen.showStatus(Component.translatable("gui.marketbox.config.error.scale_max", "3.0"), true);
            return;
        }

        // 利息属于服务端权威配置：联机时交给服务端处理，单机/标题界面直接写本地
        boolean inGame = Minecraft.getInstance().getConnection() != null;
        if (inGame) {
            try {
                ClientNetwork.send(new UpdateCommonConfigPacket(rate, interval, parseOrDefault(screen.feeBox, screen.savedMarketFee)));
            } catch (Exception e) {
                MarketBox.LOGGER.error("同步配置到服务端失败", e);
                screen.showStatus(Component.translatable("gui.marketbox.config.error.network"), true);
                return;
            }
        } else {
            try {
                Config.setInterestSettings(rate, interval);
            } catch (Exception e) {
                screen.showStatus(Component.translatable("gui.marketbox.config.error.save_failed"), true);
                return;
            }
        }

        try {
            Config.saveQuickAmounts(
                    parseOrDefault(screen.quick1Box, screen.savedQuick1),
                    parseOrDefault(screen.quick2Box, screen.savedQuick2),
                    parseOrDefault(screen.quick3Box, screen.savedQuick3));
            Config.saveClient(screen.tempShowHud, screen.tempShowMarketPriceTooltip, screen.tempDisableMenuBlur,
                    screen.tempHudPosition, screen.tempHudTextStyle, scale, xOffset, yOffset);
            // 模糊度是客户端全局选项，保存后立即生效
            MarketBoxClient.applyMenuBlurSetting();
        } catch (Exception e) {
            screen.showStatus(Component.translatable("gui.marketbox.config.error.save_failed"), true);
            return;
        }

        // 更新基线，保存按钮回到禁用态
        screen.savedInterestRate = rate;
        screen.savedInterestIntervalMinutes = interval;
        screen.savedShowHud = screen.tempShowHud;
        screen.savedShowMarketPriceTooltip = screen.tempShowMarketPriceTooltip;
        screen.savedDisableMenuBlur = screen.tempDisableMenuBlur;
        screen.savedHudPosition = screen.tempHudPosition;
        screen.savedHudTextStyle = screen.tempHudTextStyle;
        screen.savedHudScale = scale;
        screen.savedHudXOffset = xOffset;
        screen.savedHudYOffset = yOffset;
        screen.savedMarketFee = parseOrDefault(screen.feeBox, screen.savedMarketFee);
        screen.savedQuick1 = parseOrDefault(screen.quick1Box, screen.savedQuick1);
        screen.savedQuick2 = parseOrDefault(screen.quick2Box, screen.savedQuick2);
        screen.savedQuick3 = parseOrDefault(screen.quick3Box, screen.savedQuick3);

        screen.showStatus(Component.translatable("gui.marketbox.config.saved"), false);
    }

    private static double parseOrDefault(EditBox box, double fallback) {
        try {
            double v = Double.parseDouble(box.getValue());
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
