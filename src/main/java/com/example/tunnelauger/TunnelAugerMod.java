package com.example.tunnelauger;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.tunnelauger.block.ModBlocks;
import com.example.tunnelauger.block.entity.ModBlockEntities;
import com.example.tunnelauger.handler.AugerMiningHandler;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.ModItems;

/**
 * Точка входа мода. Fabric вызывает onInitialize() один раз при старте игры
 * (и на клиенте, и на сервере — общий код).
 */
public class TunnelAugerMod implements ModInitializer {

    public static final String MOD_ID = "tunnel_auger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[TunnelAuger] инициализация начата");

        ModComponents.register();
        ModItems.register();
        ModBlocks.register();
        ModBlockEntities.register();
        AugerMiningHandler.register();

        LOGGER.info("[TunnelAuger] мод загружен");
    }
}
