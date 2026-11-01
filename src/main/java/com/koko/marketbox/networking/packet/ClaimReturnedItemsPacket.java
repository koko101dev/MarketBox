package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.networking.ModMessages;
import com.koko.marketbox.networking.PacketRateLimiter;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端 -> 服务端：领取过期/退货物品。无载荷字段。
 */
public class ClaimReturnedItemsPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClaimReturnedItemsPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("claim_returned_items"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimReturnedItemsPacket> CODEC =
            StreamCodec.ofMember((buf, payload) -> {
                // 无字段
            }, buf -> new ClaimReturnedItemsPacket());

    public ClaimReturnedItemsPacket() {
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(ClaimReturnedItemsPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        context.server().execute(() -> {
            if (!PacketRateLimiter.allow(player)) {
                return;
            }
            if (player == null) {
                return;
            }

            MarketManager manager = MarketManager.get(player.serverLevel());
            List<ItemStack> items = manager.claimReturnedItems(player.getUUID());

            if (items.isEmpty()) {
                ModMessages.sendToPlayer(player, new ActionFeedbackPacket("gui.marketbox.market.error.no_returned_items"));
                return;
            }

            // 按物品类型合并通知，避免刷屏
            Map<String, ItemStack> combinedItems = new HashMap<>();
            boolean droppedAny = false;

            for (ItemStack stack : items) {
                String key = stack.getDescriptionId();
                if (combinedItems.containsKey(key)) {
                    combinedItems.get(key).grow(stack.getCount());
                } else {
                    combinedItems.put(key, stack.copy());
                }

                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                    droppedAny = true;
                }
            }

            player.containerMenu.broadcastChanges();
            PlayerEvents.syncPlayerBalance(player);

            String translationKey = droppedAny
                    ? "gui.marketbox.market.claim_success_dropped"
                    : "gui.marketbox.market.claim_success";

            for (ItemStack stack : combinedItems.values()) {
                MutableComponent countComponent = Component.literal(String.valueOf(stack.getCount()))
                        .withStyle(ChatFormatting.BLUE);
                player.sendSystemMessage(Component.translatable(translationKey, stack.getHoverName(), countComponent));
            }
        });
    }
}
