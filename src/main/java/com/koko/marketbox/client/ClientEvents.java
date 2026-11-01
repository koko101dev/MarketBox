package com.koko.marketbox.client;

import com.koko.marketbox.Config;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 客户端提示框：在物品说明中追加价格。
 *
 * 价格完全由市场决定：只显示玩家实际挂单的价格区间。
 * 没有任何挂单时不显示价格行——没有人在卖的东西，标一个参考价只会误导玩家。
 * 这样不需要维护任何静态物价数据，模组物品也能自动覆盖。
 */
public final class ClientEvents {

    private ClientEvents() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, tooltipType, lines) -> {
            if (!Config.isShowMarketPriceTooltip() || stack.isEmpty()) {
                return;
            }

            MarketStats stats = marketStats(stack);
            if (stats != null) {
                lines.add(Component.empty());
                String text = stats.count() == 1
                        ? MoneyUtils.formatValue(stats.lowest())
                        : MoneyUtils.formatValue(stats.lowest()) + " ~ " + MoneyUtils.formatValue(stats.highest());
                lines.add(Component.translatable("tooltip.marketbox.market_price")
                        .append(Component.literal(": " + text))
                        .withStyle(ChatFormatting.GOLD));
                lines.add(Component.translatable("tooltip.marketbox.listing_count",
                                String.valueOf(stats.count()))
                        .withStyle(ChatFormatting.DARK_GRAY));
                return;
            }

            // 没有挂单就不显示：没人卖的东西，标价也没有意义

        });
    }

    /** 市场上该物品的价格区间（按单价计算，与整单数量无关） */
    private static MarketStats marketStats(ItemStack stack) {
        List<MarketListing> listings = ClientData.getMarketListings();
        double lowest = -1;
        double highest = -1;
        int count = 0;

        for (MarketListing listing : listings) {
            if (!ItemStack.isSameItemSameComponents(listing.item(), stack)) {
                continue;
            }
            int stackCount = Math.max(1, listing.item().getCount());
            double unitPrice = listing.price() / stackCount;
            if (lowest < 0 || unitPrice < lowest) {
                lowest = unitPrice;
            }
            if (unitPrice > highest) {
                highest = unitPrice;
            }
            count++;
        }

        return count > 0 ? new MarketStats(lowest, highest, count) : null;
    }

    private record MarketStats(double lowest, double highest, int count) {
    }
}
