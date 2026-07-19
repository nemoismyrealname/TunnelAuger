package com.example.tunnelauger.mixin;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.TunnelAugerItem;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перехватывает {@link Enchantment#canEnchant(ItemStack)} для
 * {@link TunnelAugerItem}: возвращает {@code false} для Tier 0–2
 * (зачаровательный стол).
 */
@Mixin(Enchantment.class)
public class TunnelAugerEnchantmentMixin {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void tunnelAuger_canEnchant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof TunnelAugerItem)) return;

        AugerProgress p = stack.get(ModComponents.AUGER_PROGRESS);
        if (p == null || p.level() < AugerProgress.MAX_LEVEL) {
            cir.setReturnValue(false);
        }
    }
}
