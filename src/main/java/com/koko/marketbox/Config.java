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

    /** 结息周期上限（分钟）：一年。超过它换算成秒会 int 溢出，周期退化成 60 秒 */
    public static final int MAX_INTERVAL_MINUTES = 525_600;

    /** 服务端权威配置（利息相关） */
    public static final class Common {
        /** 默认 0.05%／期：0.19 会让货币一年膨胀上万倍，不适合长期运营 */
        public double interestRate = 0.05;
        /**
         * 结息周期，单位：分钟。
         * 默认 1440 分钟 = 24 小时（真实时间），配合 0.05% 的日利率，复利年化约 20%。
         */
        public int interestIntervalMinutes = 1440;
        /** 市场成交手续费百分比（0-100），从卖家实收中扣除 */
        public double marketFeePercent = 2.0;
        /**
         * 挂单有效期（分钟），由服主统一设定，客户端不可选。
         * 若交给玩家选择，所有人都会选最长档，市场会被长期挂单占满。
         * 默认 120 分钟（2 小时）。
         */
        public int listingExpiryMinutes = 120;
        /** 定期存款日利率（%），按期限分档 */
        public double fixedRate7Days = 0.10;
        public double fixedRate14Days = 0.12;
        public double fixedRate30Days = 0.15;
        /** 单人同时挂单数量上限 */
        public int maxListingsPerPlayer = 20;
        /** 单条挂单的价格上限（总价，单位：元） */
        public double maxListingPrice = 1_000_000;
        /** 单笔存入、取出、转账的金额上限（单位：元） */
        public double maxTransactionAmount = 1_000_000_000;
        /** 是否允许玩家之间互相转账（关闭后只能与系统交互） */
        public boolean transferEnabled = true;
        /** 禁止上架的物品 ID 列表，如 minecraft:spawner */
        public java.util.List<String> listingBlacklist = new java.util.ArrayList<>();
        /**
         * 启用的货币面额。
         * 禁用的面额不再被发放（取款、管理员发钱、找零），
         * 但玩家手里已有的仍可正常存入与统计，不会变成废纸。
         */
        public java.util.List<Double> enabledDenominations = new java.util.ArrayList<>(
                java.util.List.of(0.01, 0.1, 1.0, 5.0, 10.0, 20.0, 50.0, 100.0));
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
        LOGGER.info("Config loaded: rate={}%, interval={} min", common.interestRate, common.interestIntervalMinutes);
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
            LOGGER.error("Failed to read config " + path, e);
            return fallback;
        }
    }

    private static void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to write config " + path, e);
        }
    }

    // ---- 服务端配置 ----

    public static double getInterestRate() {
        // 读取侧也要裁剪：手改配置文件可以绕过 setInterestSettings 的写入限制。
        // 填 1e9 会让复利因子溢出、利息被永久跳过；填 1000 则一期直接发十倍利息
        double rate = common.interestRate;
        if (!Double.isFinite(rate)) {
            return 0;
        }
        return Math.max(0, Math.min(100, rate));
    }

    /** 结息周期（秒）。配置以分钟存储，这里换算后供内部计算使用 */
    public static int getInterestIntervalSeconds() {
        // 用 long 换算再裁剪：int 乘法在周期超过约 3571 万分钟时会溢出成负数，
        // 被 Math.max(60, ...) 兜成 60 秒，等于每分钟复利一次
        long seconds = (long) getInterestIntervalMinutes() * 60L;
        return (int) Math.max(60L, Math.min(seconds, Integer.MAX_VALUE));
    }

    /** 结息周期（分钟），供界面与命令展示 */
    public static int getInterestIntervalMinutes() {
        return Math.max(1, Math.min(MAX_INTERVAL_MINUTES, common.interestIntervalMinutes));
    }

    /** 指定期限的定期日利率（%） */
    public static double getFixedRate(int days) {
        double rate = switch (days) {
            case 14 -> common.fixedRate14Days;
            case 30 -> common.fixedRate30Days;
            default -> common.fixedRate7Days;
        };
        return Math.max(0, Math.min(10, rate));
    }

    public static void setFixedRates(double r7, double r14, double r30) {
        common.fixedRate7Days = Math.max(0, Math.min(10, r7));
        common.fixedRate14Days = Math.max(0, Math.min(10, r14));
        common.fixedRate30Days = Math.max(0, Math.min(10, r30));
        write(COMMON_PATH, common);
    }

    /** 启用的面额列表；空配置时回退到全部面额，避免发不出钱 */
    public static java.util.List<Double> getEnabledDenominations() {
        if (common.enabledDenominations == null || common.enabledDenominations.isEmpty()) {
            return new java.util.ArrayList<>(java.util.List.of(0.01, 0.1, 1.0, 5.0, 10.0, 20.0, 50.0, 100.0));
        }
        return new java.util.ArrayList<>(common.enabledDenominations);
    }

    public static void setEnabledDenominations(java.util.List<Double> values) {
        common.enabledDenominations = values == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(values);
        write(COMMON_PATH, common);
    }

    public static int getMaxListingsPerPlayer() {
        return Math.max(1, Math.min(200, common.maxListingsPerPlayer));
    }

    public static double getMaxListingPrice() {
        return Math.max(1, Math.min(1_000_000_000, common.maxListingPrice));
    }

    public static double getMaxTransactionAmount() {
        return Math.max(1, Math.min(1_000_000_000, common.maxTransactionAmount));
    }

    public static void setMaxListingsPerPlayer(int value) {
        common.maxListingsPerPlayer = Math.max(1, Math.min(200, value));
        write(COMMON_PATH, common);
    }

    public static void setMaxListingPrice(double value) {
        common.maxListingPrice = Math.max(1, Math.min(1_000_000_000, value));
        write(COMMON_PATH, common);
    }

    public static void setMaxTransactionAmount(double value) {
        common.maxTransactionAmount = Math.max(1, Math.min(1_000_000_000, value));
        write(COMMON_PATH, common);
    }

    public static void setTransferEnabled(boolean value) {
        common.transferEnabled = value;
        write(COMMON_PATH, common);
    }

    public static boolean isTransferEnabled() {
        return common.transferEnabled;
    }

    /** 物品 ID 是否在禁止上架名单里 */
    public static boolean isListingBlocked(String itemId) {
        return common.listingBlacklist != null && common.listingBlacklist.contains(itemId);
    }

    public static java.util.List<String> getListingBlacklist() {
        return common.listingBlacklist == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(common.listingBlacklist);
    }

    public static void addToBlacklist(String itemId) {
        if (common.listingBlacklist == null) {
            common.listingBlacklist = new java.util.ArrayList<>();
        }
        if (!common.listingBlacklist.contains(itemId)) {
            common.listingBlacklist.add(itemId);
            write(COMMON_PATH, common);
        }
    }

    public static void removeFromBlacklist(String itemId) {
        if (common.listingBlacklist != null && common.listingBlacklist.remove(itemId)) {
            write(COMMON_PATH, common);
        }
    }

    /** 挂单有效期（分钟），服务端权威，客户端只做展示 */
    public static int getListingExpiryMinutes() {
        return Math.max(5, Math.min(1440, common.listingExpiryMinutes));
    }

    /** 更新挂单有效期并落盘；裁剪到 5~1440 分钟 */
    public static void setListingExpiryMinutes(int minutes) {
        common.listingExpiryMinutes = Math.max(5, Math.min(1440, minutes));
        write(COMMON_PATH, common);
    }

    /** 手续费百分比。写入侧裁到 0-50，读取侧同样裁剪：
     *  直接改配置文件填成 150 会让卖家倒扣、填成负数等于凭空造币 */
    public static double getMarketFeePercent() {
        return Math.max(0, Math.min(50, common.marketFeePercent));
    }

    /** 手续费是否进入金库（否则销毁） */
    public static boolean isFeeToTreasury() {
        String mode = common.marketFeeMode;
        if ("DESTROY".equalsIgnoreCase(mode)) {
            return false;
        }
        if (!"TREASURY".equalsIgnoreCase(mode)) {
            // 这个字段只能手改配置文件，拼错时静默变成「销毁」会让服主以为钱进了金库
            MarketBox.LOGGER.warn("[MarketBox] Unknown marketFeeMode '{}', falling back to TREASURY", mode);
        }
        return true;
    }

    /** 写入手续费并落盘；数值裁剪到 0-50，避免误配置抽干收益 */
    public static void setMarketFeePercent(double percent) {
        common.marketFeePercent = Math.max(0, Math.min(50, percent));
        write(COMMON_PATH, common);
    }

    public static void setInterestSettings(double rate, int intervalMinutes) {
        common.interestRate = Math.max(0, Math.min(100, rate));
        common.interestIntervalMinutes = Math.max(1, Math.min(MAX_INTERVAL_MINUTES, intervalMinutes));
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
        // 手改配置写成 null 时 Gson 会覆盖字段初始值，这里兜底避免 name() 空指针
        return client.hudPosition == null ? HudPosition.TOP_LEFT : client.hudPosition;
    }

    public static HudTextStyle getHudTextStyle() {
        return client.hudTextStyle == null ? HudTextStyle.NONE : client.hudTextStyle;
    }

    public static double getHudScale() {
        double scale = client.hudScale;
        return Double.isFinite(scale) ? Math.max(0.5, Math.min(3.0, scale)) : 1.0;
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
        // 写入口裁剪：这里过去直接赋值，配置文件里一个 1e9 的缩放就能让 HUD 渲染异常
        client.hudPosition = position == null ? HudPosition.TOP_LEFT : position;
        client.hudTextStyle = style == null ? HudTextStyle.NONE : style;
        client.hudScale = Double.isFinite(scale) ? Math.max(0.5, Math.min(3.0, scale)) : 1.0;
        client.hudXOffset = Math.max(-10_000, Math.min(10_000, xOffset));
        client.hudYOffset = Math.max(-10_000, Math.min(10_000, yOffset));
        write(CLIENT_PATH, client);
    }
}
