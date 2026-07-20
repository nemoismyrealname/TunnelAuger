package com.example.tunnelauger.handler;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.TunnelAugerItem;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fabric-событие, которое срабатывает после разрушения блока игроком.
 * <p>
 * Считает накопанные блоки в {@link AugerProgress},
 * а на уровне 1+ ломает блоки в области: 3×3, 5×5 или 7×7.
 * Геометрия области (ориентация по грани блока, список позиций) вынесена
 * в {@link AugerAreaShape} — её же использует клиентская обводка области.
 * <p>
 * QoL-правила:
 * <ul>
 *   <li><b>Shift+ПКМ</b> циклически переключает размер области:
 *       1 → 3×3 → 5×5 → 7×7 (до максимума тира) —
 *       точная копка одним блоком = режим 1×1;</li>
 *   <li>бур <b>никогда не ломается</b> от площадной копки: когда остаётся
 *       1 прочности — область перестаёт копаться (износ при этом
 *       честный: 1 прочность за каждый сломанный блок);</li>
 *   <li>при достижении порога апгрейда — только короткий звук, без текста на экране.</li>
 * </ul>
 * В области копаются только блоки из тега {@code #minecraft:mineable/pickaxe}.
 * В креативе дроп из area mining не выпадает.
 * Рекурсия предотвращается флагом {@link #isBreakingArea}.
 */
public final class AugerMiningHandler {

    private static final TagKey<Block> INCORRECT_FOR_IRON = TagKey.create(
            BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath("minecraft", "incorrect_for_iron_tool")
    );

    private static final TagKey<Block> INCORRECT_FOR_DIAMOND = TagKey.create(
            BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath("minecraft", "incorrect_for_diamond_tool")
    );

    private static final TagKey<Block> MINEABLE_PICKAXE = TagKey.create(
            BuiltInRegistries.BLOCK.key(),
            Identifier.fromNamespaceAndPath("minecraft", "mineable/pickaxe")
    );

    private static boolean isBreakingArea = false;

    private AugerMiningHandler() {
    }

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide()) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof TunnelAugerItem)) return;

            handleBreak(level, pos, state, player, stack);
        });
    }

    private static void handleBreak(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
            addProgress(stack, player, 1);
        }

        AugerProgress progress = stack.getOrDefault(ModComponents.AUGER_PROGRESS, AugerProgress.INITIAL);
        // Размер области = выбранный режим (Shift+ПКМ), не выше максимума тира.
        int areaSize = TunnelAugerItem.effectiveAreaSize(stack);
        if (areaSize > 1) {
            breakArea(level, pos, player, stack, progress, areaSize);
        }
    }

    /**
     * Копает область {@code size×size} вокруг центра.
     * Размер зависит от уровня: 1→3×3, 2→5×5, 3→7×7.
     */
    private static void breakArea(Level level, BlockPos center, Player player, ItemStack stack, AugerProgress progress, int size) {
        if (isBreakingArea) return;
        isBreakingArea = true;

        try {
            boolean isCreative = player.getAbilities().instabuild;

            AugerAreaShape.Orientation orientation = orientationFor(player, center);
            int radius = (size - 1) / 2; // 3→1, 5→2, 7→3

            int blocksBroken = 0;
            for (BlockPos target : AugerAreaShape.positions(center, orientation, radius)) {
                // ── Бур никогда не ломается от площадной копки:
                //     когда остаётся 1 прочности — останавливаемся ──
                if (!isCreative && stack.getMaxDamage() - stack.getDamageValue() <= 1) break;

                if (tryBreakAt(level, target, player, stack, progress.level(), isCreative)) {
                    blocksBroken++;
                    // ── Износ за КАЖДЫЙ блок отдельно — Unbreaking бросает
                    //     кубик на каждый блок независимо ──
                    if (!isCreative) {
                        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    }
                }
            }

            // ── Ванильное истощение голода: 0.005 за блок (без случайности) ──
            if (blocksBroken > 0 && !isCreative) {
                player.causeFoodExhaustion(0.005f * blocksBroken);
            }
        } finally {
            isBreakingArea = false;
        }
    }

    /**
     * Ориентация области по грани центрального блока, в которую бил игрок.
     * <p>Событие Fabric грань не сообщает, а сам блок к этому моменту уже
     * сломан (рейтрейс мира пролетит насквозь) — поэтому грань
     * восстанавливается геометрически: через какую грань куба входит луч
     * взгляда. Если луч мимо (лаг, рассинхрон) — фолбэк по углам взгляда.</p>
     */
    private static AugerAreaShape.Orientation orientationFor(Player player, BlockPos center) {
        Direction face = lookEntryFace(player.getEyePosition(), player.getViewVector(1.0f), center);
        if (face != null) return AugerAreaShape.orientationFor(face);
        return AugerAreaShape.orientationFor(player.getYRot(), player.getXRot());
    }

    /** Грань куба {@code pos}, через которую входит луч; {@code null} — луч мимо куба. */
    private static Direction lookEntryFace(Vec3 eye, Vec3 view, BlockPos pos) {
        double tEnter = Double.NEGATIVE_INFINITY;
        double tExit = Double.POSITIVE_INFINITY;
        Direction face = null;

        double[] mins = {pos.getX(), pos.getY(), pos.getZ()};
        double[] eyes = {eye.x, eye.y, eye.z};
        double[] dirs = {view.x, view.y, view.z};
        Direction[][] entryFaces = {
                {Direction.WEST, Direction.EAST},   // луч идёт по +X / по −X
                {Direction.DOWN, Direction.UP},
                {Direction.NORTH, Direction.SOUTH},
        };

        // Slab-метод: пересечение луча с единичным кубом по трём осям.
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(dirs[axis]) < 1.0e-7) {
                if (eyes[axis] < mins[axis] || eyes[axis] > mins[axis] + 1) return null;
                continue;
            }
            double t1 = (mins[axis] - eyes[axis]) / dirs[axis];
            double t2 = (mins[axis] + 1 - eyes[axis]) / dirs[axis];
            Direction entry = dirs[axis] > 0 ? entryFaces[axis][0] : entryFaces[axis][1];
            double tNear = Math.min(t1, t2);
            double tFar = Math.max(t1, t2);
            if (tNear > tEnter) {
                tEnter = tNear;
                face = entry;
            }
            tExit = Math.min(tExit, tFar);
        }

        if (face == null || tEnter > tExit || tEnter < 0) return null;
        return face;
    }

    /**
     * Может ли бур указанного тира выкопать блок площадной копкой:
     * не воздух, не «неразрушимый» (бедрок), без block entity
     * (печи, спавнеры, шалкеры — только прицельно), копается киркой
     * и подходит по тиру инструмента.
     * <p>Единая точка правды для серверной копки И клиентской обводки —
     * обводка всегда показывает ровно те блоки, что будут сломаны.</p>
     */
    public static boolean isAreaMineable(Level level, BlockPos pos, BlockState state, int augerLevel) {
        if (state.isAir()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        // ── Функциональные блоки (печи, спавнеры, шалкеры, воронки…)
        //     областью не ломаются — только прицельно, центральным блоком ──
        if (state.hasBlockEntity()) return false;
        if (!state.is(MINEABLE_PICKAXE)) return false;
        TagKey<Block> incorrectTag = augerLevel >= 1 ? INCORRECT_FOR_DIAMOND : INCORRECT_FOR_IRON;
        return !state.is(incorrectTag);
    }

    private static boolean tryBreakAt(Level level, BlockPos pos, Player player, ItemStack stack, int augerLevel, boolean isCreative) {
        BlockState state = level.getBlockState(pos);
        if (!isAreaMineable(level, pos, state, augerLevel)) return false;

        if (!isCreative && level instanceof ServerLevel serverLevel) {
            // ── Используем стак игрока, чтобы зачарования (Fortune, Silk Touch)
            //     применялись к дропу. Стандартный destroyBlock() использует
            //     ItemStack.EMPTY — без зачарований.
            //     NB: Block.dropResources() сам вызывает state.spawnAfterBreak() —
            //     не дублировать!
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            Block.dropResources(state, serverLevel, pos, blockEntity, player, stack);
            level.removeBlock(pos, false);
        } else {
            level.destroyBlock(pos, false, player);
        }

        // ── Считаем блок в прогресс апгрейда ──
        addProgress(stack, player, 1);

        return true;
    }

    /**
     * Единая точка начисления прогресса. При пересечении порога
     * апгрейда — однократный звуковой сигнал (без текста на экране).
     */
    private static void addProgress(ItemStack stack, Player player, int amount) {
        AugerProgress before = stack.getOrDefault(ModComponents.AUGER_PROGRESS, AugerProgress.INITIAL);
        AugerProgress after = before.withMinedBlocks(before.minedBlocks() + amount);
        stack.set(ModComponents.AUGER_PROGRESS, after);

        if (!before.canUpgrade() && after.canUpgrade()) {
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 0.6f);
        }
    }
}
