package com.axalotl.async.common.mixin.entity.sensor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Mixin(value = NearestItemSensor.class, priority = 1500)
public class NearestItemSensorMixin {

    @Redirect(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<ItemEntity> doTick(ToDoubleFunction<? super ItemEntity> keyExtractor, ServerLevel world, Mob entity) {
        Map<ItemEntity, Vec3> positionCache = new HashMap<>();
        return (item1, item2) -> {
            Vec3 pos1 = positionCache.computeIfAbsent(item1, Entity::position);
            Vec3 pos2 = positionCache.computeIfAbsent(item2, Entity::position);
            double dist1 = entity.distanceToSqr(pos1);
            double dist2 = entity.distanceToSqr(pos2);
            return Double.compare(dist1, dist2);
        };
    }
}