package com.axalotl.async.mixin.entity.sensor;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.sensor.NearestLivingEntitiesSensor;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Mixin(value = NearestLivingEntitiesSensor.class, priority = 1500)
public class NearestLivingEntitiesSensorMixin {

    @Redirect(method = "sense", at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<LivingEntity> sense(ToDoubleFunction<? super LivingEntity> keyExtractor, ServerWorld world, LivingEntity entity) {
        Map<LivingEntity, Vec3d> positionCache = new HashMap<>();
        return (entity1, entity2) -> {
            Vec3d pos1 = positionCache.computeIfAbsent(entity1, Entity::getPos);
            Vec3d pos2 = positionCache.computeIfAbsent(entity2, Entity::getPos);
            double dist1 = entity.squaredDistanceTo(pos1);
            double dist2 = entity.squaredDistanceTo(pos2);
            return Double.compare(dist1, dist2);
        };
    }
}