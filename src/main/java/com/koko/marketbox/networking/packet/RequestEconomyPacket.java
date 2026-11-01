package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.capabilities.PlayerBalanceAttachment;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.util.EconomyStats;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 -> 服务端：请求经济概览数据（打开经济页时发送）。
 * 数据量小且不是每帧需要，用请求-响应模式而不是持续推送。
 */
public class RequestEconomyPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestEconomyPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("request_economy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestEconomyPacket> CODEC =
            StreamCodec.unit(new RequestEconomyPacket());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 统计并发回经济概览（仅聚合值，不含个人数据） */
    public static void handle(RequestEconomyPacket payload, ServerPlayNetworking.Context context) {
        var player = context.player();
        context.server().execute(() -> {
            try {
                var server = player.getServer();
                if (server == null) {
                    return;
                }
                var manager = MarketManager.get(server.overworld());

                double onlineTotal = 0;
                int onlineCount = 0;
                for (var online : server.getPlayerList().getPlayers()) {
                    onlineTotal += PlayerBalanceAttachment.get(online).getBalance();
                    onlineCount++;
                }

                ModMessages.sendToPlayer(player, new SyncEconomyPacket(
                        onlineTotal, onlineCount,
                        manager.getTreasuryBalance(),
                        EconomyStats.getInterestIssued(),
                        EconomyStats.getAdminIssued(),
                        EconomyStats.getFeeDestroyed(),
                        EconomyStats.getFeeToTreasury(),
                        manager.getListings().size()));
            } catch (Exception e) {
                MarketBox.LOGGER.error("统计经济数据失败 类型={}", e.getClass().getSimpleName());
            }
        });
    }
}
