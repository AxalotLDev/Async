package com.axalotl.async.common.compat;

import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Keeps entity ticks that can interact with Sable sub-levels on the server thread.
 */
public final class SableCompatibility {
    private static final double COLLISION_QUERY_MARGIN = 2.0;

    private SableCompatibility() {
    }

    /**
     * Returns whether Sable can access sub-level state while ticking the entity.
     */
    public static boolean shouldTickSynchronously(Entity entity) {
        SableCompanion companion = SableCompanion.INSTANCE;
        if (companion.getContaining(entity) != null
                || companion.getTrackingOrVehicleSubLevel(entity) != null) {
            return true;
        }

        AABB entityBounds = entity.getBoundingBox();
        AABB movementBounds = entityBounds
                .minmax(entityBounds.move(entity.getDeltaMovement()))
                .inflate(COLLISION_QUERY_MARGIN);
        BoundingBox3d queryBounds = new BoundingBox3d(movementBounds);
        return companion.getAllIntersecting(entity.level(), queryBounds).iterator().hasNext();
    }
}
