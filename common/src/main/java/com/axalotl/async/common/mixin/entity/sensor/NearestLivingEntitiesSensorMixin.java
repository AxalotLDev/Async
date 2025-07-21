package com.axalotl.async.common.mixin.entity.sensor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Mixin(value = NearestLivingEntitySensor.class, priority = 1500)
public class NearestLivingEntitiesSensorMixin {

    @Redirect(method = "doTick",
            at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<LivingEntity> doTick(ToDoubleFunction<? super LivingEntity> keyExtractor, ServerLevel world, LivingEntity entity) {
        Map<LivingEntity, Vec3> positionCache = new HashMap<>();
        return (entity1, entity2) -> {
            Vec3 pos1 = positionCache.computeIfAbsent(entity1, Entity::position);
            Vec3 pos2 = positionCache.computeIfAbsent(entity2, Entity::position);
            double dist1 = entity.distanceToSqr(pos1);
            double dist2 = entity.distanceToSqr(pos2);
            return Double.compare(dist1, dist2);
        };
    }
}