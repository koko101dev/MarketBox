package com.koko.marketbox.networking.packet;

import com.koko.marketbox.Config;
import com.koko.marketbox.MarketBox;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 -> 服务端：修改服务端权威的经济与市场配置。
 * 仅服主级（权限 >= 4）可提交，数值一律在服务端裁剪后再落盘。
 */
public class UpdateCommonConfigPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UpdateCommonConfigPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("update_common_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateCommonConfigPacket> CODEC =
            StreamCodec.ofMember(UpdateCommonConfigPacket::write, UpdateCommonConfigPacket::new);

    private final double interestRate;
    private final int interestIntervalMinutes;
    private final double marketFeePercent;
    private final int maxListings;
    private final double maxListingPrice;
    private final double maxTransactionAmount;
    private final boolean transferEnabled;

    public UpdateCommonConfigPacket(double interestRate, int interestIntervalMinutes,
                                    double marketFeePercent, int maxListings,
                                    double maxListingPrice, double maxTransactionAmount,
                                    boolean transferEnabled) {
        this.interestRate = interestRate;
        this.interestIntervalMinutes = interestIntervalMinutes;
        this.marketFeePercent = marketFeePercent;
        this.maxListings = maxListings;
        this.maxListingPrice = maxListingPrice;
        this.maxTransactionAmount = maxTransactionAmount;
        this.transferEnabled = transferEnabled;
    }

    public UpdateCommonConfigPacket(RegistryFriendlyByteBuf buf) {
        this.interestRate = buf.readDouble();
        this.interestIntervalMinutes = buf.readInt();
        this.marketFeePercent = buf.readDouble();
        this.maxListings = buf.readVarInt();
        this.maxListingPrice = buf.readDouble();
        this.maxTransactionAmount = buf.readDouble();
        this.transferEnabled = buf.readBoolean();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(interestRate);
        buf.writeInt(interestIntervalMinutes);
        buf.writeDouble(marketFeePercent);
        buf.writeVarInt(maxListings);
        buf.writeDouble(maxListingPrice);
        buf.writeDouble(maxTransactionAmount);
        buf.writeBoolean(transferEnabled);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(UpdateCommonConfigPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            try {
                if (player == null || !player.hasPermissions(4) || player.getServer() == null) {
                    // 经济参数影响全服，普通管理员不应能改
                    if (player != null) {
                        ModMessages.sendToPlayer(player,
                                new ActionFeedbackPacket("gui.marketbox.config.need_owner"));
                    }
                    return;
                }
                if (!PacketRateLimiter.allow(player)) {
                    return;
                }

                // 服务端再次裁剪，防止客户端伪造越界值
                double safeRate = Math.max(0.0, Math.min(100.0, payload.interestRate));
                int safeInterval = Math.max(1, payload.interestIntervalMinutes);
                double safeFee = Math.max(0.0, Math.min(50.0, payload.marketFeePercent));
                int safeListings = Math.max(1, Math.min(200, payload.maxListings));
                double safePrice = Math.max(1, Math.min(1_000_000_000, payload.maxListingPrice));
                double safeTx = Math.max(1, Math.min(1_000_000_000, payload.maxTransactionAmount));

                // 审计：经济参数变更写入服务器日志，事后可追溯
                MarketBox.LOGGER.info("[Audit] {} changed economy config: rate {} -> {}, interval {} -> {} sec, fee {} -> {}%, listings {} -> {}, maxPrice {} -> {}, maxTx {} -> {}, transfer {}",
                        player.getName().getString(),
                        Config.getInterestRate(), safeRate,
                        Config.getInterestIntervalSeconds(), safeInterval,
                        Config.getMarketFeePercent(), safeFee,
                        Config.getMaxListingsPerPlayer(), safeListings,
                        Config.getMaxListingPrice(), safePrice,
                        Config.getMaxTransactionAmount(), safeTx,
                        payload.transferEnabled);

                Config.setInterestSettings(safeRate, safeInterval);
                Config.setMarketFeePercent(safeFee);
                Config.setMaxListingsPerPlayer(safeListings);
                Config.setMaxListingPrice(safePrice);
                Config.setMaxTransactionAmount(safeTx);
                Config.setTransferEnabled(payload.transferEnabled);

                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    try {
                        PlayerEvents.syncPlayerBalance(p);
                    } catch (Exception e) {
                        MarketBox.LOGGER.error("Failed to sync balance after config update, player={}", p.getUUID(), e);
                    }
                }
                ServerEvents.markLeaderboardDirty();
                ModMessages.sendToPlayer(player,
                        new ActionFeedbackPacket("gui.marketbox.config.saved_ok"));
            } catch (Exception e) {
                MarketBox.LOGGER.error("Failed to update common config", e);
            }
        });
    }
}
