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
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;

/**
 * Все предметы мода регистрируются здесь, в одном месте — так проще
 * поддерживать список при переходе на новые версии игры.
 *
 * TUNNEL_AUGER сейчас — полноценная кирка по характеристикам (материал
 * "как железо"), но пока без площадной копки и без уровней — это
 * следующие шаги. Площадная копка и прогрессия будут жить в отдельном
 * обработчике, а не здесь, чтобы не смешивать регистрацию и поведение.
 */
public final class ModItems {

    /** Блоки, которые бур не может нормально добыть — как у железного инструмента. */
    public static final TagKey<Block> INCORRECT_FOR_TUNNEL_AUGER = TagKey.create(
            BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "incorrect_for_tunnel_auger")
    );

    /** Чем можно чинить бур на наковальне. */
    public static final TagKey<Item> REPAIRS_TUNNEL_AUGER = TagKey.create(
            BuiltInRegistries.ITEM.key(),
            Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, "repairs_tunnel_auger")
    );

    public static final ToolMaterial TUNNEL_AUGER_MATERIAL = new ToolMaterial(
            INCORRECT_FOR_TUNNEL_AUGER,
            500,    // прочность
            6.0F,   // скорость копки (как у железа)
            1.0F,   // бонус к урону в ближнем бою
            14,     // зачаровываемость
            REPAIRS_TUNNEL_AUGER
    );

    public static final Item TUNNEL_AUGER = register(
            "tunnel_auger",
            settings -> new Item(settings
                    .pickaxe(TUNNEL_AUGER_MATERIAL, 1.0F, -2.8F)
                    .component(ModComponents.AUGER_PROGRESS, AugerProgress.INITIAL))
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
