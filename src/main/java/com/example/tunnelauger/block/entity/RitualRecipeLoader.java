package com.example.tunnelauger.block.entity;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.tunnelauger.TunnelAugerMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Загружает рецепты ритуала из датапаков: {@code data/<ns>/ritual_recipes/*.json}.
 *
 * <p>Сознательно использует простой {@link ResourceManagerReloadListener}
 * и ручной обход {@code listResources}, а не SimpleJsonResourceReloadListener —
 * у последнего конструктор менялся между версиями, а этот API стабилен.</p>
 *
 * <p>Ошибка в одном файле не роняет загрузку остальных — она логируется,
 * файл пропускается.</p>
 */
public final class RitualRecipeLoader implements IdentifiableResourceReloadListener, ResourceManagerReloadListener {

    private static final String DIRECTORY = "ritual_recipes";

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath(TunnelAugerMod.MOD_ID, DIRECTORY);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        List<RitualRecipe> loaded = new ArrayList<>();

        Map<Identifier, Resource> resources =
                manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                loaded.add(parse(entry.getKey(), GsonHelper.parse(reader)));
            } catch (Exception e) {
                TunnelAugerMod.LOGGER.error("[TunnelAuger] не удалось прочитать рецепт ритуала {}", entry.getKey(), e);
            }
        }

        RitualRecipes.reload(loaded);
        TunnelAugerMod.LOGGER.info("[TunnelAuger] загружено рецептов ритуала: {}", loaded.size());
    }

    private static RitualRecipe parse(Identifier sourceId, JsonObject json) {
        JsonObject ingredientsJson = GsonHelper.getAsJsonObject(json, "ingredients");
        Map<Item, Integer> ingredients = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : ingredientsJson.entrySet()) {
            int count = GsonHelper.convertToInt(e.getValue(), e.getKey());
            if (count <= 0) {
                throw new IllegalArgumentException("количество для '" + e.getKey() + "' должно быть > 0");
            }
            ingredients.put(itemById(e.getKey(), sourceId), count);
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("пустой список ingredients");
        }

        JsonObject resultJson = GsonHelper.getAsJsonObject(json, "result");
        Item resultItem = itemById(GsonHelper.getAsString(resultJson, "item"), sourceId);
        int resultCount = GsonHelper.getAsInt(resultJson, "count", 1);

        return new RitualRecipe(Map.copyOf(ingredients), new ItemStack(resultItem, resultCount));
    }

    private static Item itemById(String id, Identifier sourceId) {
        // NB: в старых маппингах — Identifier.tryParse(...)
        Identifier itemId = Identifier.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            throw new IllegalArgumentException("неизвестный предмет '" + id + "' в рецепте " + sourceId);
        }
        // NB: в некоторых версиях метод называется get(...)
        return BuiltInRegistries.ITEM.getValue(itemId);
    }
}
