package com.axalotl.async.mixin.entity.sensor;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.sensor.NearestPlayersSensor;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

@Mixin(value = NearestPlayersSensor.class, priority = 1500)
public class NearestPlayersSensorMixin {

    @Redirect(method = "sense", at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<ServerPlayerEntity> sense(ToDoubleFunction<? super ServerPlayerEntity> keyExtractor, ServerWorld world, LivingEntity entity) {
        Map<ServerPlayerEntity, Vec3d> positionCache = new HashMap<>();
        return (player1, player2) -> {
            Vec3d pos1 = positionCache.computeIfAbsent(player1, Entity::getPos);
            Vec3d pos2 = positionCache.computeIfAbsent(player2, Entity::getPos);
            double dist1 = entity.squaredDistanceTo(pos1);
            double dist2 = entity.squaredDistanceTo(pos2);
            return Double.compare(dist1, dist2);
        };
    }
}
