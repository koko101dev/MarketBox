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
    /** 面额列表的长度上限：实际只有 8 种，留一倍余量 */
    private static final int MAX_DENOMINATIONS = 16;
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
    private final java.util.List<Double> enabledDenominations;

    public UpdateCommonConfigPacket(double interestRate, int interestIntervalMinutes,
                                    double marketFeePercent, int maxListings,
                                    double maxListingPrice, double maxTransactionAmount,
                                    boolean transferEnabled,
                                    java.util.List<Double> enabledDenominations) {
        this.interestRate = interestRate;
        this.interestIntervalMinutes = interestIntervalMinutes;
        this.marketFeePercent = marketFeePercent;
        this.maxListings = maxListings;
        this.maxListingPrice = maxListingPrice;
        this.maxTransactionAmount = maxTransactionAmount;
        this.transferEnabled = transferEnabled;
        this.enabledDenominations = enabledDenominations;
    }

    public UpdateCommonConfigPacket(RegistryFriendlyByteBuf buf) {
        this.interestRate = buf.readDouble();
        this.interestIntervalMinutes = buf.readInt();
        this.marketFeePercent = buf.readDouble();
        this.maxListings = buf.readVarInt();
        this.maxListingPrice = buf.readDouble();
        this.maxTransactionAmount = buf.readDouble();
        this.transferEnabled = buf.readBoolean();
        int denomCount = buf.readVarInt();
        // 长度前缀来自客户端，且解码早于权限校验：不设上限会被 5 字节的包打成 OOM
        if (denomCount < 0 || denomCount > MAX_DENOMINATIONS) {
            throw new io.netty.handler.codec.DecoderException("invalid denomination count: " + denomCount);
        }
        this.enabledDenominations = new java.util.ArrayList<>(denomCount);
        for (int i = 0; i < denomCount; i++) {
            this.enabledDenominations.add(buf.readDouble());
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(interestRate);
        buf.writeInt(interestIntervalMinutes);
        buf.writeDouble(marketFeePercent);
        buf.writeVarInt(maxListings);
        buf.writeDouble(maxListingPrice);
        buf.writeDouble(maxTransactionAmount);
        buf.writeBoolean(transferEnabled);
        java.util.List<Double> denoms = enabledDenominations == null
                ? java.util.List.of() : enabledDenominations;
        buf.writeVarInt(denoms.size());
        for (double d : denoms) {
            buf.writeDouble(d);
        }
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
                // 上限一年：没有上界时 *60 换算成秒会 int 溢出，周期退化成 60 秒
                int safeInterval = Math.max(1, Math.min(Config.MAX_INTERVAL_MINUTES, payload.interestIntervalMinutes));
                double safeFee = Math.max(0.0, Math.min(50.0, payload.marketFeePercent));
                int safeListings = Math.max(1, Math.min(200, payload.maxListings));
                double safePrice = Math.max(1, Math.min(1_000_000_000, payload.maxListingPrice));
                double safeTx = Math.max(1, Math.min(1_000_000_000, payload.maxTransactionAmount));

                // 审计：经济参数变更写入服务器日志，事后可追溯
                MarketBox.LOGGER.info("[Audit] {} changed economy config: rate {} -> {}, interval {} -> {} min, fee {} -> {}%, listings {} -> {}, maxPrice {} -> {}, maxTx {} -> {}, transfer {}",
                        player.getName().getString(),
                        Config.getInterestRate(), safeRate,
                        Config.getInterestIntervalMinutes(), safeInterval,
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
                // 面额只接受本模组真实存在的值并去重：否则超长或非法列表会被写进配置文件，
                // 并随每次余额同步发给每个在线玩家，最终撞上网络包上限导致全服同步失效
                java.util.List<Double> safeDenoms = new java.util.ArrayList<>();
                if (payload.enabledDenominations != null) {
                    for (Double d : payload.enabledDenominations) {
                        if (d != null
                                && com.koko.marketbox.util.MoneyUtils.DENOMINATIONS.containsKey(d)
                                && !safeDenoms.contains(d)) {
                            safeDenoms.add(d);
                        }
                    }
                }
                if (safeDenoms.isEmpty()) {
                    // 空列表会让取款发不出钱，回落到全部面额
                    safeDenoms.addAll(com.koko.marketbox.util.MoneyUtils.DENOMINATIONS.keySet());
                }
                Config.setEnabledDenominations(safeDenoms);

                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    try {
                        PlayerEvents.syncPlayerBalance(p);
                    } catch (Exception e) {
                        MarketBox.LOGGER.error("Failed to sync balance after config update, player={}", p.getUUID(), e);
                    }
                }
                ServerEvents.markLeaderboardDirty();
                ModMessages.sendToPlayer(player,
                        ActionFeedbackPacket.success("gui.marketbox.config.saved_ok"));
            } catch (Exception e) {
                MarketBox.LOGGER.error("Failed to update common config", e);
            }
        });
    }
}
