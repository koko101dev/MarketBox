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

    /**
     * 物品到面额的反向索引。
     * 统计背包金额时每格都要判断是不是货币，遍历 DENOMINATIONS 是 8 次比较，
     * 走这张表只要 1 次查找。该方法每 2 秒对每个在线玩家调用一次。
     */
    private static final Map<net.minecraft.world.item.Item, Double> BY_ITEM = new java.util.HashMap<>();
    
    static {
        // 反向索引在下面统一填充
        DENOMINATIONS.put(100.0, ModItems.MONEY_100);
        DENOMINATIONS.put(50.0, ModItems.MONEY_50);
        DENOMINATIONS.put(20.0, ModItems.MONEY_20);
        DENOMINATIONS.put(10.0, ModItems.MONEY_10);
        DENOMINATIONS.put(5.0, ModItems.MONEY_5);
        DENOMINATIONS.put(1.0, ModItems.MONEY_1);
        DENOMINATIONS.put(0.1, ModItems.MONEY_0_1);
        DENOMINATIONS.put(0.01, ModItems.MONEY_0_01);
        DENOMINATIONS.forEach((value, item) -> BY_ITEM.put(item, value));
    }

    /**
     * 将金额分解为最少数量的货币面值组合
     */
    /**
     * 当前启用的面额（面额 -> 物品）。
     * 发放货币时只看这张表：服主禁用某个面额后，取款与找零不再产生它，
     * 但 {@link #DENOMINATIONS} 仍保留全部面额，玩家手里的旧货币照常可用。
     */
    public static Map<net.minecraft.world.item.Item, Double> activeByItem() {
        java.util.List<Double> enabled = com.koko.marketbox.Config.getEnabledDenominations();
        Map<net.minecraft.world.item.Item, Double> map = new java.util.LinkedHashMap<>();
        for (Map.Entry<Double, net.minecraft.world.item.Item> entry : DENOMINATIONS.entrySet()) {
            if (enabled.contains(entry.getKey())) {
                map.put(entry.getValue(), entry.getKey());
            }
        }
        // 极端情况：全部禁用时退回完整列表，否则取款发不出钱
        if (map.isEmpty()) {
            DENOMINATIONS.forEach((value, item) -> map.put(item, value));
        }
        return map;
    }

    /** 启用的面额，按面值从大到小 */
    public static java.util.List<Double> activeValues() {
        java.util.List<Double> values = new java.util.ArrayList<>();
        for (Map.Entry<Double, net.minecraft.world.item.Item> entry : DENOMINATIONS.entrySet()) {
            if (com.koko.marketbox.Config.getEnabledDenominations().contains(entry.getKey())) {
                values.add(entry.getKey());
            }
        }
        if (values.isEmpty()) {
            values.addAll(DENOMINATIONS.keySet());
        }
        return values;
    }

    /**
     * 当前启用的面额能否精确凑出该金额。
     *
     * 禁用 0.01 元之后，最小单位变成 0.1 元，0.15 这类金额就凑不出来了。
     * 取款前必须检查：否则会先扣余额、再发出不足额的物品，玩家凭空丢钱。
     */
    public static boolean canMakeExact(double amount) {
        long cents = Math.round(amount * 100);
        if (cents <= 0) {
            return false;
        }
        long minCent = Long.MAX_VALUE;
        for (double value : activeValues()) {
            minCent = Math.min(minCent, Math.round(value * 100));
        }
        return minCent > 0 && cents % minCent == 0;
    }

    /** 当前启用的最小面额，用于提示玩家可调整的粒度 */
    public static double minEnabledDenomination() {
        double min = Double.MAX_VALUE;
        for (double value : activeValues()) {
            min = Math.min(min, value);
        }
        return min == Double.MAX_VALUE ? 0.01 : min;
    }

    public static Map<Double, Integer> calculateItemBreakdown(double amount) {
        Map<Double, Integer> breakdown = new HashMap<>();
        if (amount <= 0 || !Double.isFinite(amount)) {
            return breakdown;
        }
        // 使用 long 以避免浮点数精度问题 (以分位单位)
        long remainingCents = Math.round(amount * 100);

        for (double value : activeValues()) {
            long valueCents = Math.round(value * 100);
            if (valueCents <= 0) continue;
            if (remainingCents >= valueCents) {
                // 强转 int 前先夹住上限：金额极大时（例如取款 10 亿）直接转 int 会溢出成负数，
                // 随后 new ItemStack(item, 负数) 等于把整笔钱吞掉
                long count = remainingCents / valueCents;
                breakdown.put(value, (int) Math.min(Integer.MAX_VALUE, count));
                remainingCents %= valueCents;
            }
        }
        return breakdown;
    }

    /**
     * 按「剩余总价 / 剩余数量」结算一笔部分购买，返回这一笔应扣的分数。
     *
     * 向下取整，余数留到最后一笔（want == totalCount 时直接收掉剩余总价），
     * 保证分多次买完的累计金额恰好等于挂单标价。
     * 每次至少收 1 分会把 64 个卖 0.20 元的挂单分 64 次买成 0.64 元。
     */
    public static long prorateCostCents(long totalCents, int totalCount, int want) {
        if (totalCents <= 0 || totalCount <= 0 || want <= 0) {
            return 0;
        }
        if (want >= totalCount) {
            return totalCents;
        }
        return totalCents * want / totalCount;
    }

    /**
     * 估算背包能否完整装下这笔金额。
     *
     * 取款前必须检查：金额大到超过背包容量时，货币会溢出掉在地上，
     * 而按面额分解出的单个堆叠又可能远超堆叠上限，掉落后既捡不起来也会消失。
     */
    public static boolean canHoldAmount(Player player, double amount) {
        if (player == null || amount <= 0) return true;
        var inventory = player.getInventory();
        int freeSlots = 0;
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).isEmpty()) {
                freeSlots++;
            }
        }

        for (Map.Entry<Double, Integer> entry : calculateItemBreakdown(amount).entrySet()) {
            net.minecraft.world.item.Item item = DENOMINATIONS.get(entry.getKey());
            if (item == null) continue;
            int remaining = entry.getValue();
            int maxStack = Math.max(1, new ItemStack(item).getMaxStackSize());
            // 先合并进已有的同类堆叠，剩余部分才需要占用空槽
            for (int i = 0; i < 36 && remaining > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty() && stack.is(item)) {
                    remaining -= Math.min(remaining, Math.max(0, maxStack - stack.getCount()));
                }
            }
            long slotsNeeded = ((long) remaining + maxStack - 1) / maxStack;
            if (slotsNeeded > freeSlots) {
                return false;
            }
            freeSlots -= (int) slotsNeeded;
        }
        return true;
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

            Double value = BY_ITEM.get(stack.getItem());
            if (value != null) {
                total += value * stack.getCount();
            }
        }

        // 副手
        for (var hand : inventory.offhand) {
            if (hand.isEmpty()) continue;
            Double handValue = BY_ITEM.get(hand.getItem());
            if (handValue != null) {
                total += handValue * hand.getCount();
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
            // 找零凑不出来时整笔放弃：否则上面已经扣了大面额，下面按启用面额又发不出零头，
            // 差额会凭空消失（禁用 0.01 元后存 0.95 元、背包只有 1 元纸币就会触发）
            if (changeCents > 0 && !canMakeExact(changeCents / 100.0)) {
                return false;
            }
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
     * 给玩家发放指定金额的货币物品（按面值分解）。
     *
     * @return 是否全部装进背包；false 表示有部分掉在地上
     */
    public static boolean giveMoneyToInventory(Player player, double amount) {
        if (player == null || amount <= 0) return true;

        boolean allStored = true;
        Map<Double, Integer> breakdown = calculateItemBreakdown(amount);
        for (Map.Entry<Double, Integer> entry : breakdown.entrySet()) {
            double denom = entry.getKey();
            net.minecraft.world.item.Item item = DENOMINATIONS.get(denom);
            if (item == null) continue;

            int remaining = entry.getValue();
            // 按最大堆叠分批发放：直接 new ItemStack(item, 一千万) 会生成一个
            // 远超堆叠上限的实体，既捡不起来，也会在 5 分钟后消失
            int maxStack = Math.max(1, new ItemStack(item).getMaxStackSize());
            while (remaining > 0) {
                int batch = Math.min(remaining, maxStack);
                ItemStack stack = new ItemStack(item, batch);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                    allStored = false;
                }
                remaining -= batch;
            }
        }
        return allStored;
    }
}


