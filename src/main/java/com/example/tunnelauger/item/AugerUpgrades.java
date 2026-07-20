package com.example.tunnelauger.item;

import java.util.List;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;

/**
 * Всё, что связано с апгрейдом бура, в одном месте:
 * <ul>
 *   <li>варианты стоимости ритуала для каждого уровня ({@link #costsForLevel});</li>
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
     * Варианты материальной стоимости ритуала для перехода НА указанный
     * уровень (плюс {@link #HEARTS_REQUIRED} сердце моря). Для апгрейда
     * достаточно ЛЮБОГО одного варианта целиком; смешивать варианты нельзя.
     * <ul>
     *   <li>0→1: 18 алмазов ИЛИ 2 алмазных блока;</li>
     *   <li>1→2: 8 незеритовых обломков ИЛИ 2 незеритовых слитка;</li>
     *   <li>2→3: 8 незеритовых слитков.</li>
     * </ul>
     *
     * @return пустой список, если такого перехода нет
     */
    public static List<Cost> costsForLevel(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new Cost(Items.DIAMOND, 18), new Cost(Items.DIAMOND_BLOCK, 2));
            case 2 -> List.of(new Cost(Items.NETHERITE_SCRAP, 8), new Cost(Items.NETHERITE_INGOT, 2));
            case 3 -> List.of(new Cost(Items.NETHERITE_INGOT, 8));
            default -> List.of();
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
