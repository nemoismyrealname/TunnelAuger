package com.example.tunnelauger.client;

import java.util.List;

import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.TunnelAugerItem;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

/**
 * Клиентский обработчик тултипа для бура.
 * Вся информация о моде вставляется сразу после имени предмета (индекс 1),
 * чтобы она всегда была сверху, а встроенные строки Minecraft
 * ("When in hand…", компоненты и т.д.) — снизу.
 *
 * <p>В обычном состоянии (без Shift) показывается только уровень — строка
 * {@code line_level}. При зажатом Shift добавляются область копки и прогресс
 * апгрейда.</p>
 *
 * <p>Регистрируется в {@link TunnelAugerClient#onInitializeClient()}.</p>
 */
public final class TunnelAugerTooltipHandler {

    private TunnelAugerTooltipHandler() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register(TunnelAugerTooltipHandler::onTooltip);
    }

    private static void onTooltip(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                  net.minecraft.world.item.TooltipFlag flag, List<Component> lines) {
        if (!(stack.getItem() instanceof TunnelAugerItem)) return;

        AugerProgress progress = stack.get(ModComponents.AUGER_PROGRESS);
        if (progress == null) return;

        int augerLevel = progress.level();
        int mined = progress.minedBlocks();
        int need = AugerProgress.MINED_BLOCKS_FOR_UPGRADE;

        // ── Уровень (всегда, без Shift тоже) ───────
        // Вставляем после имени предмета (индекс 0 → вставляем на 1)
        lines.add(1, Component.translatable(
                "item.tunnel_auger.tunnel_auger.line_level", augerLevel));

        // ── Shift-блок: расширенная информация ───────
        if (!isShiftDown()) return;

        int insertIdx = 2; // после line_level

        // Область копки (Tier 1+)
        if (augerLevel >= 1) {
            int area = AugerProgress.areaSize(augerLevel);
            lines.add(insertIdx++, Component.translatable(
                    "item.tunnel_auger.tunnel_auger.line_area", area, area));
        }

        // Прогресс апгрейда
        if (augerLevel < AugerProgress.MAX_LEVEL) {
            lines.add(insertIdx++, Component.translatable(
                    "item.tunnel_auger.tunnel_auger.line_progress", mined, need));

            if (mined >= need) {
                lines.add(insertIdx++, Component.translatable(
                        "item.tunnel_auger.tunnel_auger.line_ready"));
            }
        } else {
            lines.add(insertIdx++, Component.translatable(
                    "item.tunnel_auger.tunnel_auger.line_progress_max", mined));
        }
    }

    private static boolean isShiftDown() {
        try {
            long handle = Minecraft.getInstance().getWindow().handle();
            return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == 1
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == 1;
        } catch (Exception e) {
            return false;
        }
    }
}
