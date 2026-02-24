package com.axalotl.async.common.mixin.server;

import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PersistentEntitySectionManager.Callback.class)
public abstract class PersistentEntitySectionManagerCallbackMixin implements AutoCloseable {

//    @Unique
//    private static final Object async$lock = new Object();
//
//    @WrapMethod(method = "onMove")
//    private void onMove(Operation<Void> original) {
//        synchronized (async$lock) {
//            original.call();
//        }
//    }
//
//    @WrapMethod(method = "onRemove")
//    private void onRemove(Entity.RemovalReason reason, Operation<Void> original) {
//        synchronized (async$lock) {
//            original.call(reason);
//        }
//    }
}