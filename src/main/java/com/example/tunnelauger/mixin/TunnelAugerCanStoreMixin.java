package com.example.tunnelauger.mixin;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.TunnelAugerItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перехватывает {@link EnchantmentHelper#canStoreEnchantments(ItemStack)}
 * для {@link TunnelAugerItem}: возвращает {@code false} для Tier 0–2.
 *
 * <p>Наковальня вызывает этот метод перед применением книги —
 * без него {@link TunnelAugerEnchantmentMixin} не блокирует книги.</p>
 */
@Mixin(EnchantmentHelper.class)
public class TunnelAugerCanStoreMixin {

    @Inject(method = "canStoreEnchantments", at = @At("HEAD"), cancellable = true)
    private static void tunnelAuger_canStoreEnchantments(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof TunnelAugerItem)) return;

        AugerProgress p = stack.get(ModComponents.AUGER_PROGRESS);
        if (p == null || p.level() < AugerProgress.MAX_LEVEL) {
            cir.setReturnValue(false);
        }
    }
}
