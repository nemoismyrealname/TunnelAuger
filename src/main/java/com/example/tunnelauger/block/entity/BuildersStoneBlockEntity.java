package com.example.tunnelauger.block.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
 * Логика ритуала + визуальная обратная связь.
 *
 * Сервер (каждые 10 тиков):
 *   1. Сканирует предметы прямо над камнем.
 *   2. За 4 тика до проверки — мягко подтягивает предметы к центру.
 *   3. При совпадении рецепта — спираль частиц, звук, кулдаун.
 *   4. При несовпадении — дымок + искры, чтобы игрок понял: «что-то не так».
 *
 * Клиент (каждый тик):
 *   - Фоновые искорки зачарования (чаще, если над камнем есть предметы).
 */
public class BuildersStoneBlockEntity extends BlockEntity {

    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final int COOLDOWN_TICKS = 40; // 2 секунды отдыха после крафта

    private int ticksSinceCheck = 0;
    private int cooldownTicks = 0;

    public BuildersStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUILDERS_STONE_ENTITY, pos, state);
    }

    // ═══════════════════════════════════════════════
    //  Главный тик — серверная логика + клиентские частицы
    // ═══════════════════════════════════════════════

    public static void tick(Level level, BlockPos pos, BlockState state, BuildersStoneBlockEntity entity) {
        if (level.isClientSide()) {
            entity.clientParticles(level, pos);
            return;
        }

        // ── Кулдаун после успешного крафта ──────────
        if (entity.cooldownTicks > 0) {
            entity.cooldownTicks--;
            return;
        }

        entity.ticksSinceCheck++;

        // ── За 4 тика до проверки — подтягиваем предметы ──
        if (entity.ticksSinceCheck >= CHECK_INTERVAL_TICKS - 4) {
            pullItemsTowardCenter(level, pos);
        }

        if (entity.ticksSinceCheck < CHECK_INTERVAL_TICKS) {
            return;
        }
        entity.ticksSinceCheck = 0;

        // ── Сканирование зоны над камнем ────────────
        AABB scanArea = new AABB(pos).move(0, 1, 0).inflate(0.15);
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, scanArea);
        if (found.isEmpty()) {
            return;
        }

        for (RitualRecipe recipe : RitualRecipes.ALL) {
            if (tryConsume(found, recipe)) {
                spawnResult(level, pos, recipe.result());
                spawnSuccessParticles(level, pos);
                level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.2f);
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.0f);
                entity.cooldownTicks = COOLDOWN_TICKS;
                return;
            }
        }

        // ── Ни один рецепт не собрался целиком — но это может быть
        //    просто незаконченный набор (игрок кидает предметы по одному).
        //    Показываем "неудачу" только если текущие предметы вообще
        //    не подходят ни к одному рецепту.
        if (!isPossibleSubsetOfAnyRecipe(found)) {
            spawnFailureParticles(level, pos);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 0.8f);
        }
    }

    /**
     * true, если каждый тип брошенного предмета входит в состав хотя бы
     * одного рецепта (неважно, в достаточном ли количестве) — то есть
     * игрок мог просто ещё не докидать остальное.
     */
    private static boolean isPossibleSubsetOfAnyRecipe(List<ItemEntity> entities) {
        Set<Item> presentTypes = new HashSet<>();
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) {
                continue;
            }
            presentTypes.add(e.getItem().getItem());
        }
        if (presentTypes.isEmpty()) {
            return true;
        }

        for (RitualRecipe recipe : RitualRecipes.ALL) {
            if (recipe.ingredients().keySet().containsAll(presentTypes)) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════════════════════════════════════
    //  Клиент-сайд: фоновые частицы
    // ═══════════════════════════════════════════════

    /**
     * Искорки зачарования, которые поднимаются от камня.
     * Проверка случайного шанса — до сканирования сущностей,
     * чтобы не дёргать AABB-запрос каждый тик без надобности.
     */
    private void clientParticles(Level level, BlockPos pos) {
        // Базовый шанс ~5% (раз в секунду). Если над камнем есть
        // предметы —сканирование сделаем только при выпадении шанса.
        if (level.getRandom().nextInt(20) != 0) return;

        AABB scanArea = new AABB(pos).move(0, 1, 0).inflate(0.15);
        boolean hasItems = !level.getEntitiesOfClass(ItemEntity.class, scanArea).isEmpty();

        // Если предметы есть — ещё одна частица с вероятностью 50%
        if (hasItems && level.getRandom().nextBoolean()) {
            spawnAmbientParticle(level, pos);
        }

        spawnAmbientParticle(level, pos);
    }

    private static void spawnAmbientParticle(Level level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.9;
        double cz = pos.getZ() + 0.5;

        level.addParticle(
                ParticleTypes.ENCHANT,
                cx + (level.getRandom().nextDouble() - 0.5) * 0.4,
                cy + level.getRandom().nextDouble() * 0.3,
                cz + (level.getRandom().nextDouble() - 0.5) * 0.4,
                (level.getRandom().nextDouble() - 0.5) * 0.02,
                0.08 + level.getRandom().nextDouble() * 0.05,
                (level.getRandom().nextDouble() - 0.5) * 0.02
        );
    }

    // ═══════════════════════════════════════════════
    //  Подтягивание предметов к центру (визуал)
    // ═══════════════════════════════════════════════

    /**
     * За несколько тиков до проверки рецепта предметы плавно
     * дрейфуют к центру блока — создаёт ощущение «втягивания».
     */
    private static void pullItemsTowardCenter(Level level, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.2;
        double cz = pos.getZ() + 0.5;

        AABB scanArea = new AABB(pos).move(0, 1, 0).inflate(0.5);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, scanArea);
        for (ItemEntity item : items) {
            if (!item.isAlive() || item.getItem().isEmpty()) continue;

            double dx = cx - item.getX();
            double dy = cy - item.getY();
            double dz = cz - item.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < 0.01) continue;

            double factor = 0.04;
            item.setDeltaMovement(
                    item.getDeltaMovement().x + dx * factor,
                    item.getDeltaMovement().y + dy * factor + 0.02,
                    item.getDeltaMovement().z + dz * factor
            );
        }
    }

    // ═══════════════════════════════════════════════
    //  Частицы успеха
    // ═══════════════════════════════════════════════

    /**
     * Спираль из частиц зачарования + вспышка в центре + лёгкий дым.
     */
    private static void spawnSuccessParticles(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;

        // Спираль: частицы зачарования по кругу, каждая летит вверх
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            serverLevel.sendParticles(
                    ParticleTypes.ENCHANT,
                    cx + Math.cos(rad) * 0.5,
                    cy,
                    cz + Math.sin(rad) * 0.5,
                    1, 0.0, 0.3, 0.0, 0.15
            );
        }

        // Вспышка в центре — частицы от зачарованного удара
        serverLevel.sendParticles(
                ParticleTypes.ENCHANTED_HIT,
                cx, cy + 0.3, cz,
                12, 0.2, 0.1, 0.2, 0.3
        );

        // Дымок по краям — «ожог» от алхимической реакции
        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                cx, cy + 0.2, cz,
                8, 0.4, 0.1, 0.4, 0.02
        );
    }

    // ═══════════════════════════════════════════════
    //  Частицы неудачи
    // ═══════════════════════════════════════════════

    /**
     * Если предметы есть, но ни один рецепт не подошёл —
     * вспышка дыма и мелкие искры, чтобы игрок понял:
     * «состав не тот».
     */
    private static void spawnFailureParticles(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;

        serverLevel.sendParticles(
                ParticleTypes.SMOKE,
                cx, cy, cz,
                12, 0.3, 0.1, 0.3, 0.04
        );

        serverLevel.sendParticles(
                ParticleTypes.SMALL_FLAME,
                cx, cy, cz,
                4, 0.2, 0.05, 0.2, 0.02
        );
    }

    // ═══════════════════════════════════════════════
    //  Потребление предметов и спавн результата
    // ═══════════════════════════════════════════════

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

    /**
     * Создаёт ItemEntity с результатом рецепта над блоком.
     */
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
