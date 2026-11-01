package com.koko.marketbox.capabilities;

import com.koko.marketbox.MarketBox;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家余额的附加数据（替代 Forge Capability）。
 * 使用 Fabric 官方 AttachmentRegistry：随实体持久化，并在死亡后复制，
 * 语义等价于原 Capability + PlayerEvent.Clone 的组合。
 */
public final class PlayerBalanceAttachment {
    public static final AttachmentType<PlayerBalance> BALANCE = AttachmentRegistry.create(
            MarketBox.id("balance"),
            builder -> builder
                    .initializer(PlayerBalance::new)
                    // 复用已有的 NBT 读写逻辑，避免重复实现序列化
                    .persistent(CompoundTag.CODEC.xmap(PlayerBalance::fromTag, PlayerBalance::toTag))
                    .copyOnDeath());

    private PlayerBalanceAttachment() {
    }

    /** 获取玩家余额，不存在时按初始化器创建 */
    public static PlayerBalance get(Player player) {
        return player.getAttachedOrCreate(BALANCE);
    }
}
