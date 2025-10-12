package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PersistentEntitySectionManager.Callback.class)
public abstract class PersistentEntitySectionManagerCallbackMixin implements AutoCloseable {

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "onMove")
    private void onMove(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }

    //TODO Entity wasn't found in section
    @Redirect(method = "onMove", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;[Ljava/lang/Object;)V"), remap = false)
    private void notFoundSectionDisable(Logger instance, String s, Object[] objects) {
    }

    @WrapMethod(method = "onRemove")
    private void onRemove(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(reason);
        }
    }
}