package com.koko.marketbox.util;

import com.koko.marketbox.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for money calculations and formatting.
 */
public class MoneyUtils {

    /**
     * 定义所有面值及其对应的物品，按从大到小排序
     */
    public static final Map<Double, net.minecraft.world.item.Item> DENOMINATIONS = new java.util.LinkedHashMap<>();
    
    static {
        DENOMINATIONS.put(100.0, ModItems.MONEY_100);
        DENOMINATIONS.put(50.0, ModItems.MONEY_50);
        DENOMINATIONS.put(20.0, ModItems.MONEY_20);
        DENOMINATIONS.put(10.0, ModItems.MONEY_10);
        DENOMINATIONS.put(5.0, ModItems.MONEY_5);
        DENOMINATIONS.put(1.0, ModItems.MONEY_1);
        DENOMINATIONS.put(0.1, ModItems.MONEY_0_1);
        DENOMINATIONS.put(0.01, ModItems.MONEY_0_01);
    }

    /**
     * 将金额分解为最少数量的货币面值组合
     */
    public static Map<Double, Integer> calculateItemBreakdown(double amount) {
        Map<Double, Integer> breakdown = new HashMap<>();
        // 使用 long 以避免浮点数精度问题 (以分位单位)
        long remainingCents = Math.round(amount * 100);

        for (double value : DENOMINATIONS.keySet()) {
            long valueCents = Math.round(value * 100);
            if (remainingCents >= valueCents) {
                int count = (int) (remainingCents / valueCents);
                breakdown.put(value, count);
                remainingCents %= valueCents;
            }
        }
        return breakdown;
    }

    /**
     * Formats large numbers into a human-readable string (e.g., 1.5K, 2.3M)
     */
    public static String formatValue(double value) {
        if (value < 1000) {
            return String.format("%.2f", value);
        }
        
        String[] units = {"", "K", "M", "B", "T", "P", "E"};
        int unitIndex = 0;
        double formattedValue = value;
        
        // 用 999.995 而不是 1000 做阈值：999999 除以 1000 是 999.999，
        // 按 %.2f 会四舍五入成 "1000.00K"，应该进位到 "1.00M"
        while (Math.abs(formattedValue) >= 999.995 && unitIndex < units.length - 1) {
            formattedValue /= 1000;
            unitIndex++;
        }

        return String.format("%.2f%s", formattedValue, units[unitIndex]);
    }

    /**
     * Calculates the total amount of money items in the player's inventory.
     */
    public static double getInventoryMoneyTotal(Player player) {
        if (player == null) return 0;
        
        double total = 0;
        var inventory = player.getInventory();
        // 主背包：getContainerSize() 返回 41，但 36 之后的索引取不到盔甲/副手，
        // 因此副手单独遍历，避免放在副手的货币不计入总资产
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            for (Map.Entry<Double, net.minecraft.world.item.Item> entry : DENOMINATIONS.entrySet()) {
                if (stack.is(entry.getValue())) {
                    total += entry.getKey() * stack.getCount();
                    break;
                }
            }
        }

        // 副手
        for (var hand : inventory.offhand) {
            if (hand.isEmpty()) continue;
            for (Map.Entry<Double, net.minecraft.world.item.Item> entry : DENOMINATIONS.entrySet()) {
                if (hand.is(entry.getValue())) {
                    total += entry.getKey() * hand.getCount();
                    break;
                }
            }
        }
        return total;
    }

    /**
     * Checks if the given item stack is a currency item from this mod.
     */
    public static boolean isCurrency(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (net.minecraft.world.item.Item item : DENOMINATIONS.values()) {
            if (stack.is(item)) return true;
        }
        return false;
    }

    /**
     * 从玩家背包中扣除指定金额的货币物品（支持自动找零）
     * @return 如果成功扣除则返回 true，否则返回 false
     */
    public static boolean takeMoneyFromInventory(Player player, double amount) {
        if (player == null || amount <= 0) return false;

        // 使用分作为单位进行计算，避免精度问题
        long targetCents = Math.round(amount * 100);

        // 1. 获取玩家背包中所有面值的数量
        Map<Double, Integer> availableCounts = new HashMap<>();
        for (double denom : DENOMINATIONS.keySet()) {
            int count = 0;
            net.minecraft.world.item.Item item = DENOMINATIONS.get(denom);
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(item)) {
                    count += stack.getCount();
                }
            }
            availableCounts.put(denom, count);
        }

        // 2. 尝试使用贪心策略寻找能够凑齐 amount 的组合（优先使用大面值）
        Map<Double, Integer> toRemove = new HashMap<>();
        long remainingCents = targetCents;
        for (double denom : DENOMINATIONS.keySet()) {
            if (remainingCents <= 0) break;
            long denomCents = Math.round(denom * 100);
            int available = availableCounts.getOrDefault(denom, 0);
            int take = (int) Math.min(remainingCents / denomCents, available);
            if (take > 0) {
                toRemove.put(denom, take);
                remainingCents -= (long) take * denomCents;
            }
        }

        // 3. 如果还有剩余，说明无法精确凑齐，尝试拿一个稍大的面值进行找零
        long changeCents = 0;
        if (remainingCents > 0) {
            Double bestDenom = null;
            java.util.List<Double> sortedDenoms = new java.util.ArrayList<>(DENOMINATIONS.keySet());
            java.util.Collections.sort(sortedDenoms);
            
            for (double denom : sortedDenoms) {
                long denomCents = Math.round(denom * 100);
                int alreadyTaken = toRemove.getOrDefault(denom, 0);
                if (denomCents >= remainingCents && (availableCounts.getOrDefault(denom, 0) - alreadyTaken) > 0) {
                    bestDenom = denom;
                    break;
                }
            }

            if (bestDenom != null) {
                toRemove.put(bestDenom, toRemove.getOrDefault(bestDenom, 0) + 1);
                changeCents = Math.round(bestDenom * 100) - remainingCents;
                remainingCents = 0;
            }
        }

        // 4. 如果成功凑齐
        if (remainingCents == 0) {
            // 执行扣除
            for (Map.Entry<Double, Integer> entry : toRemove.entrySet()) {
                int countToRemove = entry.getValue();
                net.minecraft.world.item.Item item = DENOMINATIONS.get(entry.getKey());
                for (int i = 0; i < player.getInventory().getContainerSize() && countToRemove > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.isEmpty() && stack.is(item)) {
                        int take = Math.min(stack.getCount(), countToRemove);
                        stack.shrink(take);
                        countToRemove -= take;
                    }
                }
            }

            // 找零
            if (changeCents > 0) {
                giveMoneyToInventory(player, changeCents / 100.0);
            }
            return true;
        }

        return false;
    }

    /**
     * 给玩家发放指定金额的货币物品（按面值分解）
     */
    public static void giveMoneyToInventory(Player player, double amount) {
        if (player == null || amount <= 0) return;
        
        Map<Double, Integer> breakdown = calculateItemBreakdown(amount);
        for (Map.Entry<Double, Integer> entry : breakdown.entrySet()) {
            double denom = entry.getKey();
            int count = entry.getValue();
            net.minecraft.world.item.Item item = DENOMINATIONS.get(denom);
            if (item != null) {
                ItemStack stack = new ItemStack(item, count);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
            }
        }
    }
}


