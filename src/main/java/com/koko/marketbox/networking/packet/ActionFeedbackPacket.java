package com.koko.marketbox.networking.packet;

import com.koko.marketbox.MarketBox;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 服务端 -> 客户端：在界面中显示一条操作反馈。
 * 既用于错误提示，也用于成功提示，由 error 标记决定客户端显示红色还是绿色。
 * 客户端处理逻辑位于 client 包，避免服务端加载客户端类。
 */
public class ActionFeedbackPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ActionFeedbackPacket> ID =
            new CustomPacketPayload.Type<>(MarketBox.id("action_feedback"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionFeedbackPacket> CODEC =
            StreamCodec.ofMember(ActionFeedbackPacket::write, ActionFeedbackPacket::new);

    private final String messageKey;
    /** 格式化参数（如金额），空串表示无参数 */
    private final String arg;
    private final boolean error;

    /** 错误提示：兼容既有的单参调用 */
    /**
     * 单参数构造默认按「错误」处理：绝大多数的单参数调用都是失败提示，
     * 默认成功会导致真正的错误被显示成绿色。成功提示请用 {@link #success}。
     */
    public ActionFeedbackPacket(String messageKey) {
        this(messageKey, "", true);
    }

    /** 成功提示：绿色样式 */
    public static ActionFeedbackPacket success(String messageKey) {
        return new ActionFeedbackPacket(messageKey, "", false);
    }

    /** 带参数的成功提示 */
    public static ActionFeedbackPacket success(String messageKey, String arg) {
        return new ActionFeedbackPacket(messageKey, arg, false);
    }

    public ActionFeedbackPacket(String messageKey, String arg, boolean error) {
        this.messageKey = messageKey;
        this.arg = arg == null ? "" : arg;
        this.error = error;
    }

    public ActionFeedbackPacket(RegistryFriendlyByteBuf buf) {
        this.messageKey = buf.readUtf();
        this.arg = buf.readUtf();
        this.error = buf.readBoolean();
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(messageKey);
        buf.writeUtf(arg);
        buf.writeBoolean(error);
    }

    public String messageKey() {
        return messageKey;
    }

    public String arg() {
        return arg;
    }

    public boolean error() {
        return error;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
