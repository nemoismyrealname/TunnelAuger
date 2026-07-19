package com.example.tunnelauger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.tunnelauger.block.ModBlocks;
import com.example.tunnelauger.block.entity.ModBlockEntities;
import com.example.tunnelauger.block.entity.RitualRecipeLoader;
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

        // Рецепты ритуала грузятся из датапаков: data/<ns>/ritual_recipes/*.json
        RitualRecipeLoader ritualLoader = new RitualRecipeLoader();
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(ritualLoader);

        // Страховка: если reload-listener по какой-то причине не сработал
        // (и рецепты остались пустыми) — читаем их напрямую при старте
        // сервера (работает и в одиночной игре, и на dedicated-сервере).
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                ritualLoader.onResourceManagerReload(server.getResourceManager()));

        LOGGER.info("[TunnelAuger] мод загружен");
    }
}
