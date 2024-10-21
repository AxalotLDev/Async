package com.axalotl.async.mixin.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.LivingTargetCache;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.NearestLivingEntitiesSensor;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

@Mixin(NearestLivingEntitiesSensor.class)
public abstract class NearestLivingEntitiesSensorMixin<T extends LivingEntity> extends Sensor<T> {

    @Inject(method = "sense", at = @At("HEAD"), cancellable = true)
    private void onSense(ServerWorld world, T entity, CallbackInfo ci) {
        List<LivingEntity> nearbyEntities = world.getEntitiesByClass(LivingEntity.class,
                entity.getBoundingBox().expand(16, 16, 16),
                e -> e != entity && e.isAlive());
        ConcurrentSkipListSet<LivingEntity> sortedEntities = new ConcurrentSkipListSet<>((e1, e2) -> {
            int distanceCompare = Double.compare(entity.squaredDistanceTo(e1), entity.squaredDistanceTo(e2));
            if (distanceCompare != 0) {
                return distanceCompare;
            }
            int idCompare = Integer.compare(e1.getId(), e2.getId());
            if (idCompare != 0) {
                return idCompare;
            }
            return Integer.compare(System.identityHashCode(e1), System.identityHashCode(e2));
        });
        sortedEntities.addAll(nearbyEntities);
        ObjectArrayList<LivingEntity> sortedList = new ObjectArrayList<>(sortedEntities);
        Brain<?> brain = entity.getBrain();
        if (brain != null) {
            synchronized (brain) {
                brain.remember(MemoryModuleType.MOBS, sortedList);
                brain.remember(MemoryModuleType.VISIBLE_MOBS, new LivingTargetCache(entity, sortedList));
            }
        }
        ci.cancel();
    }
}