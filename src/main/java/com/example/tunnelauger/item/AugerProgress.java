package com.example.tunnelauger.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.Rarity;

/**
 * Прогресс конкретного экземпляра бура: текущий уровень и количество
 * блоков, выкопанных на этом уровне. Хранится как Data Component прямо
 * на ItemStack — сериализуется вместе с предметом автоматически,
 * вручную читать/писать NBT не нужно.
 *
 * <p>Уровни: 0 → 1 → 2 → 3 (макс). Для апгрейда нужно накопать
 * {@link #MINED_BLOCKS_FOR_UPGRADE} блоков и провести ритуал
 * на Философском камне строителя.</p>
 */
public record AugerProgress(int level, int minedBlocks) {

    public static final AugerProgress INITIAL = new AugerProgress(0, 0);

    /** Сколько блоков нужно накопать для апгрейда на каждом уровне. */
    public static final int MINED_BLOCKS_FOR_UPGRADE = 50;

    /** Максимальный уровень бура. */
    public static final int MAX_LEVEL = 3;

    /** Размер области копки по уровню: level → (radius, area) */
    public static int areaSize(int level) {
        return switch (level) {
            case 1 -> 3;  // 3×3
            case 2 -> 5;  // 5×5
            case 3 -> 7;  // 7×7
            default -> 1; // нет копки области
        };
    }

    public static final Codec<AugerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(AugerProgress::level),
            Codec.INT.fieldOf("mined_blocks").forGetter(AugerProgress::minedBlocks)
    ).apply(instance, AugerProgress::new));

    public AugerProgress withMinedBlocks(int newMinedBlocks) {
        return new AugerProgress(level, newMinedBlocks);
    }

    public AugerProgress nextLevel() {
        return new AugerProgress(level + 1, 0);
    }

    /**
     * Редкость для указанного уровня бура.
     * Lv.0=COMMON, Lv.1=UNCOMMON, Lv.2=RARE, Lv.3=EPIC.
     */
    public static Rarity rarityForLevel(int level) {
        return switch (level) {
            case 1 -> Rarity.UNCOMMON;
            case 2 -> Rarity.RARE;
            case 3 -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    /**
     * true, если бур готов к улучшению: накоплено достаточно блоков
     * и это не максимальный уровень.
     */
    public boolean canUpgrade() {
        return level < MAX_LEVEL && minedBlocks >= MINED_BLOCKS_FOR_UPGRADE;
    }
}
