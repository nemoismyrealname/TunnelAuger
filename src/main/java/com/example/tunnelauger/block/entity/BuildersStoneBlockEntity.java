package com.example.tunnelauger.block.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.AugerUpgrades;
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
 *   <li>Проверяет рецепты крафта ({@link RitualRecipes} — грузятся из JSON).</li>
 *   <li>Если нет — проверяет апгрейд бура (стоимость — в {@link AugerUpgrades}).</li>
 *   <li>Негативный отклик (частицы + звук) — <b>однократно</b>, только в момент,
 *       когда на камень попадает предмет, не участвующий ни в одном рецепте
 *       и ни в одном ритуале апгрейда (см. {@link #signalWrongItems}).</li>
 * </ol>
 *
 * <p><b>Клиент (каждый тик):</b> фоновые искорки зачарования.
 *
 * <p>Предметы к камню <b>не притягиваются</b> — ванильное поведение дропа.
 * Частицы вынесены в {@link BuildersStoneParticles}.
 */
public class BuildersStoneBlockEntity extends BlockEntity {

    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final int COOLDOWN_TICKS = 40;

    private int ticksSinceCheck = 0;
    private int cooldownTicks = 0;

    /**
     * Типы «неправильных» предметов, на которые отклик уже был дан.
     * Когда предмет убирают с камня — тип забывается, и повторный дроп
     * снова даст однократный отклик.
     */
    private final Set<Item> signaledWrongItems = new HashSet<>();

    public BuildersStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUILDERS_STONE_ENTITY, pos, state);
    }

    // ═══════════════════════════════════════════
    //  Главный тик
    // ═══════════════════════════════════════════

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
        if (items.isEmpty()) {
            entity.signaledWrongItems.clear();
            return;
        }

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

        // 3. Однократный негативный отклик на чужеродные предметы
        entity.signalWrongItems(level, pos, items);
    }

    // ═══════════════════════════════════════════
    //  Сканирование предметов
    // ═══════════════════════════════════════════

    /**
     * Зона ритуала — вся верхняя грань камня с запасом по краям.
     * Без магнита предметы лежат там, куда упали, поэтому зона покрывает
     * весь верх блока (включая предметы, свесившиеся за кромку),
     * но не землю рядом с камнем.
     */
    private static List<ItemEntity> scanItems(Level level, BlockPos pos) {
        AABB scanArea = new AABB(
                pos.getX() - 0.5, pos.getY() + 0.5, pos.getZ() - 0.5,
                pos.getX() + 1.5, pos.getY() + 2.0, pos.getZ() + 1.5);
        return level.getEntitiesOfClass(ItemEntity.class, scanArea);
    }

    // ═══════════════════════════════════════════
    //  Рецепты
    // ═══════════════════════════════════════════

    private static boolean tryAnyRecipe(Level level, BlockPos pos, List<ItemEntity> items) {
        for (RitualRecipe recipe : RitualRecipes.all()) {
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

    // ═══════════════════════════════════════════
    //  Апгрейд бура
    // ═══════════════════════════════════════════

    /**
     * Уровни: 0→1 (♥ + 8 золота), 1→2 (♥ + 8 алмазов),
     * 2→3 (♥ + 8 незеритовых слитков).
     * Стоимость живёт в {@link AugerUpgrades#costForLevel}.
     *
     * <p>Если бур ещё не накопал нужное число блоков или материалов
     * не хватает — просто ничего не происходит (без шипения: все
     * участники ритуала — «правильные» предметы).</p>
     *
     * @return true, если апгрейд выполнен
     */
    private static boolean tryUpgrade(Level level, BlockPos pos, List<ItemEntity> entities) {
        if (!(level instanceof ServerLevel)) return false;

        AugerTarget target = findAugerTarget(entities);
        if (target == null) return false;

        AugerProgress progress = target.progress();
        if (!progress.canUpgrade()) return false;

        int nextLevel = progress.level() + 1;
        AugerUpgrades.Cost cost = AugerUpgrades.costForLevel(nextLevel);
        if (cost == null) return false;

        if (!hasUpgradeMaterials(entities, cost)) return false;

        consumeUpgradeMaterials(entities, cost);

        // ── Применяем апгрейд (через setItem чтобы обновить визуал) ──
        ItemStack augerStack = target.entity().getItem().copy();
        AugerUpgrades.applyLevel(augerStack, progress.nextLevel());
        target.entity().setItem(augerStack);

        BuildersStoneParticles.spawnUpgrade(level, pos);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0f, 1.2f);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.0f);
        return true;
    }

    private record AugerTarget(ItemEntity entity, AugerProgress progress) {
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

    private static boolean hasUpgradeMaterials(List<ItemEntity> entities, AugerUpgrades.Cost cost) {
        int hearts = 0;
        int mats = 0;
        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            ItemStack stack = e.getItem();
            if (stack.is(Items.HEART_OF_THE_SEA)) {
                hearts += stack.getCount();
            } else if (stack.is(cost.material())) {
                mats += stack.getCount();
            }
        }
        return hearts >= AugerUpgrades.HEARTS_REQUIRED && mats >= cost.count();
    }

    private static void consumeUpgradeMaterials(List<ItemEntity> entities, AugerUpgrades.Cost cost) {
        int heartNeeded = AugerUpgrades.HEARTS_REQUIRED;
        int matNeeded = cost.count();

        for (ItemEntity e : entities) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            if (heartNeeded <= 0 && matNeeded <= 0) break;

            ItemStack stack = e.getItem();

            if (stack.is(Items.HEART_OF_THE_SEA) && heartNeeded > 0) {
                int take = Math.min(heartNeeded, stack.getCount());
                stack.shrink(take);
                heartNeeded -= take;
                if (stack.isEmpty()) e.discard();
            } else if (stack.is(cost.material()) && matNeeded > 0) {
                int take = Math.min(matNeeded, stack.getCount());
                stack.shrink(take);
                matNeeded -= take;
                if (stack.isEmpty()) e.discard();
            }
        }
    }

    // ═══════════════════════════════════════════
    //  Однократный негативный отклик
    // ═══════════════════════════════════════════

    /**
     * Шипит частицами + звуком <b>один раз</b> — только когда на камне
     * появляется новый тип предмета, не участвующий ни в одном рецепте
     * и ни в одном ритуале. Пока предмет лежит — камень молчит;
     * убрали и бросили снова — отклик повторится.
     */
    private void signalWrongItems(Level level, BlockPos pos, List<ItemEntity> items) {
        Set<Item> present = new HashSet<>();
        for (ItemEntity e : items) {
            if (!e.isAlive() || e.getItem().isEmpty()) continue;
            present.add(e.getItem().getItem());
        }

        // Забываем типы, которых на камне больше нет.
        signaledWrongItems.retainAll(present);

        boolean newWrongItem = false;
        for (Item item : present) {
            if (isKnownRitualItem(item)) continue;
            if (signaledWrongItems.add(item)) {
                newWrongItem = true;
            }
        }

        if (newWrongItem) {
            BuildersStoneParticles.spawnFailure(level, pos);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 0.8f);
        }
    }

    /**
     * «Правильный» предмет — участник хотя бы одного рецепта (ингредиент
     * или результат) либо ритуала апгрейда (бур, сердце моря, материалы).
     */
    private static boolean isKnownRitualItem(Item item) {
        if (item == ModItems.TUNNEL_AUGER) return true;
        if (item == Items.HEART_OF_THE_SEA) return true;

        for (int lvl = 1; lvl <= AugerProgress.MAX_LEVEL; lvl++) {
            AugerUpgrades.Cost cost = AugerUpgrades.costForLevel(lvl);
            if (cost != null && cost.material() == item) return true;
        }

        for (RitualRecipe recipe : RitualRecipes.all()) {
            if (recipe.ingredients().containsKey(item)) return true;
            if (recipe.result().getItem() == item) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════
    //  Клиентские частицы
    // ═══════════════════════════════════════════

    private void clientParticles(Level level, BlockPos pos) {
        if (level.getRandom().nextInt(20) != 0) return;

        boolean hasItems = !scanItems(level, pos).isEmpty();

        // Базовая искорка + вторая с шансом 50%, когда на камне лежат предметы
        // (камень «оживает» активнее).
        BuildersStoneParticles.spawnAmbient(level, pos);
        if (hasItems && level.getRandom().nextBoolean()) {
            BuildersStoneParticles.spawnAmbient(level, pos);
        }
    }
}
