package com.axalotl.async.mixin.entity.sensor;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.brain.sensor.NearestItemsSensor;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Mixin(value = NearestItemsSensor.class, priority = 1500)
public class NearestItemsSensorMixin {

    @Redirect(method = "sense(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/mob/MobEntity;)V", at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<ItemEntity> sense(ToDoubleFunction<? super ItemEntity> keyExtractor, ServerWorld world, MobEntity entity) {
        Map<ItemEntity, Vec3d> positionCache = new HashMap<>();
        return (item1, item2) -> {
            Vec3d pos1 = positionCache.computeIfAbsent(item1, Entity::getPos);
            Vec3d pos2 = positionCache.computeIfAbsent(item2, Entity::getPos);
            double dist1 = entity.squaredDistanceTo(pos1);
            double dist2 = entity.squaredDistanceTo(pos2);
            return Double.compare(dist1, dist2);
        };
    }
}
