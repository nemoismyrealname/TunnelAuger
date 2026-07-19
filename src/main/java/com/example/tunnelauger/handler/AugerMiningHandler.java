package com.example.tunnelauger.handler;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.TunnelAugerItem;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
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

/**
 * Fabric-событие, которое срабатывает после разрушения блока игроком.
 * <p>
 * Считает накопанные блоки в {@link AugerProgress},
 * а на уровне 1+ ломает блоки в области: 3×3, 5×5 или 7×7.
 * Геометрия области (ориентация по взгляду, список позиций) вынесена
 * в {@link AugerAreaShape} — её же использует клиентская обводка области.
 * <p>
 * QoL-правила:
 * <ul>
 *   <li><b>Shift</b> отключает площадную копку — точная работа одним блоком;</li>
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

        // ── Shift = точная копка одним блоком, без области ──
        if (player.isShiftKeyDown()) return;

        AugerProgress progress = stack.getOrDefault(ModComponents.AUGER_PROGRESS, AugerProgress.INITIAL);
        int areaSize = AugerProgress.areaSize(progress.level());
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
            TagKey<Block> incorrectTag = progress.level() >= 1
                    ? INCORRECT_FOR_DIAMOND
                    : INCORRECT_FOR_IRON;

            boolean isCreative = player.getAbilities().instabuild;

            // ── Бюджет прочности: оставляем минимум 1, чтобы бур
            //     никогда не ломался от площадной копки ──
            int budget = isCreative
                    ? Integer.MAX_VALUE
                    : stack.getMaxDamage() - stack.getDamageValue() - 1;
            if (budget <= 0) return;

            AugerAreaShape.Orientation orientation =
                    AugerAreaShape.orientationFor(player.getYRot(), player.getXRot());
            int radius = (size - 1) / 2; // 3→1, 5→2, 7→3

            int blocksBroken = 0;
            for (BlockPos target : AugerAreaShape.positions(center, orientation, radius)) {
                if (blocksBroken >= budget) break;
                if (tryBreakAt(level, target, player, stack, incorrectTag, isCreative)) {
                    blocksBroken++;
                }
            }

            // ── Износ: 1 прочность × количество сломанных блоков,
            //     плюс ванильное истощение голода (0.005 за блок, как в ванили) ──
            if (blocksBroken > 0 && !isCreative) {
                stack.hurtAndBreak(blocksBroken, player, EquipmentSlot.MAINHAND);
                player.causeFoodExhaustion(0.005f * blocksBroken);
            }
        } finally {
            isBreakingArea = false;
        }
    }

    private static boolean tryBreakAt(Level level, BlockPos pos, Player player, ItemStack stack, TagKey<Block> incorrectTag, boolean isCreative) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;

        if (!state.is(MINEABLE_PICKAXE)) return false;
        if (state.is(incorrectTag)) return false;

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
