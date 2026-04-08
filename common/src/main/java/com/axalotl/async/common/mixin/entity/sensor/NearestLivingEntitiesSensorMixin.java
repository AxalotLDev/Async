package com.axalotl.async.common.mixin.entity.sensor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
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
    private Comparator<LivingEntity> doTick(ToDoubleFunction<? super LivingEntity> keyExtractor, ServerLevel level, LivingEntity body) {
        Map<LivingEntity, Double> distanceCache = new HashMap<>();
        return (e1, e2) -> {
            double d1 = distanceCache.computeIfAbsent(e1, entity -> entity.distanceToSqr(body));
            double d2 = distanceCache.computeIfAbsent(e2, entity -> entity.distanceToSqr(body));
            return Double.compare(d1, d2);
        };
    }
}