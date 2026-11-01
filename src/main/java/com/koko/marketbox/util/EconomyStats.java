package com.koko.marketbox.util;

/**
 * 经济流水统计（仅内存，随服务器重启清零）。
 *
 * 目的：让服主能看到「货币是怎么变多的」，判断是否需要调整利率。
 * 只记录本会话的增减量，不追踪历史，避免额外的存档负担。
 */
public final class EconomyStats {

    private static final long START_TIME = System.currentTimeMillis();

    /** 利息发放（凭空造钱的最大来源） */
    private static double interestIssued = 0;
    /** 管理员发放（set/add 命令与面板） */
    private static double adminIssued = 0;
    /** 手续费销毁 */
    private static double feeDestroyed = 0;
    /** 手续费进入金库（不算消失，只是换了位置） */
    private static double feeToTreasury = 0;
    /** 金库拨款（金库 → 玩家，总量不变） */
    private static double treasuryGranted = 0;

    private EconomyStats() {
    }

    public static void addInterest(double amount) {
        if (amount > 0) {
            interestIssued += amount;
        }
    }

    public static void addAdminIssued(double amount) {
        if (amount > 0) {
            adminIssued += amount;
        }
    }

    public static void addFee(double amount, boolean toTreasury) {
        if (amount <= 0) {
            return;
        }
        if (toTreasury) {
            feeToTreasury += amount;
        } else {
            feeDestroyed += amount;
        }
    }

    public static void addTreasuryGrant(double amount) {
        if (amount > 0) {
            treasuryGranted += amount;
        }
    }

    /** 本会话内凭空新增的货币 = 利息 + 管理员发放 - 销毁 */
    public static double netInflation() {
        return interestIssued + adminIssued - feeDestroyed;
    }

    public static double getInterestIssued() {
        return interestIssued;
    }

    public static double getAdminIssued() {
        return adminIssued;
    }

    public static double getFeeDestroyed() {
        return feeDestroyed;
    }

    public static double getFeeToTreasury() {
        return feeToTreasury;
    }

    public static double getTreasuryGranted() {
        return treasuryGranted;
    }

    public static long getUptimeMillis() {
        return System.currentTimeMillis() - START_TIME;
    }
}
