package com.axalotl.async.common.mixin.compat.sable;

import com.axalotl.async.common.compat.SableCompatibility;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Sable applies explosion impulses to sub-level rigid bodies in the native physics scene from
 * explode(), which worker-ticked entities can reach while other workers change blocks.
 */
@Mixin(Explosion.class)
public abstract class SableExplosionMixin {
    @Shadow
    @Final
    private Level level;

    @WrapMethod(method = "explode")
    private void async$serializeSablePhysicsImpulses(Operation<Void> original) {
        Explosion explosion = (Explosion) (Object) this;
        if (!SableCompatibility.canReachSubLevel(this.level, explosion.center(), explosion.radius())) {
            original.call();
            return;
        }

        SableCompatibility.lockSharedState();
        try {
            original.call();
        } finally {
            SableCompatibility.unlockSharedState();
        }
    }
}
