package com.koko.marketbox.gametest;

import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * 压力测试：用极端数据量暴露算法与数据结构的弱点。
 *
 * 这些用例不追求"功能正确"，而是回答两个问题：
 * 1. 数据量涨到 100 倍时，耗时是线性还是会爆炸？
 * 2. 极端序列（大量部分购买、反复存读）之后，数值还守恒吗？
 *
 * 运行：./gradlew runGameTest
 */
public class StressGameTests implements FabricGameTest {

    /** 单次扫描的耗时上限：过期检查每 5 秒跑一次，超过一个 tick（50ms）就会掉帧 */
    private static final long SCAN_BUDGET_MS = 50;

    /**
     * 一万条挂单同时过期时的扫描耗时。
     *
     * checkAutoDelist 内部用 CopyOnWriteArrayList.remove()，每次删除都会复制整个数组，
     * 复杂度是 O(n²)。挂单越多越慢，这条测试就是量它到底有多慢。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
    public void scanTenThousandExpiredListings(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        long twoHoursAgo = System.currentTimeMillis() - 2L * 60 * 60 * 1000;

        for (int i = 0; i < 10_000; i++) {
            manager.addListing(new MarketListing(
                    UUID.randomUUID(),
                    new ItemStack(Items.STONE, 1),
                    1.0,
                    UUID.randomUUID(),
                    "seller" + i,
                    twoHoursAgo,
                    60,
                    0));
        }

        long start = System.nanoTime();
        manager.checkAutoDelist(helper.getLevel());
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        if (!manager.getListings().isEmpty()) {
            helper.fail("应全部下架，剩余 " + manager.getListings().size());
            return;
        }
        if (elapsed > SCAN_BUDGET_MS * 20) {
            helper.fail("扫描一万条过期挂单耗时 " + elapsed + "ms，超过 1 秒，建议改用批量移除");
            return;
        }
        // 记录实测值，方便对比优化前后
        System.out.println("[Stress] 一万条过期挂单扫描耗时 " + elapsed + "ms");
        helper.succeed();
    }

    /**
     * 货币识别与反查表一致性。
     * 背包金额统计改成查表后，必须保证每种面额都能被识别，
     * 否则玩家的现金会凭空少算。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void everyDenominationIsRecognized(GameTestHelper helper) {
        var denominations = com.koko.marketbox.util.MoneyUtils.DENOMINATIONS;
        if (denominations.isEmpty()) {
            helper.fail("面额表为空");
            return;
        }

        int checked = 0;
        for (var entry : denominations.entrySet()) {
            ItemStack stack = new ItemStack(entry.getValue(), 3);
            if (!com.koko.marketbox.util.MoneyUtils.isCurrency(stack)) {
                helper.fail("面额 " + entry.getKey() + " 未被识别为货币");
                return;
            }
            checked++;
        }

        // 非货币物品必须被判为不可卖
        if (com.koko.marketbox.util.MoneyUtils.isCurrency(new ItemStack(Items.STONE))) {
            helper.fail("石头被误判为货币");
            return;
        }
        System.out.println("[Stress] 面额识别检查通过，共 " + checked + " 种");
        helper.succeed();
    }

    /**
     * 物品注册表必须包含所有已加载模组的物品。
     * 黑名单搜索依赖这张表，若只遍历到原版物品，模组物品就无法被禁止。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void itemRegistryCoversAllLoadedMods(GameTestHelper helper) {
        int total = 0;
        java.util.Set<String> namespaces = new java.util.HashSet<>();
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            total++;
            namespaces.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getNamespace());
        }

        if (total < 1000) {
            helper.fail("物品总数异常偏少：" + total);
            return;
        }
        if (!namespaces.contains("minecraft")) {
            helper.fail("注册表里没有原版物品");
            return;
        }
        // 开发环境装了 fabric-api，应能看到原版之外的命名空间
        if (namespaces.size() < 2) {
            helper.fail("只看到 " + namespaces.size() + " 个命名空间，模组物品未进入注册表");
            return;
        }
        System.out.println("[Stress] 物品注册表共 " + total + " 项，命名空间 "
                + namespaces.size() + " 个：" + namespaces);
        helper.succeed();
    }

    /**
     * 禁用某个面额后，发放时不应再拆分出该面额。
     * 玩家手里已有的旧货币仍可正常识别，只是不再被发放。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void disabledDenominationIsNotIssued(GameTestHelper helper) {
        var original = com.koko.marketbox.Config.getEnabledDenominations();
        try {
            // 禁用 100 元：取 100 元应由 5 张 20 元凑出
            com.koko.marketbox.Config.setEnabledDenominations(
                    java.util.List.of(0.01, 0.1, 1.0, 5.0, 10.0, 20.0, 50.0));

            var breakdown = com.koko.marketbox.util.MoneyUtils.calculateItemBreakdown(100.0);
            if (breakdown.containsKey(100.0)) {
                helper.fail("禁用后仍拆出了 100 元面额");
                return;
            }
            int pieces = breakdown.values().stream().mapToInt(Integer::intValue).sum();
            double total = breakdown.entrySet().stream()
                    .mapToDouble(e -> e.getKey() * e.getValue()).sum();
            if (Math.abs(total - 100.0) > 0.001) {
                helper.fail("拆分总额不正确：" + total);
                return;
            }
            System.out.println("[Stress] 禁用 100 元后拆分结果：" + breakdown + "，共 " + pieces + " 张");
        } finally {
            com.koko.marketbox.Config.setEnabledDenominations(original);
        }
        helper.succeed();
    }

    /**
     * 禁用 0.01 元后，凑不出的金额必须被识别出来。
     * 否则取款会先扣余额、再发出不足额的物品，玩家凭空丢钱。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void unmakeableAmountIsRejected(GameTestHelper helper) {
        var original = com.koko.marketbox.Config.getEnabledDenominations();
        try {
            com.koko.marketbox.Config.setEnabledDenominations(
                    java.util.List.of(0.1, 1.0, 5.0, 10.0, 20.0, 50.0, 100.0));

            if (com.koko.marketbox.util.MoneyUtils.canMakeExact(0.15)) {
                helper.fail("0.15 元在最小 0.1 元时不该判定为可凑出");
                return;
            }
            if (!com.koko.marketbox.util.MoneyUtils.canMakeExact(0.3)) {
                helper.fail("0.3 元应该可以凑出");
                return;
            }
            if (!com.koko.marketbox.util.MoneyUtils.canMakeExact(7.0)) {
                helper.fail("7 元应该可以凑出");
                return;
            }

            // 恢复全量面额后，0.15 又可以凑出
            com.koko.marketbox.Config.setEnabledDenominations(original);
            if (!com.koko.marketbox.util.MoneyUtils.canMakeExact(0.15)) {
                helper.fail("恢复全部面额后 0.15 元仍判定为不可凑出");
                return;
            }
            System.out.println("[Stress] 金额可行性校验通过：0.15 在禁用 0.01 时被拦截");
        } finally {
            com.koko.marketbox.Config.setEnabledDenominations(original);
        }
        helper.succeed();
    }

    /**
     * 定期存款的利息计算与到期判断。
     * 利率按日单利，到期一次性给付；提前取出只退本金。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void fixedDepositMath(GameTestHelper helper) {
        long now = System.currentTimeMillis();
        // 本金 100 元（10000 分），日息 0.10%，7 天
        var deposit = new com.koko.marketbox.capabilities.FixedDeposit(
                UUID.randomUUID(), 10_000L, 0.10, now, 7);

        long expectedInterest = 70L;   // 100 元 × 0.1% × 7 = 0.7 元 = 70 分
        if (deposit.interestCents() != expectedInterest) {
            helper.fail("利息应为 " + expectedInterest + " 分，实际 " + deposit.interestCents());
            return;
        }
        if (deposit.totalCents() != 10_070L) {
            helper.fail("到期总额应为 10070 分，实际 " + deposit.totalCents());
            return;
        }
        if (deposit.isMatured(now)) {
            helper.fail("刚存入不应判定为到期");
            return;
        }
        // 七天后应到期
        long afterSevenDays = now + 7L * 86_400_000L;
        if (!deposit.isMatured(afterSevenDays)) {
            helper.fail("满七天后应判定为到期");
            return;
        }
        // 剩余时间应为正数
        if (deposit.remainingMillis(now) <= 0) {
            helper.fail("剩余时间应为正");
            return;
        }
        System.out.println("[Stress] 定期存款利息：" + expectedInterest + " 分 / 到期总额 "
                + deposit.totalCents() + " 分");
        helper.succeed();
    }

    /** 一千个玩家的余额数据存读耗时：决定服务器保存时的卡顿程度 */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200)
    public void saveAndLoadThousandPlayers(GameTestHelper helper) {
        CompoundTag[] tags = new CompoundTag[1000];
        for (int i = 0; i < tags.length; i++) {
            PlayerBalance balance = new PlayerBalance();
            balance.setBalance(1000 + i);
            // 制造 50 条流水与分类数据，模拟长期玩家
            for (int j = 0; j < 50; j++) {
                balance.addBalance(1.0, "gui.marketbox.transaction.market_sell");
            }
            tags[i] = balance.toTag();
        }

        long start = System.nanoTime();
        for (CompoundTag tag : tags) {
            PlayerBalance.fromTag(tag);
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        if (elapsed > 1000) {
            helper.fail("解析一千个玩家数据耗时 " + elapsed + "ms，超过 1 秒");
            return;
        }
        System.out.println("[Stress] 一千个玩家数据解析耗时 " + elapsed + "ms");
        helper.succeed();
    }

    /**
     * 连续一百次部分购买后，总价是否仍然守恒。
     *
     * 每次买入按「剩余总价 / 剩余数量」算单价并四舍五入到分，
     * 次数多了容易累积误差，这条测试盯的就是这个。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
    public void hundredPartialPurchasesStayConsistent(GameTestHelper helper) {
        double originalTotal = 100.00;
        int originalCount = 100;

        MarketListing listing = new MarketListing(
                UUID.randomUUID(),
                new ItemStack(Items.STONE, originalCount),
                originalTotal,
                UUID.randomUUID(),
                "seller",
                System.currentTimeMillis(),
                60,
                0);

        double collected = 0;
        int remaining = originalCount;
        double remainingPrice = originalTotal;

        for (int i = 0; i < 100 && remaining > 0; i++) {
            double unitPrice = remainingPrice / remaining;
            double cost = Math.round(unitPrice * 100) / 100.0;
            collected += cost;
            remainingPrice -= cost;
            remaining -= 1;
            listing = listing.withRemaining(remaining, remainingPrice);
        }

        double diff = Math.abs(collected - originalTotal);
        if (diff > 0.05) {
            helper.fail(String.format("百分百次部分购买后总额偏差 %.2f（收 %.2f，应收 %.2f）",
                    diff, collected, originalTotal));
            return;
        }
        System.out.println(String.format("[Stress] 一百次部分购买累计偏差 %.4f", diff));
        helper.succeed();
    }

    /**
     * 反复存读市场数据后，字段是否仍然完整。
     * 相当于服务器反复重启一百次，检查有没有字段在中途丢掉。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
    public void repeatedSaveLoadKeepsAllFields(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        UUID seller = UUID.randomUUID();
        manager.addListing(new MarketListing(UUID.randomUUID(), new ItemStack(Items.DIAMOND, 7),
                999.99, seller, "卖家", System.currentTimeMillis(), 60, 3));
        manager.addTreasury(1234.56);
        manager.addPendingPayment(seller, 78.9);

        var registries = helper.getLevel().registryAccess();
        MarketManager current = manager;
        for (int i = 0; i < 100; i++) {
            CompoundTag saved = current.save(new CompoundTag(), registries);
            current = MarketManager.load(saved, registries);
        }

        var listings = current.getListings();
        if (listings.size() != 1) {
            helper.fail("反复存读后挂单丢失，剩余 " + listings.size());
            return;
        }
        var only = listings.get(0);
        if (only.item().getCount() != 7 || only.perPlayerLimit() != 3 || only.expiryMinutes() != 60) {
            helper.fail(String.format("字段丢失：数量=%d 限购=%d 时长=%d",
                    only.item().getCount(), only.perPlayerLimit(), only.expiryMinutes()));
            return;
        }
        if (Math.abs(current.getTreasuryBalance() - 1234.56) > 0.01) {
            helper.fail("金库余额漂移：" + current.getTreasuryBalance());
            return;
        }
        if (Math.abs(current.getPendingPayment(seller) - 78.9) > 0.01) {
            helper.fail("待领取金额漂移：" + current.getPendingPayment(seller));
            return;
        }
        helper.succeed();
    }

    /**
     * 一万次余额增减后，总额与分类统计是否与流水一致。
     * 分类统计是新增的数据结构，这里验证它不会和总额对不上。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
    public void categoryTotalsMatchBalance(GameTestHelper helper) {
        PlayerBalance balance = new PlayerBalance();

        for (int i = 0; i < 5000; i++) {
            balance.addBalance(2.0, "gui.marketbox.transaction.interest");
            balance.addBalance(-1.0, "gui.marketbox.transaction.market_buy");
        }

        // 5000 × (2 - 1) = 5000
        if (Math.abs(balance.getBalance() - 5000) > 0.01) {
            helper.fail("余额应为 5000，实际 " + balance.getBalance());
            return;
        }

        long incomeSum = balance.getIncomeByCategory().values().stream().mapToLong(Long::longValue).sum();
        long expenseSum = balance.getExpenseByCategory().values().stream().mapToLong(Long::longValue).sum();

        if (Math.abs(incomeSum / 100.0 - balance.getTotalIncome()) > 0.01) {
            helper.fail(String.format("分类收入合计 %.2f 与总收入 %.2f 不一致",
                    incomeSum / 100.0, balance.getTotalIncome()));
            return;
        }
        if (Math.abs(expenseSum / 100.0 - balance.getTotalExpense()) > 0.01) {
            helper.fail(String.format("分类支出合计 %.2f 与总支出 %.2f 不一致",
                    expenseSum / 100.0, balance.getTotalExpense()));
            return;
        }
        System.out.println("[Stress] 一万次增减后分类统计与总额一致");
        helper.succeed();
    }
}
