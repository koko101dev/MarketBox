package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.event.PlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端 -> 服务端：订阅 / 取消订阅排行榜。
 *
 * 排行榜原先每 5 秒向全服广播一次全量数据，百人服务器每次约 500KB。
 * 改为只有打开排行榜界面的玩家才接收更新，关闭界面即取消订阅。
 */
public class RequestLeaderboardPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestLeaderboardPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("request_leaderboard"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestLeaderboardPacket> CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBoolean(packet.subscribe),
                    buf -> new RequestLeaderboardPacket(buf.readBoolean())
            );

    private final boolean subscribe;

    public RequestLeaderboardPacket(boolean subscribe) {
        this.subscribe = subscribe;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RequestLeaderboardPacket payload, ServerPlayNetworking.Context context) {
        var player = context.player();
        context.server().execute(() -> {
            if (payload.subscribe) {
                PlayerEvents.subscribeLeaderboard(player);
            } else {
                PlayerEvents.unsubscribeLeaderboard(player);
            }
        });
    }
}
