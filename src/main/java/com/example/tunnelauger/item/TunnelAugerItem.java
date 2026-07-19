package com.example.tunnelauger.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Кирка-бур с четырьмя уровнями. Сам класс — маркер для {@code instanceof},
 * вся логика копки живёт в {@link com.example.tunnelauger.handler.AugerMiningHandler}.
 * <p>
 * <b>Уровень 0 (железо)</b> — обычная кирка, считает блоки.<br>
 * <b>Уровень 1 (алмаз)</b> — площадная копка 3×3.<br>
 * <b>Уровень 2</b> — площадная копка 5×5.<br>
 * <b>Уровень 3</b> — площадная копка 7×7.
 *
 * <p>Вся тултип-информация вынесена в клиентский
 * {@code TunnelAugerTooltipHandler} — чтобы не смешивать данные
 * мода со встроенными строками Minecraft ("When in hand…").</p>
 */
public class TunnelAugerItem extends Item {

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

    private static final float[] LEVEL_SPEEDS = {6.0F, 8.0F, 9.0F, 10.0F};

    public TunnelAugerItem(ToolMaterial material, float attackDamage, float attackSpeed, Properties properties) {
        super(properties.pickaxe(material, attackDamage, attackSpeed));
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!state.is(MINEABLE_PICKAXE)) return 1.0F;

        AugerProgress progress = stack.get(ModComponents.AUGER_PROGRESS);
        int level = Math.min(progress != null ? progress.level() : 0, LEVEL_SPEEDS.length - 1);

        TagKey<Block> incorrect = level >= 1 ? INCORRECT_FOR_DIAMOND : INCORRECT_FOR_IRON;
        if (state.is(incorrect)) return 1.0F;

        return LEVEL_SPEEDS[level];
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (!state.is(MINEABLE_PICKAXE)) return false;

        AugerProgress progress = stack.get(ModComponents.AUGER_PROGRESS);
        int level = progress != null ? progress.level() : 0;
        TagKey<Block> incorrect = level >= 1 ? INCORRECT_FOR_DIAMOND : INCORRECT_FOR_IRON;
        return !state.is(incorrect);
    }

    // ═══════════════════════════════════════════════
    //  Разное
    // ═══════════════════════════════════════════════

    /**
     * Окрашивает имя предмета в цвет редкости (COMMON=серый, UNCOMMON=жёлтый,
     * RARE=голубой, EPIC=фиолетовый). Без этого переопределения имя всегда
     * серое, потому что Minecraft не применяет RARITY-компонент к имени,
     * заданному через Item.getName().
     */
    @Override
    public Component getName(ItemStack stack) {
        Rarity rarity = stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON);
        return Component.translatable(this.getDescriptionId()).withStyle(rarity.color());
    }

    /**
     * Базовая зачаровываемость — 0 (Item не имеет этого метода в 26.2).
     * Per-stack зачаровываемость управляется миксином
     * {@code TunnelAugerEnchantmentMixin}, который перехватывает
     * {@code ItemStack.getEnchantmentValue()}.
     */
}
