package com.koko.marketbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置系统：Fabric 无内置配置 API，改用 JSON 文件持久化。
 * 分为服务端权威的 common 配置与客户端本地的 client 配置。
 */
public final class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("MarketBox/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path COMMON_PATH = FabricLoader.getInstance().getConfigDir().resolve("marketbox-common.json");
    private static final Path CLIENT_PATH = FabricLoader.getInstance().getConfigDir().resolve("marketbox-client.json");

    /** HUD 显示位置 */
    public enum HudPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** HUD 文字样式 */
    public enum HudTextStyle {
        NONE, SHADOW, BACKGROUND
    }

    /** 服务端权威配置（利息相关） */
    public static final class Common {
        /** 默认 0.05%／期：0.19 会让货币一年膨胀上万倍，不适合长期运营 */
        public double interestRate = 0.05;
        /**
         * 结息周期，单位：分钟。
         * 默认 1440 分钟 = 24 小时（真实时间），配合 0.05% 的利率年化约 18%。
         * 早期版本用秒（1800 = 30 分钟），复利过猛，已改用分钟并把默认值调到一天。
         */
        public int interestIntervalMinutes = 1440;
        /** 市场成交手续费百分比（0-100），从卖家实收中扣除 */
        public double marketFeePercent = 2.0;
        /**
         * 手续费去向：
         * TREASURY = 进入市场金库，服主可用 /marketbox treasury withdraw 拨款（默认）
         * DESTROY  = 直接销毁，用于回收货币、抑制通胀
         * 只允许通过配置文件修改，游戏内仅只读展示，避免多管理员互相改成自己。
         */
        public String marketFeeMode = "TREASURY";
    }

    /** 客户端本地配置（HUD 相关） */
    public static final class Client {
        public boolean showHud = true;
        public boolean showMarketPriceTooltip = true;
        /** 是否关闭 1.20.2+ 的菜单背景模糊，保证界面可读性 */
        /**
         * 是否把全局菜单模糊度也设为 0。
         * 默认 false：本模组界面自身已跳过模糊，无需干预其他模组与原版界面。
         */
        public boolean disableMenuBlur = false;
        /** 银行页快捷金额按钮的前三个档位（第四个固定为「全部」） */
        public double quickAmount1 = 100;
        public double quickAmount2 = 1000;
        public double quickAmount3 = 10000;
        public HudPosition hudPosition = HudPosition.TOP_LEFT;
        public HudTextStyle hudTextStyle = HudTextStyle.NONE;
        public double hudScale = 1.0;
        public int hudXOffset = 0;
        public int hudYOffset = 0;
    }

    private static Common common = new Common();
    private static Client client = new Client();

    private Config() {
    }

    /** 加载全部配置，失败时回落默认值并记录日志 */
    public static void load() {
        common = read(COMMON_PATH, Common.class, new Common());
        client = read(CLIENT_PATH, Client.class, new Client());
        LOGGER.info("配置加载完成：利率={}%，周期={}分钟", common.interestRate, common.interestIntervalMinutes);
    }

    private static <T> T read(Path path, Class<T> type, T fallback) {
        try {
            if (!Files.exists(path)) {
                write(path, fallback);
                return fallback;
            }
            T parsed = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
            return parsed == null ? fallback : parsed;
        } catch (Exception e) {
            // 配置损坏不应导致游戏崩溃，回落默认值
            LOGGER.error("读取配置失败 {}（类型={}）：{}", path, e.getClass().getSimpleName(), e.getMessage());
            return fallback;
        }
    }

    private static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("写入配置失败 {}（类型={}）：{}", path, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    // ---- 服务端配置 ----

    public static double getInterestRate() {
        return common.interestRate;
    }

    /** 结息周期（秒）。配置以分钟存储，这里换算后供内部计算使用 */
    public static int getInterestIntervalSeconds() {
        return Math.max(60, common.interestIntervalMinutes * 60);
    }

    /** 结息周期（分钟），供界面与命令展示 */
    public static int getInterestIntervalMinutes() {
        return common.interestIntervalMinutes;
    }

    /** 更新利息参数并立即持久化，供命令与配置包调用 */
    public static double getMarketFeePercent() {
        return common.marketFeePercent;
    }

    /** 手续费是否进入金库（否则销毁） */
    public static boolean isFeeToTreasury() {
        return "TREASURY".equalsIgnoreCase(common.marketFeeMode);
    }

    /** 写入手续费并落盘；数值裁剪到 0-50，避免误配置抽干收益 */
    public static void setMarketFeePercent(double percent) {
        common.marketFeePercent = Math.max(0, Math.min(50, percent));
        write(COMMON_PATH, common);
    }

    public static void setInterestSettings(double rate, int intervalMinutes) {
        common.interestRate = rate;
        common.interestIntervalMinutes = Math.max(1, intervalMinutes);
        write(COMMON_PATH, common);
    }

    // ---- 客户端配置 ----

    public static boolean isShowHud() {
        return client.showHud;
    }

    public static boolean isShowMarketPriceTooltip() {
        return client.showMarketPriceTooltip;
    }

    public static double getQuickAmount(int index) {
        return switch (index) {
            case 1 -> client.quickAmount1;
            case 2 -> client.quickAmount2;
            default -> client.quickAmount3;
        };
    }

    /** 保存快捷金额三档（小于等于 0 的值会被忽略，保留原值） */
    public static void saveQuickAmounts(double a, double b, double c) {
        if (a > 0) client.quickAmount1 = a;
        if (b > 0) client.quickAmount2 = b;
        if (c > 0) client.quickAmount3 = c;
        write(CLIENT_PATH, client);
    }

    public static boolean isDisableMenuBlur() {
        return client.disableMenuBlur;
    }

    public static HudPosition getHudPosition() {
        return client.hudPosition;
    }

    public static HudTextStyle getHudTextStyle() {
        return client.hudTextStyle;
    }

    public static double getHudScale() {
        return client.hudScale;
    }

    public static int getHudXOffset() {
        return client.hudXOffset;
    }

    public static int getHudYOffset() {
        return client.hudYOffset;
    }

    /** 保存客户端 HUD 设置 */
    public static void saveClient(boolean showHud, boolean showTooltip, boolean disableMenuBlur,
                                  HudPosition position, HudTextStyle style, double scale,
                                  int xOffset, int yOffset) {
        client.disableMenuBlur = disableMenuBlur;
        client.showHud = showHud;
        client.showMarketPriceTooltip = showTooltip;
        client.hudPosition = position;
        client.hudTextStyle = style;
        client.hudScale = scale;
        client.hudXOffset = xOffset;
        client.hudYOffset = yOffset;
        write(CLIENT_PATH, client);
    }
}
