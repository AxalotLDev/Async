package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.utils.EntitySectionTransition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")
public class PersistentEntitySectionManagerCallbackMixin {

    @WrapMethod(method = "onMove")
    private void onMove(Operation<Void> original) {
        synchronized (EntitySectionTransition.LOCK) {
            original.call();
        }
    }

    @WrapMethod(method = "onRemove")
    private void onRemove(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (EntitySectionTransition.LOCK) {
            original.call(reason);
        }
    }
}