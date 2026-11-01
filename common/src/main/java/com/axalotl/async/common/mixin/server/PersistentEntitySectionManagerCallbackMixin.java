package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PersistentEntitySectionManager.Callback.class)
public abstract class PersistentEntitySectionManagerCallbackMixin implements AutoCloseable {

    @Unique
    private static final Object async$lock = new Object();

    /**
     * Set once {@code onRemove} has detached the entity. Guarded by {@link #async$lock}.
     */
    @Unique
    private boolean async$removed;

    /**
     * An entity can be discarded by another thread (e.g. an experience orb merged by a neighbouring orb)
     * while its own tick is inside {@code setPosRaw}, holding this callback. Running that stale
     * {@code onMove} after {@code onRemove} re-inserts the removed entity into a section, where it stays
     * collidable but invisible to selectors (issue #200).
     */
    @WrapMethod(method = "onMove")
    private void onMove(Operation<Void> original) {
        synchronized (async$lock) {
            if (this.async$removed) {
                return;
            }
            original.call();
        }
    }

    @WrapMethod(method = "onRemove")
    private void onRemove(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (async$lock) {
            try {
                original.call(reason);
            } finally {
                this.async$removed = true;
            }
        }
    }
}
