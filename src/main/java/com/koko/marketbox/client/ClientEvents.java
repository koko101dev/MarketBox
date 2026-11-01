package com.koko.marketbox.client;

import com.koko.marketbox.Config;
import com.koko.marketbox.util.MoneyUtils;
import com.koko.marketbox.util.PriceManager;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 客户端提示框：在物品说明中追加市场价。
 */
public final class ClientEvents {
    private ClientEvents() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, tooltipType, lines) -> {
            if (!Config.isShowMarketPriceTooltip() || stack.isEmpty()) {
                return;
            }

            Double price = PriceManager.getPrice(stack);
            if (price == null) {
                return;
            }

            lines.add(Component.empty());
            lines.add(Component.translatable("tooltip.marketbox.market_price")
                    .append(Component.literal(": "))
                    .append(Component.literal(MoneyUtils.formatValue(price)))
                    .withStyle(ChatFormatting.GOLD));
        });
    }
}
