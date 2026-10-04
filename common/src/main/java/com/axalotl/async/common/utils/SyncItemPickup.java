package com.axalotl.async.common.utils;

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
 *
 * <p>Internal to the mod: must live in {@code common} (compiled directly into
 * the mod jar's game-layer classloader) rather than the published {@code api}
 * module, which is embedded via jarJar as a separate library and does not
 * have visibility into mixin-patched {@code net.minecraft} classes.</p>
 */
public final class SyncItemPickup {
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
     * @param level  the server level where the pickup occurs
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