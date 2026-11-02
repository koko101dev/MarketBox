package com.koko.marketbox.util;

/**
 * 银行页的纯计算逻辑，与界面分离，便于测试。
 *
 * 放在 util 包而非 client 包：GameTest 在服务端环境运行，
 * 引用 client 包会连带加载 Minecraft 客户端类而失败。
 */
public final class BankMath {

    private BankMath() {
    }

    /**
     * 「全部」按钮该填多少。
     *
     * 存入时填背包里的现金——此时银行余额还没有这笔钱；
     * 取出时填银行余额。两者混用会导致存入显示 0。
     */
    public static double quickAllAmount(boolean deposit, double balance, double inventoryCash) {
        double value = deposit ? inventoryCash : balance;
        return Math.max(0, value);
    }
}
