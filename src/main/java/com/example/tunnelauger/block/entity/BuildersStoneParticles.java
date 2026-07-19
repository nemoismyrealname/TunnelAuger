package com.example.tunnelauger.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Все визуальные эффекты (частицы) для Философского камня строителя.
 * <p>
 * Вынесены из {@link BuildersStoneBlockEntity}, чтобы не загромождать
 * логику ритуала и улучшения бура.
 */
public final class BuildersStoneParticles {

    private BuildersStoneParticles() {
    }

    /** Спираль зачарования + вспышка + дым — успешный крафт. */
    public static void spawnSuccess(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;

        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            serverLevel.sendParticles(
                    ParticleTypes.ENCHANT, cx + Math.cos(rad) * 0.5, cy, cz + Math.sin(rad) * 0.5,
                    1, 0.0, 0.3, 0.0, 0.15
            );
        }

        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, cx, cy + 0.3, cz, 12, 0.2, 0.1, 0.2, 0.3);
        serverLevel.sendParticles(ParticleTypes.SMOKE, cx, cy + 0.2, cz, 8, 0.4, 0.1, 0.4, 0.02);
    }

    /** Дым + искры — рецепт не подходит. */
    public static void spawnFailure(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;

        serverLevel.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 12, 0.3, 0.1, 0.3, 0.04);
        serverLevel.sendParticles(ParticleTypes.SMALL_FLAME, cx, cy, cz, 4, 0.2, 0.05, 0.2, 0.02);
    }

    /** Спираль + END_ROD — апгрейд бура. */
    public static void spawnUpgrade(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;

        for (int deg = 0; deg < 360; deg += 10) {
            double rad = Math.toRadians(deg);
            serverLevel.sendParticles(
                    ParticleTypes.ENCHANT,
                    cx + Math.cos(rad) * 0.6,
                    cy + Math.sin(rad * 2) * 0.3,
                    cz + Math.sin(rad) * 0.6,
                    1, 0.0, 0.2, 0.0, 0.15
            );
        }

        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, cx, cy + 0.3, cz, 20, 0.3, 0.2, 0.3, 0.4);
        serverLevel.sendParticles(ParticleTypes.SMOKE, cx, cy + 0.2, cz, 12, 0.4, 0.1, 0.4, 0.02);
        serverLevel.sendParticles(ParticleTypes.END_ROD, cx, cy + 0.5, cz, 8, 0.3, 0.2, 0.3, 0.05);
    }

    /** Фоновая искорка зачарования для клиента. */
    public static void spawnAmbient(Level level, BlockPos pos) {
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
}
