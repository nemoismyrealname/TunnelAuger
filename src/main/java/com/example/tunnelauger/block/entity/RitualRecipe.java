package com.example.tunnelauger.block.entity;

import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * ingredients — какие предметы и в каком количестве нужно бросить на камень.
 * result — что появится, если всё совпало.
 *
 * Рецепты грузятся из JSON-датапаков ({@code data/<ns>/ritual_recipes/*.json},
 * читает {@link RitualRecipeLoader}) и хранятся в {@link RitualRecipes}.
 * Перечитываются командой /reload без пересборки мода.
 */
public record RitualRecipe(Map<Item, Integer> ingredients, ItemStack result) {
}
