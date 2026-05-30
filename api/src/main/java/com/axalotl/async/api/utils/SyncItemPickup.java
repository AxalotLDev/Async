package com.axalotl.async.api.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Sync wrap for custom pickUpItem method
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * protected void pickUpItem(final ServerLevel level, final ItemEntity entity) {
 *      SyncItemPickup.wrap(level, entity, () -> {
 *          this.onItemPickup(entity);
 *          PiglinAi.pickUpItem(level, this, entity);
 *      }
 * }}</pre>
 */
public class SyncItemPickup {
    /**
     * Hidden constructor to prevent instantiation.
     */
    private SyncItemPickup() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Executes the provided pickup logic in a synchronized context.
     *
     * <p>If the given {@link ItemEntity} has already been removed, the action
     * will not be executed.</p>
     *
     * @param level the server level where the pickup occurs
     * @param entity the item entity being picked up
     * @param action the pickup logic to execute safely
     */
    public static void wrap(ServerLevel level, ItemEntity entity, Runnable action) {
        synchronized (entity) {
            if (!entity.isRemoved()) {
                action.run();
            }
        }
    }
}