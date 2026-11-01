package com.koko.marketbox.gametest;

import com.koko.marketbox.market.MarketListing;
import com.koko.marketbox.market.MarketManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/**
 * 市场数据层的行为测试。
 *
 * 运行：./gradlew runGameTest
 * 覆盖挂单生命周期、金库、待领取、退货、成交价与购买记录。
 */
public class MarketDataGameTests implements FabricGameTest {

    private static MarketListing listing(int count, double totalPrice, long createdAt,
                                          int expiryMinutes, int perPlayerLimit) {
        return new MarketListing(
                UUID.randomUUID(),
                new ItemStack(Items.STONE, count),
                totalPrice,
                UUID.randomUUID(),
                "测试卖家",
                createdAt,
                expiryMinutes,
                perPlayerLimit);
    }

    /** 过期挂单应自动下架，并把原物退还给卖家 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void expiredListingReturnsItem(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        UUID seller = UUID.randomUUID();
        long twoHoursAgo = System.currentTimeMillis() - 2L * 60 * 60 * 1000;

        MarketListing expired = new MarketListing(
                UUID.randomUUID(),
                new ItemStack(Items.STONE, 8),
                80.0,
                seller,
                "卖家",
                twoHoursAgo,
                60,
                0);
        manager.addListing(expired);

        manager.checkAutoDelist(helper.getLevel());

        if (!manager.getListings().isEmpty()) {
            helper.fail("过期挂单应被下架，实际仍有 " + manager.getListings().size() + " 条");
            return;
        }
        List<ItemStack> returned = manager.getReturnedItems(seller);
        if (returned.size() != 1 || returned.get(0).getCount() != 8) {
            helper.fail("应退还 8 个物品，实际 " + (returned.isEmpty() ? "无" : returned.get(0).getCount()));
            return;
        }
        helper.succeed();
    }

    /** 未过期的挂单不能被误删 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void activeListingSurvivesScan(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        manager.addListing(listing(5, 50.0, System.currentTimeMillis(), 60, 0));

        manager.checkAutoDelist(helper.getLevel());

        if (manager.getListings().size() != 1) {
            helper.fail("未过期挂单被误删，剩余 " + manager.getListings().size() + " 条");
            return;
        }
        helper.succeed();
    }

    /** 金库余额不能透支 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void treasuryCannotOverdraw(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        manager.addTreasury(100.0);

        double first = manager.withdrawTreasury(60.0);
        double second = manager.withdrawTreasury(60.0);

        if (Math.abs(first - 60.0) > 0.001) {
            helper.fail("首次应取出 60.00，实际 " + first);
            return;
        }
        if (second != 0) {
            helper.fail("余额不足时应返回 0，实际 " + second);
            return;
        }
        if (Math.abs(manager.getTreasuryBalance() - 40.0) > 0.001) {
            helper.fail("金库余额应为 40.00，实际 " + manager.getTreasuryBalance());
            return;
        }
        helper.succeed();
    }

    /** 待领取付款：可累加，领取后归零 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void pendingPaymentAccumulatesAndClears(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        UUID player = UUID.randomUUID();

        manager.addPendingPayment(player, 12.5);
        manager.addPendingPayment(player, 7.5);

        if (Math.abs(manager.getPendingPayment(player) - 20.0) > 0.001) {
            helper.fail("累加后应为 20.00，实际 " + manager.getPendingPayment(player));
            return;
        }
        double claimed = manager.claimPendingPayment(player);
        if (Math.abs(claimed - 20.0) > 0.001) {
            helper.fail("领取金额应为 20.00，实际 " + claimed);
            return;
        }
        if (manager.getPendingPayment(player) != 0) {
            helper.fail("领取后应清零，实际 " + manager.getPendingPayment(player));
            return;
        }
        helper.succeed();
    }

    /** 退货物品：领取后清空，且返回的是副本 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void returnedItemsClaimedOnce(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        UUID player = UUID.randomUUID();

        manager.addReturnedItem(player, new ItemStack(Items.DIRT, 3));
        manager.addReturnedItem(player, new ItemStack(Items.STICK, 2));

        List<ItemStack> before = manager.getReturnedItems(player);
        if (before.size() != 2) {
            helper.fail("应有 2 件退货物品，实际 " + before.size());
            return;
        }

        List<ItemStack> claimed = manager.claimReturnedItems(player);
        if (claimed.size() != 2) {
            helper.fail("领取应有 2 件，实际 " + claimed.size());
            return;
        }
        if (!manager.getReturnedItems(player).isEmpty()) {
            helper.fail("领取后应清空");
            return;
        }
        helper.succeed();
    }

    /** 成交价按物品 ID 记录，与自定义名无关 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void dealPriceRecordedPerItem(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 10);

        if (manager.getDealPrice(stack) != -1) {
            helper.fail("未成交时应返回 -1");
            return;
        }
        manager.recordDeal(stack, 2.5);
        if (Math.abs(manager.getDealPrice(stack) - 2.5) > 0.001) {
            helper.fail("成交价应为 2.50，实际 " + manager.getDealPrice(stack));
            return;
        }
        // 同样物品不同数量应共享同一成交价
        if (Math.abs(manager.getDealPrice(new ItemStack(Items.IRON_INGOT, 1)) - 2.5) > 0.001) {
            helper.fail("同种物品应共享成交价");
            return;
        }
        helper.succeed();
    }

    /** 单人挂单上限按卖家计数 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void listingCountBySeller(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        UUID sellerA = UUID.randomUUID();
        UUID sellerB = UUID.randomUUID();
        long now = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            manager.addListing(new MarketListing(UUID.randomUUID(), new ItemStack(Items.STONE, 1),
                    10.0, sellerA, "A", now, 60, 0));
        }
        manager.addListing(new MarketListing(UUID.randomUUID(), new ItemStack(Items.STONE, 1),
                10.0, sellerB, "B", now, 60, 0));

        if (manager.countBySeller(sellerA) != 3) {
            helper.fail("A 应有 3 条挂单，实际 " + manager.countBySeller(sellerA));
            return;
        }
        if (manager.countBySeller(sellerB) != 1) {
            helper.fail("B 应有 1 条挂单，实际 " + manager.countBySeller(sellerB));
            return;
        }
        helper.succeed();
    }

    /** 购买记录可累加，挂单移除后自动清理 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void purchaseRecordsAccumulateAndClear(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        UUID listingId = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        manager.addPurchase(listingId, buyer, 2);
        manager.addPurchase(listingId, buyer, 3);

        if (manager.getPurchased(listingId, buyer) != 5) {
            helper.fail("购买记录应为 5，实际 " + manager.getPurchased(listingId, buyer));
            return;
        }
        manager.clearPurchases(listingId);
        if (manager.getPurchased(listingId, buyer) != 0) {
            helper.fail("清理后应为 0");
            return;
        }
        helper.succeed();
    }

    /** 部分购买到最后一个时，剩余价格不能为负 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void lastItemLeavesZeroPrice(GameTestHelper helper) {
        MarketListing original = listing(2, 10.0, System.currentTimeMillis(), 60, 0);
        MarketListing afterFirst = original.withRemaining(1, 5.0);

        if (afterFirst.item().getCount() != 1) {
            helper.fail("剩余数量应为 1");
            return;
        }
        if (Math.abs(afterFirst.price() - 5.0) > 0.001) {
            helper.fail("剩余价格应为 5.00，实际 " + afterFirst.price());
            return;
        }
        double unitPrice = afterFirst.price() / afterFirst.item().getCount();
        if (Math.abs(unitPrice - 5.0) > 0.001) {
            helper.fail("单价应为 5.00，实际 " + unitPrice);
            return;
        }
        helper.succeed();
    }

    /** 金库余额与挂单数据都要能存进存档再读回来 */
    @GameTest(template = EMPTY_STRUCTURE)
    public void treasurySurvivesSaveLoad(GameTestHelper helper) {
        MarketManager manager = new MarketManager();
        manager.addTreasury(123.45);
        UUID player = UUID.randomUUID();
        manager.addPendingPayment(player, 6.78);

        var saved = manager.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
        MarketManager loaded = MarketManager.load(saved, helper.getLevel().registryAccess());

        if (Math.abs(loaded.getTreasuryBalance() - 123.45) > 0.001) {
            helper.fail("金库余额应为 123.45，实际 " + loaded.getTreasuryBalance());
            return;
        }
        if (Math.abs(loaded.getPendingPayment(player) - 6.78) > 0.001) {
            helper.fail("待领取应为 6.78，实际 " + loaded.getPendingPayment(player));
            return;
        }
        helper.succeed();
    }
}
