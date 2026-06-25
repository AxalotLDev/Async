package com.axalotl.async.api.utils;

import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;


/**
 * Utility class for entity-related sensor operations.
 */
public class SensorUtils {

    /**
     * Hidden constructor to prevent instantiation.
     */
    private SensorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a comparator that sorts entities by squared distance to a source entity.
     *
     * @param <T> type of entity being compared
     * @param source reference entity used as distance origin
     * @return comparator ordering entities by distance to the source
     */
    public static <T extends Entity> Comparator<T> comparingDouble(Entity source) {
        Map<Entity, Double> cache = new HashMap<>();
        return (a, b) -> {
            double d1 = cache.computeIfAbsent(a, e -> e.distanceToSqr(source));
            double d2 = cache.computeIfAbsent(b, e -> e.distanceToSqr(source));
            return Double.compare(d1, d2);
        };
    }
}