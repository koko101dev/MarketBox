package com.koko.marketbox.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端发包入口。
 * 单独成类是为了把 ClientPlayNetworking 的引用限制在客户端，避免服务端加载客户端类。
 */
public final class ClientNetwork {
    private ClientNetwork() {
    }

    public static void send(CustomPacketPayload payload) {
        // 服务端未注册该通道（如单人游戏未同步）时不发包，避免抛异常中断界面逻辑
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
