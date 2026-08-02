package com.axalotl.async.common.mixin.compat.sable;

import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Isolates Sable's mutable collision scratch values between entity tick threads.
 */
@Mixin(Level.class)
public abstract class SableLevelMixin {
    @Unique
    private final ThreadLocal<LevelReusedVectors> async$sableCollisionVectors =
            ThreadLocal.withInitial(LevelReusedVectors::new);

    /**
     * Replaces the per-level shared buffer supplied by Sable's Level mixin.
     */
    @Dynamic("Method is added to Level by Sable")
    @Inject(method = "sable$getJOMLSink", at = @At("HEAD"), cancellable = true, remap = false)
    private void async$useThreadLocalCollisionVectors(
            CallbackInfoReturnable<LevelReusedVectors> callbackInfo
    ) {
        callbackInfo.setReturnValue(this.async$sableCollisionVectors.get());
    }
}
