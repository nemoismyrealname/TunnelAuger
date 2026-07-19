package com.example.tunnelauger.block.entity;

import java.util.List;
import java.util.Map;

import com.example.tunnelauger.item.ModItems;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Стартовый рецепт для проверки механики: железная кирка + 4 железных
 * слитка + алмаз, брошенные на камень — превращаются в земляной бур.
 * Числа и состав — черновые, баланс подгоним отдельно после того как
 * заработает сам механизм.
 */
public final class RitualRecipes {

    public static final List<RitualRecipe> ALL = List.of(
            new RitualRecipe(
                    Map.of(
                            Items.IRON_PICKAXE, 1,
                            Items.IRON_INGOT, 4,
                            Items.DIAMOND, 1
                    ),
                    new ItemStack(ModItems.TUNNEL_AUGER)
            ),

            new RitualRecipe(
                    Map.of(Items.COBBLESTONE, 3),
                    new ItemStack(Items.STONE_PICKAXE)
    )
    );

    private RitualRecipes() {
    }
}
