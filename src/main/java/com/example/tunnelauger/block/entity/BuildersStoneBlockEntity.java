package com.example.tunnelauger.block.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Логика ритуала и улучшения бура на Философском камне строителя.
 *
 * <p><b>Сервер (каждые 10 тиков):</b>
 * <ol>
 *   <li>Сканирует предметы прямо над камнем.</li>
 *   <li>Проверяет рецепты крафта ({@link RitualRecipes}).</li>
 *   <li>Если нет — проверяет апгрейд бура.</li>
 *   <li>При несовпадении — визуальный отклик (частицы + звук).</li>
 * </ol>
 *
 * <p><b>Клиент (каждый тик):</b> фоновые искорки зачарования.
 *
 * <p>Частицы вынесены в {@link BuildersStoneParticles}.
 */
public class BuildersStoneBlockEntity extends BlockEntity {

    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final int COOLDOWN_TICKS = 40;

    private int ticksSinceCheck = 0;
    private int cooldownTicks = 0;

    public BuildersStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUILDERS_STONE_ENTITY, pos, state);
    }

    // ═══════════════════════════════════════════════
    //  Главный тик
    // ═══════════════════════════════════════════════

    public static void tick(Level level, BlockPos pos, BlockState state, BuildersStoneBlockEntity entity) {
        if (level.isClientSide()) {
            entity.clientParticles(level, pos);
            return;
        }

        if (entity.cooldownTicks > 0) {
            entity.cooldownTicks--;
            return;
        }

        entity.ticksSinceCheck++;

        if (entity.ticksSinceCheck < CHECK_INTERVAL_TICKS) return;
        entity.ticksSinceCheck = 0;

        List<ItemEntity> items = scanItems(level, pos);
        if (items.isEmpty()) return;

        // 1. Обычные рецепты
        if (tryAnyRecipe(level, pos, items)) {
            entity.cooldownTicks = COOLDOWN_TICKS;
            return;
        }

        // 2. Апгрейд бура
        if (tryUpgrade(level, pos, items)) {
            entity.cooldownTicks = COOLDOWN_TICKS;
            return;
        }

        // 3. Валидация — шипим, если предметы явно не подходят
        if (!isUpgradePending(items) && (!isPossibleSubsetOfAnyRecipe(items) || hasExcessiveQuantities(items))) {
            BuildersStoneParticles.spawnFailure(level, pos);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 0.8f);
        }
    }

    // ═══════════════════════════════════════════════
    //  Сканирование предметов
    // ═══════════════════════════════════════════════

    private static List<ItemEntity> scanItems(Level level, BlockPos pos) {
        AABB scanArea = new AABB(pos).move(0, 1, 0).inflate(0.15);
        return level.getEntitiesOfClass(ItemEntity.class, scanArea);
    }

    // ═══════════════════════════════════════════════
    //  Рецепты
    // ═══════════════════════════════════════════════

    private static boolean tryAnyRecipe(Level level, BlockPos pos, List<ItemEntity> items) {
        for (RitualRecipe recipe : RitualRecipes.ALL) {
            if (!canConsume(items, recipe)) continue;

            consume(items, recipe);
            spawnResult(level, pos, recipe.result());
            BuildersStoneParticles.spawnSuccess(level, pos);
            level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.2f);
            level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.0f);
            return true;
        }
        return false;
    }

    private static boolean canConsume(List<ItemEntity> entities, RitualRecipe recipe) {
        Map<Item, Integer> available = new HashMap<>();
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            available.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
        }
        for (Map.Entry<Item, Integer> requirement : recipe.ingredients().entrySet()) {
            if (available.getOrDefault(requirement.getKey(), 0) < requirement.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void consume(List<ItemEntity> entities, RitualRecipe recipe) {
        Map<Item, Integer> remaining = new HashMap<>(recipe.ingredients());
        for (ItemEntity e : entities) {
            if (remaining.isEmpty()) break;
            if (!e.isAlive() || e.getItem().isEmpty()) continue;

            Item item = e.getItem().getItem();
            Integer need = remaining.get(item);
            if (need == null || need <= 0) continue;

            int take = Math.min(need, e.getItem().getCount());
            e.getItem().shrink(take);
            if (e.getItem().isEmpty()) e.discard();

            int left = need - take;
            if (left <= 0) remaining.remove(item);
            else remaining.put(item, left);
        }
    }

    private static void spawnResult(Level level, BlockPos pos, ItemStack result) {
        ItemEntity resultEntity = new ItemEntity(
                level,
                pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                result.copy(),
                0.0, 0.0, 0.0   // без скорости — парит над камнем
        );
        resultEntity.setDefaultPickUpDelay();
        level.addFreshEntity(resultEntity);
    }

    // ═══════════════════════════════════════════════
    //  Апгрейд бура
    // ═══════════════════════════════════════════════

    /**
     * Уровни: 0→1 (♥ + 8 золота), 1→2 (♥ + 8 алмазов), 2→3 (♥ + 8 алм. блоков).
     *
     * @return true, если апгрейд выполнен
     */
    private static boolean tryUpgrade(Level level, BlockPos pos, List<ItemEntity> entities) {
        if (!(level instanceof ServerLevel)) return false;

        AugerTarget target = findAugerTarget(entities);
        if (target == null) return false;

        AugerProgress progress = target.progress();

        if (!progress.canUpgrade()) {
            BuildersStoneParticles.spawnFailure(level, pos);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 0.8f);
            return true;
        }

        int nextLevel = progress.level() + 1;
        UpgradeMaterial material = UpgradeMaterial.forLevel(nextLevel);
        if (material == null) return false;

        if (!hasUpgradeMaterials(entities, material)) return false;

        consumeUpgradeMaterials(entities, material);

        // ── Применяем апгрейд (через setItem чтобы обновить визуал) ──
        ItemStack augerStack = target.entity().getItem().copy();
        augerStack.set(ModComponents.AUGER_PROGRESS, progress.nextLevel());
        augerStack.set(ModComponents.AUGER_LEVEL, nextLevel);
        augerStack.set(DataComponents.RARITY, AugerProgress.rarityForLevel(nextLevel));
        target.entity().setItem(augerStack);

        BuildersStoneParticles.spawnUpgrade(level, pos);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.2f);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.0f);
        return true;
    }

    private record AugerTarget(ItemEntity entity, AugerProgress progress) {
    }

    private record UpgradeMaterial(Item item, int count) {
        static UpgradeMaterial forLevel(int level) {
            return switch (level) {
                case 1 -> new UpgradeMaterial(Items.GOLD_INGOT, 8);
                case 2 -> new UpgradeMaterial(Items.DIAMOND, 8);
                case 3 -> new UpgradeMaterial(Items.DIAMOND_BLOCK, 8);
                default -> null;
            };
        }
    }

    private static AugerTarget findAugerTarget(List<ItemEntity> entities) {
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            if (!e.getItem().is(ModItems.TUNNEL_AUGER)) continue;

            AugerProgress p = e.getItem().get(ModComponents.AUGER_PROGRESS);
            if (p != null && p.level() < AugerProgress.MAX_LEVEL) {
                return new AugerTarget(e, p);
            }
        }
        return null;
    }

    private static boolean hasUpgradeMaterials(List<ItemEntity> entities, UpgradeMaterial material) {
        int hearts = 0;
        int mats = 0;
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            ItemStack stack = e.getItem();
            if (stack.is(Items.HEART_OF_THE_SEA)) {
                hearts += stack.getCount();
            } else if (stack.is(material.item())) {
                mats += stack.getCount();
            }
        }
        return hearts >= 1 && mats >= material.count();
    }

    private static void consumeUpgradeMaterials(List<ItemEntity> entities, UpgradeMaterial material) {
        int heartNeeded = 1;
        int matNeeded = material.count();

        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            if (heartNeeded <= 0 && matNeeded <= 0) break;

            ItemStack stack = e.getItem();

            if (stack.is(Items.HEART_OF_THE_SEA) && heartNeeded > 0) {
                int take = Math.min(heartNeeded, stack.getCount());
                stack.shrink(take);
                heartNeeded -= take;
                if (stack.isEmpty()) e.discard();
            } else if (stack.is(material.item()) && matNeeded > 0) {
                int take = Math.min(matNeeded, stack.getCount());
                stack.shrink(take);
                matNeeded -= take;
                if (stack.isEmpty()) e.discard();
            }
        }
    }

    // ═══════════════════════════════════════════════
    //  Валидация предметов на алтаре
    // ═══════════════════════════════════════════════

    private static boolean isUpgradePending(List<ItemEntity> entities) {
        return findAugerTarget(entities) != null;
    }

    /** Все ли типы предметов входят в состав хотя бы одного рецепта. */
    private static boolean isPossibleSubsetOfAnyRecipe(List<ItemEntity> entities) {
        Set<Item> presentTypes = new HashSet<>();
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            presentTypes.add(e.getItem().getItem());
        }
        if (presentTypes.isEmpty()) return true;

        for (RitualRecipe recipe : RitualRecipes.ALL) {
            if (recipe.ingredients().keySet().containsAll(presentTypes)) {
                return true;
            }
        }
        return false;
    }

    /** Превышает ли количество предметов максимум по любому рецепту. */
    private static boolean hasExcessiveQuantities(List<ItemEntity> entities) {
        Map<Item, Integer> maxNeeded = new HashMap<>();
        for (RitualRecipe recipe : RitualRecipes.ALL) {
            for (Map.Entry<Item, Integer> entry : recipe.ingredients().entrySet()) {
                maxNeeded.merge(entry.getKey(), entry.getValue(), Integer::max);
            }
        }

        Map<Item, Integer> totalByType = new HashMap<>();
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            totalByType.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
        }

        for (Map.Entry<Item, Integer> entry : totalByType.entrySet()) {
            if (entry.getValue() > maxNeeded.getOrDefault(entry.getKey(), 0)) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════
    //  Клиентские частицы
    // ═══════════════════════════════════════════════

    private void clientParticles(Level level, BlockPos pos) {
        if (level.getRandom().nextInt(20) != 0) return;

        AABB scanArea = new AABB(pos).move(0, 1, 0).inflate(0.15);
        boolean hasItems = !level.getEntitiesOfClass(ItemEntity.class, scanArea).isEmpty();

        if (hasItems && level.getRandom().nextBoolean()) {
            BuildersStoneParticles.spawnAmbient(level, pos);
        }
        BuildersStoneParticles.spawnAmbient(level, pos);
    }
}
