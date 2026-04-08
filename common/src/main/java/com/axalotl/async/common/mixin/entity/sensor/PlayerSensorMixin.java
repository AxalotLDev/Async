package com.axalotl.async.common.mixin.entity.sensor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.PlayerSensor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;
import java.util.function.ToDoubleFunction;

@Mixin(value = PlayerSensor.class, priority = 1500)
public class PlayerSensorMixin {

    @Redirect(method = "doTick",
            at = @At(value = "INVOKE", target = "Ljava/util/Comparator;comparingDouble(Ljava/util/function/ToDoubleFunction;)Ljava/util/Comparator;"))
    private Comparator<Player> doTick(ToDoubleFunction<? super Player> keyExtractor, ServerLevel level, LivingEntity body) {
        Map<LivingEntity, Double> distanceCache = new HashMap<>();
        return (e1, e2) -> {
            double d1 = distanceCache.computeIfAbsent(e1, item -> item.distanceToSqr(body));
            double d2 = distanceCache.computeIfAbsent(e2, item -> item.distanceToSqr(body));
            return Double.compare(d1, d2);
        };
    }
}