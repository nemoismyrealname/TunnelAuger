package com.example.tunnelauger.item;

import java.util.function.Function;

import com.example.tunnelauger.TunnelAugerMod;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/**
 * Все предметы мода регистрируются здесь, в одном месте — так проще
 * поддерживать список при переходе на новые версии игры.
 *
 * TUNNEL_AUGER пока обычный Item без особой логики. Механику площадной
 * копки и прогрессии уровней добавим отдельным классом позже.
 */
public final class ModItems {

    public static final Item TUNNEL_AUGER = register(
            "tunnel_auger",
            settings -> new Item(settings.stacksTo(1).durability(500))
    );

    private ModItems() {
    }

    /** Вызывается один раз из TunnelAugerMod.onInitialize(). */
    public static void register() {
        // добавляем бур во вкладку "Инструменты" творческого инвентаря
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
