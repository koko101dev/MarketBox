package com.koko.marketbox.market;

import java.util.UUID;

/**
 * 排行榜条目：纯数据，客户端与服务端共用。
 * 放在 market 包而非 client 包，避免专用服务器加载客户端类。
 */
public record PlayerEntry(String name, UUID uuid, double balance) {
}
