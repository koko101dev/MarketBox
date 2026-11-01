package com.koko.marketbox.client;

import net.minecraft.network.chat.Component;

/**
 * 能接收操作反馈的界面。
 *
 * 服务端的 ActionFeedbackPacket 原先靠 instanceof 逐个判断界面类型，
 * 新界面忘记注册就收不到提示。改为实现本接口即可自动接上。
 */
public interface FeedbackReceiver {
    /** 显示一条操作结果；isError 决定提示条的颜色与图标 */
    void showFeedback(Component message, boolean isError);
}
