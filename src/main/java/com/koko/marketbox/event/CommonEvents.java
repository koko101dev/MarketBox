package com.koko.marketbox.event;

import com.koko.marketbox.capabilities.PlayerBalanceAttachment;

import java.util.Objects;

/**
 * 通用初始化：触发玩家余额 Attachment 的静态注册。
 * Fabric 的 AttachmentRegistry 在类初始化时完成注册，因此必须确保本类被加载。
 */
public final class CommonEvents {
    private CommonEvents() {
    }

    public static void register() {
        Objects.requireNonNull(PlayerBalanceAttachment.BALANCE, "PlayerBalance Attachment 注册失败");
    }
}
