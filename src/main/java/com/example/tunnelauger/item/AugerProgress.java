package com.example.tunnelauger.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Прогресс конкретного экземпляра бура: текущий уровень и количество
 * блоков, выкопанных на этом уровне. Хранится как Data Component прямо
 * на ItemStack — сериализуется вместе с предметом автоматически,
 * вручную читать/писать NBT не нужно.
 *
 * withMinedBlocks/nextLevel пока не используются — понадобятся в
 * обработчике копки и в апгрейд-ритуале на следующих шагах.
 */
public record AugerProgress(int level, int minedBlocks) {

    public static final AugerProgress INITIAL = new AugerProgress(0, 0);

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
}
