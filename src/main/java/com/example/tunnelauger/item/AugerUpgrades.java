package com.example.tunnelauger.item;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;

/**
 * Всё, что связано с апгрейдом бура, в одном месте:
 * <ul>
 *   <li>стоимость ритуала для каждого уровня ({@link #costForLevel});</li>
 *   <li>ремонт-материал по тирам ({@link #repairItemForLevel});</li>
 *   <li>применение уровня к стаку ({@link #applyLevel}) — единая точка,
 *       которую используют и Философский камень, и креативная вкладка.</li>
 * </ul>
 */
public final class AugerUpgrades {

    /** Сердец моря на любой ритуал апгрейда. */
    public static final int HEARTS_REQUIRED = 1;

    /** Материальная часть стоимости ритуала. */
    public record Cost(Item material, int count) {
    }

    private AugerUpgrades() {
    }

    /**
     * Стоимость ритуала для перехода НА указанный уровень
     * (плюс {@link #HEARTS_REQUIRED} сердце моря).
     * Уровни: 0→1 (8 золота), 1→2 (8 алмазов), 2→3 (8 незеритовых слитков).
     *
     * @return null, если такого перехода нет
     */
    public static Cost costForLevel(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> new Cost(Items.GOLD_INGOT, 8);
            case 2 -> new Cost(Items.DIAMOND, 8);
            case 3 -> new Cost(Items.NETHERITE_INGOT, 8);
            default -> null;
        };
    }

    /**
     * Чем чинится бур на наковальне на указанном уровне:
     * железо → золото → алмаз → незеритовый слиток.
     */
    public static Item repairItemForLevel(int level) {
        return switch (level) {
            case 0 -> Items.IRON_INGOT;
            case 1 -> Items.GOLD_INGOT;
            case 2 -> Items.DIAMOND;
            default -> Items.NETHERITE_INGOT;
        };
    }

    /**
     * Применяет уровень к стаку: прогресс, визуальный уровень (модель),
     * редкость имени, максимальную прочность и ремонт-материал.
     * Единственная точка, где стак «переводится» на новый тир.
     */
    public static void applyLevel(ItemStack stack, AugerProgress newProgress) {
        int level = newProgress.level();
        stack.set(ModComponents.AUGER_PROGRESS, newProgress);
        stack.set(ModComponents.AUGER_LEVEL, level);
        stack.set(DataComponents.RARITY, AugerProgress.rarityForLevel(level));
        stack.set(DataComponents.MAX_DAMAGE, AugerProgress.durabilityForLevel(level));
        stack.set(DataComponents.REPAIRABLE, new Repairable(
                HolderSet.direct(repairItemForLevel(level).builtInRegistryHolder())));
    }
}
