package com.koko.marketbox.networking;

import com.koko.marketbox.networking.packet.ActionFeedbackPacket;
import com.koko.marketbox.networking.packet.ClaimReturnedItemsPacket;
import com.koko.marketbox.networking.packet.DelistMarketItemPacket;
import com.koko.marketbox.networking.packet.AdminBalancePacket;
import com.koko.marketbox.networking.packet.DepositMoneyPacket;
import com.koko.marketbox.networking.packet.ListMarketItemPacket;
import com.koko.marketbox.networking.packet.PurchaseMarketItemPacket;
import com.koko.marketbox.networking.packet.SyncLeaderboardPacket;
import com.koko.marketbox.networking.packet.MarketListingAddedPacket;
import com.koko.marketbox.networking.packet.MarketListingRemovedPacket;
import com.koko.marketbox.networking.packet.SyncAdminStatusPacket;
import com.koko.marketbox.networking.packet.SyncMarketListingsPacket;
import com.koko.marketbox.networking.packet.SyncPlayerBalancePacket;
import com.koko.marketbox.networking.packet.TransferMoneyPacket;
import com.koko.marketbox.networking.packet.UpdateCommonConfigPacket;
import com.koko.marketbox.networking.packet.WithdrawMoneyPacket;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 网络通道注册与发送工具。
 * 1.21 起改用 CustomPacketPayload + StreamCodec，取代 Forge 的 SimpleChannel。
 * 客户端 -> 服务端的发包入口位于 client 包（避免服务端加载客户端类）。
 */
public final class ModMessages {
    private ModMessages() {
    }

    public static void register() {
        // ---- 客户端 -> 服务端 ----
        PayloadTypeRegistry.playC2S().register(AdminBalancePacket.ID, AdminBalancePacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AdminBalancePacket.ID, AdminBalancePacket::handle);

        PayloadTypeRegistry.playC2S().register(DepositMoneyPacket.ID, DepositMoneyPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DepositMoneyPacket.ID, DepositMoneyPacket::handle);

        PayloadTypeRegistry.playC2S().register(WithdrawMoneyPacket.ID, WithdrawMoneyPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WithdrawMoneyPacket.ID, WithdrawMoneyPacket::handle);

        PayloadTypeRegistry.playC2S().register(TransferMoneyPacket.ID, TransferMoneyPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TransferMoneyPacket.ID, TransferMoneyPacket::handle);

        PayloadTypeRegistry.playC2S().register(ListMarketItemPacket.ID, ListMarketItemPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ListMarketItemPacket.ID, ListMarketItemPacket::handle);

        PayloadTypeRegistry.playC2S().register(PurchaseMarketItemPacket.ID, PurchaseMarketItemPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PurchaseMarketItemPacket.ID, PurchaseMarketItemPacket::handle);

        PayloadTypeRegistry.playC2S().register(DelistMarketItemPacket.ID, DelistMarketItemPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DelistMarketItemPacket.ID, DelistMarketItemPacket::handle);

        PayloadTypeRegistry.playC2S().register(ClaimReturnedItemsPacket.ID, ClaimReturnedItemsPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClaimReturnedItemsPacket.ID, ClaimReturnedItemsPacket::handle);

        PayloadTypeRegistry.playC2S().register(UpdateCommonConfigPacket.ID, UpdateCommonConfigPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UpdateCommonConfigPacket.ID, UpdateCommonConfigPacket::handle);

        // ---- 服务端 -> 客户端 ----
        PayloadTypeRegistry.playS2C().register(SyncPlayerBalancePacket.ID, SyncPlayerBalancePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncLeaderboardPacket.ID, SyncLeaderboardPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncMarketListingsPacket.ID, SyncMarketListingsPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncAdminStatusPacket.ID, SyncAdminStatusPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(MarketListingAddedPacket.ID, MarketListingAddedPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(MarketListingRemovedPacket.ID, MarketListingRemovedPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ActionFeedbackPacket.ID, ActionFeedbackPacket.CODEC);
    }

    /** 发给单个玩家 */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    /** 广播给全部在线玩家 */
    public static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
