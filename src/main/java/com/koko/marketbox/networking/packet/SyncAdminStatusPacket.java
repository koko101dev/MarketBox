package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：告知当前玩家是否具备管理员权限。
 * 客户端据此决定设置界面里金钱相关项是否显示；
 * 真正的权限校验仍在服务端（UpdateCommonConfigPacket / 管理员操作包）二次执行。
 */
public class SyncAdminStatusPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncAdminStatusPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("sync_admin_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAdminStatusPacket> CODEC =
            StreamCodec.ofMember(SyncAdminStatusPacket::write, SyncAdminStatusPacket::new);

    /** 玩家的权限等级：0 普通玩家，1~2 管理员，4 服主 */
    private final int permissionLevel;

    public SyncAdminStatusPacket(int permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    public SyncAdminStatusPacket(RegistryFriendlyByteBuf buf) {
        this.permissionLevel = buf.readVarInt();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(permissionLevel);
    }

    public int permissionLevel() {
        return permissionLevel;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
