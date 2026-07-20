package com.example.tunnelauger.client;

import java.util.List;

import com.example.tunnelauger.handler.AugerAreaShape;
import com.example.tunnelauger.handler.AugerMiningHandler;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Тонкая обводка блоков, которые выкопает бур в текущем режиме.
 *
 * <p>Обводится только то, что бур реально сломает — фильтр общий
 * с сервером ({@link AugerMiningHandler#isAreaMineable}): воздух, трава,
 * факелы и блоки не по тиру не подсвечиваются. Контур повторяет
 * фактическую форму блока (плиты, стены и т.п. не обводятся полным кубом),
 * а толщина линии сужается с расстоянием, чтобы вдали не казаться жирнее.</p>
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
 * <p>Не рисуется: на Tier 0, в режиме 1×1 (Shift+ПКМ),
 * без бура в руке, без блока под прицелом. При зажатом Shift обводка
 * видна — так переключение режима Shift+ПКМ даёт мгновенный отклик.</p>
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

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof TunnelAugerItem)) return;

        AugerProgress progress = stack.get(ModComponents.AUGER_PROGRESS);
        if (progress == null) return;

        int size = TunnelAugerItem.effectiveAreaSize(stack);
        if (size <= 1) return; // Tier 0 или выбран режим 1×1

        // Блок под прицелом
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) return;
        if (blockHit.getType() != HitResult.Type.BLOCK) return;

        BlockPos center = blockHit.getBlockPos();
        // Ориентация — по грани блока под прицелом (та же логика, что на сервере).
        AugerAreaShape.Orientation orientation =
                AugerAreaShape.orientationFor(blockHit.getDirection());

        List<BlockPos> area = AugerAreaShape.positions(center, orientation, (size - 1) / 2);
        int augerLevel = progress.level();

        // Гизмо, добавленные внутри этой коллекции, живут до следующего тика.
        try (var ignored = minecraft.collectPerTickGizmos()) {
            outlineBlock(minecraft, player, augerLevel, center); // центр — блок под прицелом
            for (BlockPos pos : area) {
                outlineBlock(minecraft, player, augerLevel, pos);
            }
        }
    }

    /**
     * Обводит блок, только если бур его реально сломает.
     * Контур — по фактической форме блока (getShape), а не полный куб.
     */
    private static void outlineBlock(Minecraft minecraft, Player player, int augerLevel, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        if (!AugerMiningHandler.isAreaMineable(minecraft.level, pos, state, augerLevel)) return;

        VoxelShape shape = state.getShape(minecraft.level, pos);
        if (shape.isEmpty()) return;

        // Чуть больше формы блока, чтобы линии не мерцали на гранях.
        AABB box = shape.bounds().move(pos).inflate(0.002);

        // Толщина штриха задаётся в пикселях экрана, поэтому издалека
        // линии выглядят толще относительно блоков — сужаем с расстоянием:
        // ~2 px вплотную, ~1 px на 12 блоках, минимум 0.5 px.
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - player.getY();
        double dz = pos.getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float width = (float) Math.clamp(12.0 / Math.max(dist, 1.0), 0.5, 2.0);

        Gizmos.cuboid(box, GizmoStyle.stroke(OUTLINE_COLOR, width));
    }
}
