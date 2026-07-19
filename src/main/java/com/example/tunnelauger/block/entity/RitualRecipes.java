package com.example.tunnelauger.block.entity;

import java.util.List;

import com.example.tunnelauger.TunnelAugerMod;

/**
 * Держатель актуального списка рецептов ритуала.
 *
 * <p>Источник истины — JSON-файлы датапаков:
 * {@code data/<namespace>/ritual_recipes/*.json} (читает {@link RitualRecipeLoader}).
 * Встроенные рецепты мода лежат в ресурсах:
 * {@code src/main/resources/data/tunnel_auger/ritual_recipes/}.
 * Рецепты можно добавлять/менять датапаком без пересборки мода;
 * {@code /reload} перечитывает их на лету.</p>
 *
 * <p>Формат файла:</p>
 * <pre>{@code
 * {
 *   "ingredients": {
 *     "minecraft:iron_pickaxe": 1,
 *     "minecraft:iron_ingot": 4,
 *     "minecraft:diamond": 1
 *   },
 *   "result": { "item": "tunnel_auger:tunnel_auger", "count": 1 }
 * }
 * }</pre>
 */
public final class RitualRecipes {

    private static volatile List<RitualRecipe> recipes = List.of();

    private RitualRecipes() {
    }

    /** Актуальный список рецептов (неизменяемый). */
    public static List<RitualRecipe> all() {
        return recipes;
    }

    /** Вызывается из {@link RitualRecipeLoader} при (пере)загрузке ресурсов. */
    static void reload(List<RitualRecipe> loaded) {
        if (loaded.isEmpty()) {
            TunnelAugerMod.LOGGER.warn("[TunnelAuger] не найдено ни одного рецепта ритуала — камень будет только апгрейдить бур");
        }
        recipes = List.copyOf(loaded);
    }
}
