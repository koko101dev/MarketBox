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

    /** 同一玩家两次敏感操作的最小间隔 */
    private static final long MIN_INTERVAL_MS = 80;

    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

    private PacketRateLimiter() {
    }

    /** 允许则记录时间并返回 true；过于频繁返回 false */
    public static boolean allow(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_ACTION.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return false;
        }
        LAST_ACTION.put(player.getUUID(), now);
        return true;
    }

    /** 玩家退出时清理，避免长期运行后 Map 无限增长 */
    public static void forget(UUID playerId) {
        if (playerId != null) {
            LAST_ACTION.remove(playerId);
        }
    }
}
