package com.example.tunnelauger.block.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Логика ритуала:
 *  1. Раз в 10 тиков (не каждый — незачем гонять сканирование постоянно)
 *     собираем все ItemEntity в зоне сразу над камнем.
 *  2. Сравниваем с известными рецептами (RitualRecipes.ALL).
 *  3. При совпадении — списываем нужное количество предметов,
 *     заканчиваем на первом сработавшем рецепте за этот тик,
 *     выплёвываем результат и играем звук.
 */
public class BuildersStoneBlockEntity extends BlockEntity {

    private static final int CHECK_INTERVAL_TICKS = 10;

    private int ticksSinceCheck = 0;

    public BuildersStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUILDERS_STONE_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BuildersStoneBlockEntity entity) {
        if (level.isClientSide()) {
            return;
        }

        entity.ticksSinceCheck++;
        if (entity.ticksSinceCheck < CHECK_INTERVAL_TICKS) {
            return;
        }
        entity.ticksSinceCheck = 0;

        // зона сразу над камнем, с небольшим запасом на физическое дрожание предметов
        AABB scanArea = new AABB(pos).move(0, 1, 0).inflate(0.15);
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, scanArea);
        if (found.isEmpty()) {
            return;
        }

        for (RitualRecipe recipe : RitualRecipes.ALL) {
            if (tryConsume(found, recipe)) {
                spawnResult(level, pos, recipe.result());
                level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.0f);
                return;
            }
        }
    }

    /**
     * Проверяет, хватает ли брошенных предметов на рецепт, и если да —
     * списывает их с ItemEntity (уменьшает стак или удаляет сущность).
     * Возвращает false и ничего не трогает, если предметов не хватает.
     */
    private static boolean tryConsume(List<ItemEntity> entities, RitualRecipe recipe) {
        Map<Item, Integer> available = new HashMap<>();
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) {
                continue;
            }
            available.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
        }

        for (Map.Entry<Item, Integer> requirement : recipe.ingredients().entrySet()) {
            if (available.getOrDefault(requirement.getKey(), 0) < requirement.getValue()) {
                return false;
            }
        }

        Map<Item, Integer> remaining = new HashMap<>(recipe.ingredients());
        for (ItemEntity e : entities) {
            if (remaining.isEmpty()) {
                break;
            }
            if (!e.isAlive() || e.getItem().isEmpty()) {
                continue;
            }

            Item item = e.getItem().getItem();
            Integer need = remaining.get(item);
            if (need == null || need <= 0) {
                continue;
            }

            int take = Math.min(need, e.getItem().getCount());
            e.getItem().shrink(take);
            if (e.getItem().isEmpty()) {
                e.discard();
            }

            int left = need - take;
            if (left <= 0) {
                remaining.remove(item);
            } else {
                remaining.put(item, left);
            }
        }

        return true;
    }

    private static void spawnResult(Level level, BlockPos pos, ItemStack result) {
        ItemEntity resultEntity = new ItemEntity(
                level,
                pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                result.copy()
        );
        resultEntity.setDefaultPickUpDelay();
        level.addFreshEntity(resultEntity);
    }
}
