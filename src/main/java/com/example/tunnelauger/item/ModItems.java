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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;

/**
 * Все предметы мода регистрируются здесь, в одном месте — так проще
 * поддерживать список при переходе на новые версии игры.
 *
 * TUNNEL_AUGER — кастомная кирка (TunnelAugerItem) с четырьмя тирами.
 * Базовая регистрация — на железном уровне (Tier 0). Пороги апгрейда
 * и прочность по тирам живут в {@link AugerProgress}, стоимость ритуала
 * и применение уровня — в {@link AugerUpgrades}.
 *
 * В креативной вкладке лежат все четыре тира (как ванильные
 * зачарованные книги разных уровней) — удобно для тестов и креатива.
 * В выживании путь один: копать и проводить ритуалы на Философском камне.
 */
public final class ModItems {

    /** Блоки, которые бур не может нормально добыть (указывает на #minecraft:incorrect_for_diamond_tool). */
    public static final TagKey<Block> INCORRECT_FOR_TUNNEL_AUGER = TagKey.create(
            BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "incorrect_for_tunnel_auger")
    );

    /** Чем можно чинить бур на наковальне (базовый тег для Tier 0;
     *  на старших тирах переопределяется компонентом REPAIRABLE,
     *  см. {@link AugerUpgrades#applyLevel}). */
    public static final TagKey<Item> REPAIRS_TUNNEL_AUGER = TagKey.create(
            BuiltInRegistries.ITEM.key(),
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "repairs_tunnel_auger")
    );

    // ── Материал бура ────────────────────────
    //
    // Базовая регистрация — на железном уровне (500 прочность, 6.0 скорость).
    // Все level-зависимые штуки (скорость, правильный инструмент для дропа,
    // корректный тег неправильных блоков) переопределены в TunnelAugerItem,
    // прочность и ремонт по тирам — через компоненты в AugerUpgrades.applyLevel().

    public static final ToolMaterial TUNNEL_AUGER_MATERIAL = new ToolMaterial(
            INCORRECT_FOR_TUNNEL_AUGER,
            500,    // прочность (Tier 0; выше — через MAX_DAMAGE-компонент)
            6.0F,   // скорость копки
            1.0F,   // бонус к урону
            14,     // зачаровываемость
            REPAIRS_TUNNEL_AUGER
    );

    // ── Предмет ─────────────────────────────────

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
                .register(entries -> {
                    entries.accept(TUNNEL_AUGER);
                    // Старшие тиры — для креатива и быстрого теста без гринда
                    for (int level = 1; level <= AugerProgress.MAX_LEVEL; level++) {
                        entries.accept(augerAtLevel(level));
                    }
                });
    }

    /** Готовый стак бура указанного тира (креативная вкладка, тесты). */
    public static ItemStack augerAtLevel(int level) {
        ItemStack stack = new ItemStack(TUNNEL_AUGER);
        AugerUpgrades.applyLevel(stack, new AugerProgress(level, 0));
        return stack;
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
