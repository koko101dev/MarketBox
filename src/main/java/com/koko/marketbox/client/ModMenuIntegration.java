package com.koko.marketbox.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 集成：在模组列表中为本模组提供「配置」按钮。
 * 该类只在安装 Mod Menu 时由它的 entrypoint 加载，未安装时不参与运行，因此 Mod Menu 是可选前置。
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // ConfigScreen 接收上一级界面，关闭时返回 Mod Menu 列表
        return ConfigScreen::new;
    }
}
