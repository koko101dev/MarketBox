package com.koko.marketbox.event;

import com.koko.marketbox.market.MarketManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * 服务端周期任务：排行榜节流同步与市场挂单自动下架。
 */
public final class ServerEvents {
    private static volatile boolean leaderboardDirty = false;
    private static int leaderboardSyncCooldown = 0;
    /**
     * 玩家数据待落盘标记。
     * 交易后不立即 saveAll（几百人服务器上等于每笔交易写几百个文件），
     * 改为最多每 10 秒落盘一次，崩溃时最多丢失这个窗口内的变更。
     */
    private static volatile boolean playerDataDirty = false;
    private static int playerPersistCooldown = 0;

    private ServerEvents() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (leaderboardSyncCooldown > 0) {
                leaderboardSyncCooldown--;
            }

            if (leaderboardDirty && leaderboardSyncCooldown <= 0) {
                PlayerEvents.syncLeaderboard(server.getPlayerList().getPlayers());
                leaderboardDirty = false;
                // 每 5 秒最多同步一次排行榜，降低网络负担
                leaderboardSyncCooldown = 100;
            }

            if (playerPersistCooldown > 0) {
                playerPersistCooldown--;
            }

            // 交易变更合并落盘：最多每 10 秒写一次玩家数据
            if (playerDataDirty && playerPersistCooldown <= 0) {
                server.getPlayerList().saveAll();
                playerDataDirty = false;
                playerPersistCooldown = 200;
            }

            // 每 100 tick（5 秒）扫描一次过期挂单
            if (server.getTickCount() % 100 == 0) {
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    MarketManager.get(overworld).checkAutoDelist(overworld);
                }
            }
        });
    }

    public static void markLeaderboardDirty() {
        leaderboardDirty = true;
    }

    /** 标记玩家数据有变更，交由 tick 合并落盘 */
    public static void markPlayerDataDirty() {
        playerDataDirty = true;
    }
}
