package com.example.tunnelauger.handler;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.TunnelAugerItem;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
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
 * Считает накопанные блоки в {@link com.example.tunnelauger.item.AugerProgress},
 * а на уровне 1+ ломает блоки в области: 3×3, 5×5 или 7×7.
 * <p>
 * Направление копки зависит от взгляда игрока:
 * <ul>
 *   <li>Смотрит прямо (|pitch| &lt; 45°) — вертикальная стенка</li>
 *   <li>Смотрит вниз/вверх (|pitch| ≥ 45°) — горизонтальная площадка</li>
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
    private static final float PITCH_THRESHOLD = 45.0f;

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
        AugerProgress progress = stack.getOrDefault(ModComponents.AUGER_PROGRESS, AugerProgress.INITIAL);

        if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
            stack.set(ModComponents.AUGER_PROGRESS,
                    progress.withMinedBlocks(progress.minedBlocks() + 1));
        }

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

            float pitch = player.getXRot();
            int radius = (size - 1) / 2; // 3→1, 5→2, 7→3

            if (Math.abs(pitch) < PITCH_THRESHOLD) {
                breakVerticalWall(level, center, player, stack, incorrectTag, radius);
            } else {
                breakHorizontalFloor(level, center, player, stack, incorrectTag, radius);
            }
        } finally {
            isBreakingArea = false;
        }
    }

    private static void breakVerticalWall(Level level, BlockPos center, Player player, ItemStack stack, TagKey<Block> incorrectTag, int radius) {
        float yaw = ((player.getYRot() % 360) + 360) % 360;
        boolean widthAlongX;

        if (yaw >= 45 && yaw < 135) {
            widthAlongX = false;
        } else if (yaw >= 135 && yaw < 225) {
            widthAlongX = true;
        } else if (yaw >= 225 && yaw < 315) {
            widthAlongX = false;
        } else {
            widthAlongX = true;
        }

        boolean isCreative = player.getAbilities().instabuild;
        int blocksBroken = 0;

        for (int dVert = -radius; dVert <= radius; dVert++) {
            for (int dHoriz = -radius; dHoriz <= radius; dHoriz++) {
                if (dVert == 0 && dHoriz == 0) continue;
                if (stack.isEmpty()) return;

                BlockPos target = widthAlongX
                        ? center.offset(dHoriz, dVert, 0)
                        : center.offset(0, dVert, dHoriz);

                if (tryBreakAt(level, target, player, stack, incorrectTag, isCreative)) {
                    blocksBroken++;
                }
            }
        }

        // ── Износ: 1 прочность × количество сломанных блоков ──
        if (blocksBroken > 0) {
            stack.hurtAndBreak(blocksBroken, player, EquipmentSlot.MAINHAND);
        }
    }

    private static void breakHorizontalFloor(Level level, BlockPos center, Player player, ItemStack stack, TagKey<Block> incorrectTag, int radius) {
        boolean isCreative = player.getAbilities().instabuild;
        int blocksBroken = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (stack.isEmpty()) return;

                BlockPos target = center.offset(dx, 0, dz);
                if (tryBreakAt(level, target, player, stack, incorrectTag, isCreative)) {
                    blocksBroken++;
                }
            }
        }

        // ── Износ: 1 прочность × количество сломанных блоков ──
        if (blocksBroken > 0) {
            stack.hurtAndBreak(blocksBroken, player, EquipmentSlot.MAINHAND);
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
        AugerProgress prog = stack.getOrDefault(ModComponents.AUGER_PROGRESS, AugerProgress.INITIAL);
        stack.set(ModComponents.AUGER_PROGRESS, prog.withMinedBlocks(prog.minedBlocks() + 1));

        return true;
    }
}
