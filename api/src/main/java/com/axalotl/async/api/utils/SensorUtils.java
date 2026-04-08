package com.axalotl.async.api.utils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SensorUtils {

    public static <T extends LivingEntity> Sensor<T> wrapSensor(Sensor<T> originalSensor, TickWrapper<T> wrapperLogic) {
        return new Sensor<>() {

            @Override
            public @NonNull Set<MemoryModuleType<?>> requires() {
                return originalSensor.requires();
            }

            @Override
            protected void doTick(@NonNull ServerLevel level, @NonNull T entity) {
                wrapperLogic.tick(level, entity);
            }
        };
    }

    /**
     * Utility for sorting with distance caching
     */
    public static <T extends Entity> Comparator<T> distanceComparator(Entity source) {
        Map<Entity, Double> cache = new HashMap<>();
        return (a, b) -> {
            double d1 = cache.computeIfAbsent(a, e -> e.distanceToSqr(source));
            double d2 = cache.computeIfAbsent(b, e -> e.distanceToSqr(source));
            return Double.compare(d1, d2);
        };
    }

    @FunctionalInterface
    public interface TickWrapper<T extends LivingEntity> {
        void tick(ServerLevel level, T entity);
    }
}