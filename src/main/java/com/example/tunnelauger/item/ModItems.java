package com.example.tunnelauger.item;

import java.util.function.Function;

import com.example.tunnelauger.TunnelAugerMod;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;

/**
 * Все предметы мода регистрируются здесь, в одном месте — так проще
 * поддерживать список при переходе на новые версии игры.
 *
 * TUNNEL_AUGER — кастомная кирка (TunnelAugerItem):
 * <ul>
 *   <li>Уровень 0 — железный уровень (6.0 скорость, 500 прочность)</li>
 *   <li>Уровень 1 — алмазный уровень (8.0 скорость, 1561 прочность, копка 3×3)</li>
 * </ul>
 *
 * В креативе выдаётся бур уровня 0 — игроку нужно накопать 200 блоков
 * и провести ритуал на Философском камне, чтобы улучшить до уровня 1.
 */
public final class ModItems {

    /** Блоки, которые бур не может нормально добыть (указывает на #minecraft:incorrect_for_diamond_tool). */
    public static final TagKey<Block> INCORRECT_FOR_TUNNEL_AUGER = TagKey.create(
            BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "incorrect_for_tunnel_auger")
    );

    /** Чем можно чинить бур на наковальне. */
    public static final TagKey<Item> REPAIRS_TUNNEL_AUGER = TagKey.create(
            BuiltInRegistries.ITEM.key(),
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "repairs_tunnel_auger")
    );

    // ── Материал бура ────────────────────────────
    //
    // Базовая регистрация — на железном уровне (500 прочность, 6.0 скорость).
    // Все level-зависимые штуки (скорость, правильный инструмент для дропа,
    // корректный тег неправильных блоков) переопределены в TunnelAugerItem
    // и смотрят на AugerProgress.level. Алмазные характеристики для level=1
    // задаются там же, в коде предмета.

    public static final ToolMaterial TUNNEL_AUGER_MATERIAL = new ToolMaterial(
            INCORRECT_FOR_TUNNEL_AUGER,
            500,    // прочность
            6.0F,   // скорость копки
            1.0F,   // бонус к урону
            14,     // зачаровываемость
            REPAIRS_TUNNEL_AUGER
    );

    // ── Предмет ─────────────────────────────────────────

    public static final Item TUNNEL_AUGER = register(
            "tunnel_auger",
            settings -> new TunnelAugerItem(
                    TUNNEL_AUGER_MATERIAL,
                    1.0F,     // бонус атаки базовый (поверх материала)
                    -2.8F,    // скорость атаки
                    settings.component(ModComponents.AUGER_PROGRESS, AugerProgress.INITIAL)
                            .component(ModComponents.AUGER_LEVEL, 0)
                            .component(DataComponents.RARITY, AugerProgress.rarityForLevel(0))
            )
    );

    private ModItems() {
    }

    /** Вызывается один раз из TunnelAugerMod.onInitialize(). */
    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.accept(TUNNEL_AUGER));
    }

    private static Item register(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, name)
        );
        Item item = factory.apply(new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
