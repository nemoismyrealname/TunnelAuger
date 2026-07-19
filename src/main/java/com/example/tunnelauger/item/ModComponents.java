package com.example.tunnelauger.item;

import com.example.tunnelauger.TunnelAugerMod;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModComponents {

    public static final DataComponentType<AugerProgress> AUGER_PROGRESS = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "auger_progress"),
            DataComponentType.<AugerProgress>builder().persistent(AugerProgress.CODEC).build()
    );

    /** Для визуального переключения модели: хранит только уровень (int), чтобы модель могла на него ссылаться. */
    public static final DataComponentType<Integer> AUGER_LEVEL = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "auger_level"),
            DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT).build()
    );

    private ModComponents() {
    }

    /** Вызывается из TunnelAugerMod.onInitialize() — гарантирует загрузку класса. */
    public static void register() {
    }
}
