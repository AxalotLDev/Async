package com.axalotl.async.common.mixin.entity.sensor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;
import java.util.function.ToDoubleFunction;

@Mixin(value = NearestItemSensor.class, priority = 1500)
public class NearestItemSensorMixin {

    @Redirect(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<ItemEntity> doTick(ToDoubleFunction<? super ItemEntity> keyExtractor, ServerLevel level, Mob body) {
        Map<ItemEntity, Double> distanceCache = new HashMap<>();
        return (e1, e2) -> {
            double d1 = distanceCache.computeIfAbsent(e1, item -> item.distanceToSqr(body));
            double d2 = distanceCache.computeIfAbsent(e2, item -> item.distanceToSqr(body));
            return Double.compare(d1, d2);
        };
    }
}