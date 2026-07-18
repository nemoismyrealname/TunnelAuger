package com.example.tunnelauger.block.entity;

import java.util.Map;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * ingredients — какие предметы и в каком количестве нужно бросить на камень.
 * result — что появится, если всё совпало.
 *
 * Сейчас список рецептов просто захардкожен в RitualRecipes — этого хватает,
 * пока рецептов немного. Когда их станет больше и захочется редактировать
 * без пересборки мода, этот же record можно грузить из JSON через
 * SimpleJsonResourceReloadListener — сама структура данных для этого
 * не изменится.
 */
public record RitualRecipe(Map<Item, Integer> ingredients, ItemStack result) {
}
