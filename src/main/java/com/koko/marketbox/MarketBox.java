package com.koko.marketbox;

import com.koko.marketbox.commands.MarketBoxCommand;
import com.koko.marketbox.event.CommonEvents;
import com.koko.marketbox.event.PlayerEvents;
import com.koko.marketbox.event.ServerEvents;
import com.koko.marketbox.item.ModCreativeModeTabs;
import com.koko.marketbox.item.ModItems;
import com.koko.marketbox.networking.ModMessages;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MarketBox 主类（Fabric 入口）。
 * 只负责初始化顺序编排，不承载具体业务逻辑，保持单一职责。
 */
public class MarketBox implements ModInitializer {
    public static final String MODID = "marketbox";
    public static final Logger LOGGER = LoggerFactory.getLogger("MarketBox");

    @Override
    public void onInitialize() {
        LOGGER.info("MarketBox initializing...");

        // 配置与物价表：两端都需要，客户端需本地价格用于展示
        Config.load();

        // 注册表条目必须早于网络与事件
        ModItems.register();
        ModCreativeModeTabs.register();

        // 网络通道与接收器
        ModMessages.register();

        // 事件与命令
        CommonEvents.register();
        ServerEvents.register();
        PlayerEvents.register();
        MarketBoxCommand.register();

        LOGGER.info("MarketBox initialized, version {}",
                FabricLoader.getInstance().getModContainer(MODID)
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"));
    }

    /** 构造本模组命名空间下的资源位置 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
