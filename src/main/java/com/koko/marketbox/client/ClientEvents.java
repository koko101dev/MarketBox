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
            double dealPrice = ClientData.getDealPrice(stack);

            if (dealPrice > 0 || stats != null) {
                lines.add(Component.empty());
            }

            // 最近成交价最能反映真实价值
            if (dealPrice > 0) {
                lines.add(Component.translatable("tooltip.marketbox.deal_price")
                        .append(Component.literal(": " + MoneyUtils.formatValue(dealPrice)))
                        .withStyle(ChatFormatting.GOLD));
            }

            // 当前在售价（中位数，单条离谱挂单不影响）
            if (stats != null) {
                lines.add(Component.translatable("tooltip.marketbox.on_sale")
                        .append(Component.literal(": " + MoneyUtils.formatValue(stats.median())
                                + "（" + stats.count() + "）"))
                        .withStyle(ChatFormatting.GRAY));
            }

        });
    }

    /**
     * 市场上该物品的典型价格（按单价计算，与整单数量无关）。
     * 用中位数而非最低价：单条离谱挂单（1 块钱砸盘或 99999 钓鱼）不会污染显示。
     */
    private static MarketStats marketStats(ItemStack stack) {
        List<MarketListing> listings = ClientData.getMarketListings();
        List<Double> prices = new java.util.ArrayList<>();

        for (MarketListing listing : listings) {
            if (!ItemStack.isSameItemSameComponents(listing.item(), stack)) {
                continue;
            }
            int stackCount = Math.max(1, listing.item().getCount());
            prices.add(listing.price() / stackCount);
        }
        if (prices.isEmpty()) {
            return null;
        }

        java.util.Collections.sort(prices);
        int size = prices.size();
        double median = size % 2 == 1
                ? prices.get(size / 2)
                : (prices.get(size / 2 - 1) + prices.get(size / 2)) / 2;

        return new MarketStats(median, prices.get(0), prices.get(size - 1), size);
    }

    private record MarketStats(double median, double lowest, double highest, int count) {
    }
}
