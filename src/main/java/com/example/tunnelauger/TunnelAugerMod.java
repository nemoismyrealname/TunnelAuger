package com.example.tunnelauger;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.tunnelauger.item.ModItems;

/**
 * Точка входа мода. Fabric вызывает onInitialize() один раз при старте игры
 * (и на клиенте, и на сервере — общий код).
 *
 * Дальше сюда добавятся вызовы ModBlocks.register(), ModBlockEntities.register()
 * и т.д. — каждый новый кусок функциональности одной строкой.
 */
public class TunnelAugerMod implements ModInitializer {

    public static final String MOD_ID = "tunnel-auger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[TunnelAuger] инициализация начата");

        ModItems.register();

        LOGGER.info("[TunnelAuger] мод загружен");
    }
}
