package com.koko.marketbox.networking;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家操作频率限制。
 *
 * 背景：Fabric 的自定义网络包，客户端可以任意伪造并按任意频率发送，
 * 而每个请求都要扫描背包、读写存档。没有限流时，一个恶意客户端
 * 每秒能推送数百个请求，把服务端主线程拖垮。
 *
 * 这里做的是最粗但有效的一道闸：同一玩家的敏感操作至少间隔 MIN_INTERVAL_MS。
 * 正常人手点击间隔远大于此值，不影响体验。
 */
public final class PacketRateLimiter {

    /**
     * 同一玩家两次敏感操作的最小间隔。
     * 80ms 在局域网（延迟接近 0）下会吞掉快速双击的第二下，
     * 放宽到 120ms 仍然远低于恶意客户端的请求频率，但不影响正常操作。
     */
    private static final long MIN_INTERVAL_MS = 120;

    /** key = 玩家 UUID + ":" + 操作类型，按操作分别计时 */
    private static final Map<String, Long> LAST_ACTION = new ConcurrentHashMap<>();

    private PacketRateLimiter() {
    }

    /** 允许则记录时间并返回 true；过于频繁返回 false */
    public static boolean allow(ServerPlayer player) {
        return allow(player, "action");
    }

    /**
     * 按操作类型分桶限流。
     *
     * 所有操作共用一个窗口时，「存完钱立刻开定期」会被当成连点吞掉，
     * 玩家看到的是「点了没反应」。分桶后同类操作仍防连点，不同类互不影响。
     */
    public static boolean allow(ServerPlayer player, String bucket) {
        if (player == null) {
            return false;
        }
        String key = player.getUUID() + ":" + bucket;
        long now = System.currentTimeMillis();
        Long last = LAST_ACTION.get(key);
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return false;
        }
        LAST_ACTION.put(key, now);
        return true;
    }

    /** 玩家退出时清理，避免长期运行后 Map 无限增长 */
    public static void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String prefix = playerId + ":";
        LAST_ACTION.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
