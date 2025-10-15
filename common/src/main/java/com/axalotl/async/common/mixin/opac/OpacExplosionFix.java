package com.axalotl.async.common.mixin.opac;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ParallelProcessor.class, priority = 1500, remap = false)
public abstract class OpacExplosionFix {

    @Inject(method = "performAsyncEntityTick", at = @At("HEAD"), cancellable = true)
    private static void async$routeExplosiveEntityTicks(ServerLevel world, Entity entity, CallbackInfo ci) {
        if (shouldRouteToMainThread(world, entity)) {
            // schedule the tick on the main server thread and cancel async processing
            world.getServer().execute(() -> {
                try {
                    world.tickNonPassenger(entity);
                } catch (Throwable t) {
                    ParallelProcessor.LOGGER.error("Unhandled exception in mixin", t);
                }
            });
            ci.cancel();
        }
    }

    @Unique
    private static boolean shouldRouteToMainThread(ServerLevel world, Entity entity) {
        if (world.getServer().isSameThread()) return false;

        try {
            if (entity instanceof Creeper creeper) {
                if (creeper.isIgnited()) return true;
                // im still in shock that i didnt notice this when testing LOL
                float swelling = creeper.getSwelling(1.0F);
                int swellDir = creeper.getSwellDir();

                if (swelling > 0.01F) return true;
                if (swellDir > 0) return true;
            }
            if (entity instanceof WitherBoss wither) {
                // During spawning, invulnerableTicks counts down.
                // Explosion happens when it reaches 0.
                int invuln = wither.getInvulnerableTicks();
                if (invuln > 0 && invuln <= 220)
                    return true; // Route while spawning
            }
        } catch (Throwable ignored) {}

        return false;
    }
}