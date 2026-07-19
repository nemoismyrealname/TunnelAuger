package com.example.tunnelauger.handler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Геометрия площадной копки, общая для сервера и клиента:
 * <ul>
 *   <li>{@link AugerMiningHandler} берёт отсюда список позиций для разрушения;</li>
 *   <li>клиентский {@code AugerAreaOutlineRenderer} — габариты области для обводки.</li>
 * </ul>
 * Так сервер и превью на клиенте гарантированно совпадают.
 */
public final class AugerAreaShape {

    /** |pitch| меньше порога — вертикальная стенка, иначе — горизонтальная площадка. */
    public static final float PITCH_THRESHOLD = 45.0f;

    public enum Orientation {
        /** Стенка, ширина вдоль оси X (игрок смотрит на север/юг). */
        WALL_X,
        /** Стенка, ширина вдоль оси Z (игрок смотрит на запад/восток). */
        WALL_Z,
        /** Горизонтальная площадка (игрок смотрит вверх/вниз). */
        FLOOR
    }

    private AugerAreaShape() {
    }

    public static Orientation orientationFor(float yawDeg, float pitchDeg) {
        if (Math.abs(pitchDeg) >= PITCH_THRESHOLD) return Orientation.FLOOR;

        float yaw = ((yawDeg % 360) + 360) % 360;
        if (yaw >= 45 && yaw < 135) return Orientation.WALL_Z;   // запад/восток
        if (yaw >= 135 && yaw < 225) return Orientation.WALL_X;  // север
        if (yaw >= 225 && yaw < 315) return Orientation.WALL_Z;
        return Orientation.WALL_X;                               // юг
    }

    /** Все позиции области, кроме центральной (центр ломает сам игрок). */
    public static List<BlockPos> positions(BlockPos center, Orientation orientation, int radius) {
        List<BlockPos> result = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                if (a == 0 && b == 0) continue;
                result.add(switch (orientation) {
                    case WALL_X -> center.offset(b, a, 0);
                    case WALL_Z -> center.offset(0, a, b);
                    case FLOOR -> center.offset(a, 0, b);
                });
            }
        }
        return result;
    }

    /**
     * Габариты всей области одним боксом — для тонкой клиентской обводки.
     * NB: если {@code AABB.encapsulatingFullBlocks} не резолвится в этой
     * версии — заменить на ручной {@code new AABB(min).minmax(new AABB(max))}.
     */
    public static AABB bounds(BlockPos center, Orientation orientation, int radius) {
        BlockPos min = switch (orientation) {
            case WALL_X -> center.offset(-radius, -radius, 0);
            case WALL_Z -> center.offset(0, -radius, -radius);
            case FLOOR -> center.offset(-radius, 0, -radius);
        };
        BlockPos max = switch (orientation) {
            case WALL_X -> center.offset(radius, radius, 0);
            case WALL_Z -> center.offset(0, radius, radius);
            case FLOOR -> center.offset(radius, 0, radius);
        };
        return AABB.encapsulatingFullBlocks(min, max);
    }
}
