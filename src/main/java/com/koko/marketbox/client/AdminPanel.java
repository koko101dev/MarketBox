package com.koko.marketbox.client;

import com.koko.marketbox.market.PlayerEntry;
import com.koko.marketbox.networking.packet.AdminBalancePacket;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 设置界面里的「管理员操作」区块：把 /marketbox set|add|reset 搬到 UI。
 * 仅管理员可见；服务端 AdminBalancePacket 会再次校验权限。
 */
final class AdminPanel {

    private static final int ROW_COUNT = 3;

    /** 目标玩家索引，跨 rebuildWidgets 保持 */
    private static int targetIndex = 0;
    private static EditBox amountBox;

    private AdminPanel() {
    }

    static int rowCount() {
        return ROW_COUNT;
    }

    /** 当前选中的目标；没有在线玩家时为 null */
    private static PlayerEntry currentTarget() {
        List<PlayerEntry> list = ClientData.getLeaderboard();
        if (list.isEmpty()) {
            return null;
        }
        if (targetIndex >= list.size()) {
            targetIndex = 0;
        }
        return list.get(targetIndex);
    }

    /**
     * 构建三行控件：目标玩家（循环切换）、金额输入、三个操作按钮。
     * addWidget 由调用方传入，因为 addRenderableWidget 是 protected。
     */
    static void build(ConfigScreen screen, int startY, int rowHeight, int controlHeight,
                      int controlX, int controlWidth, Consumer<AbstractWidget> addWidget) {
        int centerY = startY + rowHeight / 2 - controlHeight / 2;

        // 第 1 行：目标玩家，点击打开带搜索的玩家列表
        PlayerEntry target = currentTarget();
        String name = target != null ? target.name() : "—";
        addWidget.accept(new StyledButton(controlX, centerY, controlWidth, controlHeight,
                Component.literal(name), b -> {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            minecraft.setScreen(new PlayerSelectScreen(screen, AdminPanel::setTarget));
        }));

        // 第 2 行：金额
        int amountY = startY + rowHeight + rowHeight / 2 - controlHeight / 2;
        amountBox = new EditBox(net.minecraft.client.Minecraft.getInstance().font, controlX, amountY, controlWidth, controlHeight, Component.empty());
        amountBox.setMaxLength(12);
        amountBox.setFilter(s -> s.isEmpty() || s.matches("\\d+(\\.\\d{0,2})?"));
        amountBox.setValue("1000");
        addWidget.accept(amountBox);

        // 第 3 行：三个操作按钮
        int btnY = startY + rowHeight * 2 + rowHeight / 2 - controlHeight / 2;
        int gap = 2;
        int btnW = (controlWidth - gap * 2) / 3;
        addWidget.accept(new StyledButton(controlX, btnY, btnW, controlHeight,
                Component.translatable("gui.marketbox.admin.set"), b -> send(AdminBalancePacket.ACTION_SET)));
        addWidget.accept(new StyledButton(controlX + btnW + gap, btnY, btnW, controlHeight,
                Component.translatable("gui.marketbox.admin.add"), b -> send(AdminBalancePacket.ACTION_ADD)));
        addWidget.accept(new StyledButton(controlX + (btnW + gap) * 2, btnY, btnW, controlHeight,
                Component.translatable("gui.marketbox.admin.reset"), b -> send(AdminBalancePacket.ACTION_RESET)));
    }

    /** 玩家选择器回调：记录选中的目标 */
    static void setTarget(PlayerEntry entry) {
        if (entry == null) {
            return;
        }
        List<PlayerEntry> list = ClientData.getLeaderboard();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid().equals(entry.uuid())) {
                targetIndex = i;
                return;
            }
        }
    }

    private static void send(int action) {
        PlayerEntry target = currentTarget();
        if (target == null) {
            return;
        }
        double amount = 0;
        if (action != AdminBalancePacket.ACTION_RESET) {
            try {
                amount = Double.parseDouble(amountBox.getValue());
                if (amount <= 0) {
                    return;
                }
            } catch (NumberFormatException e) {
                return;
            }
        }
        ClientNetwork.send(new AdminBalancePacket(target.uuid(), amount, action));
    }
}
