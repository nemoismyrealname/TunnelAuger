package com.example.tunnelauger.block.entity;

import com.example.tunnelauger.TunnelAugerMod;
import com.example.tunnelauger.block.ModBlocks;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final BlockEntityType<BuildersStoneBlockEntity> BUILDERS_STONE_ENTITY = register(
            "builders_philosopher_stone",
            BuildersStoneBlockEntity::new,
            ModBlocks.BUILDERS_STONE
    );

    private ModBlockEntities() {
    }

    /**
     * Ничего не делает — но вызов из TunnelAugerMod.onInitialize() гарантирует,
     * что класс загрузится и статическое поле выше зарегистрируется.
     */
    public static void register() {
    }

    private static <T extends BlockEntity> BlockEntityType<T> register(
            String name,
            FabricBlockEntityTypeBuilder.Factory<? extends T> entityFactory,
            Block... blocks
    ) {
        Identifier id = Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, name);
        return Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                id,
                FabricBlockEntityTypeBuilder.<T>create(entityFactory, blocks).build()
        );
    }
}
