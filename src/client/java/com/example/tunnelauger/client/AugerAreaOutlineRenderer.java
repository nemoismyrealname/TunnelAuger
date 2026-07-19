package com.example.tunnelauger.client;

import java.util.List;

import com.example.tunnelauger.handler.AugerAreaShape;
import com.example.tunnelauger.item.AugerProgress;
import com.example.tunnelauger.item.ModComponents;
import com.example.tunnelauger.item.TunnelAugerItem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Тонкая обводка блоков, которые выкопает бур в текущем режиме.
 *
 * <p>Обводится каждый <b>невоздушный</b> блок области отдельным
 * кубиком — воздух не подсвечивается, видно ровно то, что будет сломано.</p>
 *
 * <p>В 26.2 старый способ (LevelRenderer/ShapeRenderer.renderLineBox +
 * RenderType.lines()) удалён вместе с переработкой рендера.
 * Вместо него используем новый ванильный API гизмо:
 * {@link Minecraft#collectPerTickGizmos()} + {@link Gizmos#cuboid} —
 * гизмо, добавленные за тик, рисуются штатным GizmoFeatureRenderer.</p>
 *
 * <p>Ориентация и набор позиций берутся из {@link AugerAreaShape} — той же
 * логики, что использует сервер при копке, поэтому обводка всегда
 * совпадает с реально выкапываемой областью.</p>
 *
 * <p>Не рисуется: при Shift (площадная копка отключена), на Tier 0
 * (области ещё нет), без бура в руке, без блока под прицелом.</p>
 */
public final class AugerAreaOutlineRenderer {

    /** Чёрная полупрозрачная (ARGB) — в стиле ванильной обводки блока. */
    private static final int OUTLINE_COLOR = 0x66000000;

    private AugerAreaOutlineRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AugerAreaOutlineRenderer::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) return;
        if (player.isShiftKeyDown()) return; // Shift = точная копка, области нет

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof TunnelAugerItem)) return;

        AugerProgress progress = stack.get(ModComponents.AUGER_PROGRESS);
        if (progress == null) return;

        int size = AugerProgress.areaSize(progress.level());
        if (size <= 1) return; // Tier 0 — обычная кирка

        // Блок под прицелом
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) return;
        if (blockHit.getType() != HitResult.Type.BLOCK) return;

        BlockPos center = blockHit.getBlockPos();
        AugerAreaShape.Orientation orientation =
                AugerAreaShape.orientationFor(player.getYRot(), player.getXRot());

        List<BlockPos> area = AugerAreaShape.positions(center, orientation, (size - 1) / 2);
        GizmoStyle style = GizmoStyle.stroke(OUTLINE_COLOR);

        // Гизмо, добавленные внутри этой коллекции, живут до следующего тика.
        try (var ignored = minecraft.collectPerTickGizmos()) {
            outlineIfNotAir(minecraft, center, style); // центр — блок под прицелом
            for (BlockPos pos : area) {
                outlineIfNotAir(minecraft, pos, style);
            }
        }
    }

    /** Обводит блок, только если он не воздух. */
    private static void outlineIfNotAir(Minecraft minecraft, BlockPos pos, GizmoStyle style) {
        if (minecraft.level.getBlockState(pos).isAir()) return;
        // Чуть больше блока, чтобы линии не мерцали на гранях.
        Gizmos.cuboid(new AABB(pos).inflate(0.002), style);
    }
}
