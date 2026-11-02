package com.koko.marketbox.gametest;

import com.koko.marketbox.capabilities.PlayerBalance;
import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import com.koko.marketbox.util.MoneyUtils;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * 服务端逻辑的自动化测试。
 *
 * 运行：./gradlew runGameTestServer
 * 这些测试不需要显卡，CI 里也能跑。覆盖的都是曾经出过问题的地方，
 * 防止改动时再次退化。
 */
public class MarketBoxGameTests implements FabricGameTest {

    /**
     * 每人限购必须随存档持久化。
     * 曾经 save() 漏写 perPlayerLimit，导致重启后所有限购回落为「不限」，
     * 限量商品可被单人一次扫光。
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void perPlayerLimitSurvivesSaveLoad(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        UUID listingId = UUID.randomUUID();
        manager.addListing(new MarketListing(
                listingId,
                new ItemStack(Items.STONE, 10),
                100.0,
                UUID.randomUUID(),
                "测试卖家",
                System.currentTimeMillis(),
                60,
                5));

        CompoundTag saved = manager.save(new CompoundTag(), helper.getLevel().registryAccess());
        MarketManager loaded = MarketManager.load(saved, helper.getLevel().registryAccess());

        List<MarketListing> listings = loaded.getListings();
        if (listings.size() != 1) {
            helper.fail("挂单数量应为 1，实际 " + listings.size());
            return;
        }
        int limit = listings.get(0).perPlayerLimit();
        if (limit != 5) {
            helper.fail("限购设置丢失：期望 5，实际 " + limit);
            return;
        }
        helper.succeed();
    }

    /**
     * 利息按「经过的完整周期数」结算，不足一个周期不发息。
     * 当前配置 0.05% / 1440 分钟，存 1 万满一天应得 5 元。
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void interestAccruesOncePerFullInterval(GameTestHelper helper) {
        PlayerBalance balance = new PlayerBalance();
        balance.setBalance(10_000);
        long oneDayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        balance.setLastInterestTime(oneDayAgo);

        double gained = balance.applyInterest(0.05, 1440 * 60);

        if (Math.abs(gained - 5.0) > 0.01) {
            helper.fail("满一天应得 5.00，实际 " + String.format("%.2f", gained));
            return;
        }
        helper.succeed();
    }

    /** 不足一个周期不应发息，且时间戳不能被推进 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void noInterestBeforeFirstInterval(GameTestHelper helper) {
        PlayerBalance balance = new PlayerBalance();
        balance.setBalance(10_000);
        long oneHourAgo = System.currentTimeMillis() - 60L * 60 * 1000;
        balance.setLastInterestTime(oneHourAgo);

        double gained = balance.applyInterest(0.05, 1440 * 60);

        if (gained > 0) {
            helper.fail("不满一个周期不应发息，实际发了 " + String.format("%.2f", gained));
            return;
        }
        helper.succeed();
    }

    /**
     * 金额进位边界。
     * 999999 曾经显示为「1000.00K」，因为除以 1000 得 999.999 后四舍五入成 1000。
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void moneyFormatCarriesProperly(GameTestHelper helper) {
        assertFormat(helper, 999, "999.00");
        assertFormat(helper, 1000, "1.00K");
        assertFormat(helper, 999_999, "1.00M");
        assertFormat(helper, 1_234_567, "1.23M");
        helper.succeed();
    }

    private void assertFormat(GameTestHelper helper, double value, String expected) {
        String actual = MoneyUtils.formatValue(value);
        if (!expected.equals(actual)) {
            helper.fail(String.format("%.0f 应格式化为 %s，实际 %s", value, expected, actual));
        }
    }

    /**
     * 部分购买后剩余挂单的数量与总价必须自洽。
     * 买家按「剩余总价 / 剩余数量」得到单价，任一环节算错都会导致后续买家多付或少付。
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void partialPurchaseKeepsPriceConsistent(GameTestHelper helper) {
        MarketListing original = new MarketListing(
                UUID.randomUUID(),
                new ItemStack(Items.STONE, 64),
                64.0,
                UUID.randomUUID(),
                "测试卖家",
                System.currentTimeMillis(),
                60,
                0);

        // 买走 32 个，扣 32 元，剩 32 个 32 元
        MarketListing remaining = original.withRemaining(32, 32.0);

        if (remaining.item().getCount() != 32) {
            helper.fail("剩余数量应为 32，实际 " + remaining.item().getCount());
            return;
        }
        if (Math.abs(remaining.price() - 32.0) > 0.001) {
            helper.fail("剩余总价应为 32.00，实际 " + remaining.price());
            return;
        }
        // 单价必须还是 1.00
        double unitPrice = remaining.price() / remaining.item().getCount();
        if (Math.abs(unitPrice - 1.0) > 0.001) {
            helper.fail("单价应为 1.00，实际 " + String.format("%.4f", unitPrice));
            return;
        }
        helper.succeed();
    }

    /**
     * 损坏的挂单数据不应导致整份市场存档加载失败。
     * 单条记录解析异常时跳过该条，其余照常读取。
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void corruptedListingIsSkipped(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        manager.addListing(new MarketListing(
                UUID.randomUUID(),
                new ItemStack(Items.STONE, 5),
                10.0,
                UUID.randomUUID(),
                "正常卖家",
                System.currentTimeMillis(),
                60,
                0));

        CompoundTag saved = manager.save(new CompoundTag(), helper.getLevel().registryAccess());
        // 插入一条物品数据损坏的挂单
        CompoundTag badListing = new CompoundTag();
        badListing.putUUID("id", UUID.randomUUID());
        badListing.put("item", new CompoundTag());
        badListing.putDouble("price", 99.0);
        badListing.putUUID("sellerUUID", UUID.randomUUID());
        badListing.putString("sellerName", "坏数据");
        badListing.putLong("createdAt", System.currentTimeMillis());
        badListing.putInt("expiryMinutes", 60);
        saved.getList("listings", net.minecraft.nbt.Tag.TAG_COMPOUND).add(badListing);

        MarketManager loaded = MarketManager.load(saved, helper.getLevel().registryAccess());
        List<MarketListing> listings = loaded.getListings();

        if (listings.size() != 1) {
            helper.fail("应只加载 1 条正常挂单（坏数据被跳过），实际 " + listings.size());
            return;
        }
        helper.succeed();
    }

    /**
     * 银行页「全部」按钮的取值规则。
     *
     * 存入时该填背包里的现金，取出时该填银行余额。
     * 重写银行界面时这两者一度混用，导致手上有现金点「全部」显示 0。
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
    public void bankQuickAllAmount(GameTestHelper helper) {
        // 手上有 6400 现金、银行余额 0：存入的全额应是 6400
        double depositAll = com.koko.marketbox.util.BankMath.quickAllAmount(true, 0, 6400);
        if (Math.abs(depositAll - 6400) > 0.001) {
            helper.fail("存入全部应为 6400，实际 " + depositAll);
            return;
        }
        // 同一状态下取出的全额应是余额 0
        double withdrawAll = com.koko.marketbox.util.BankMath.quickAllAmount(false, 0, 6400);
        if (Math.abs(withdrawAll) > 0.001) {
            helper.fail("取出全部应为 0，实际 " + withdrawAll);
            return;
        }
        // 反向：余额 500、现金 0，取出全额 500
        if (Math.abs(com.koko.marketbox.util.BankMath.quickAllAmount(false, 500, 0) - 500) > 0.001) {
            helper.fail("取出全部应取银行余额");
            return;
        }
        // 负值不应外泄
        if (com.koko.marketbox.util.BankMath.quickAllAmount(true, 0, -10) < 0) {
            helper.fail("金额不应为负");
            return;
        }
        System.out.println("[Stress] 银行全部按钮取值正确：存入=" + depositAll + " 取出=" + withdrawAll);
        helper.succeed();
    }
}
