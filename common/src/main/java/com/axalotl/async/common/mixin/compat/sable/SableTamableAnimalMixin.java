package com.axalotl.async.common.mixin.compat.sable;

import com.axalotl.async.common.compat.SableCompatibility;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Sable's teleport-to-owner check (inside maybeTeleportTo) shares one static scratch box between
 * every pet, and pets follow their owners from worker threads.
 */
@Mixin(TamableAnimal.class)
public abstract class SableTamableAnimalMixin {
    @WrapMethod(method = "teleportToAroundBlockPos")
    private void async$serializeSableTeleportChecks(BlockPos target, Operation<Void> original) {
        SableCompatibility.lockSharedState();
        try {
            original.call(target);
        } finally {
            SableCompatibility.unlockSharedState();
        }
    }
}
