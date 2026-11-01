package com.koko.marketbox.market;

import java.util.UUID;

/**
 * 排行榜条目：纯数据，客户端与服务端共用。
 * 放在 market 包而非 client 包，避免专用服务器加载客户端类。
 *
 * balance 为总资产（余额 + 背包现金），其余三项用于分类排行榜。
 */
public record PlayerEntry(String name, UUID uuid, double balance,
                          double deposits, double income, double expense) {

    /** 兼容仅提供总资产的调用方 */
    public PlayerEntry(String name, UUID uuid, double balance) {
        this(name, uuid, balance, balance, 0, 0);
    }
}
