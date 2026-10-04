package com.axalotl.async.common.utils;

import net.minecraft.world.entity.Entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for entity-related sensor operations.
 *
 * <p>Internal to the mod: must live in {@code common} (compiled directly into
 * the mod jar's game-layer classloader) rather than the published {@code api}
 * module, which is embedded via jarJar as a separate library and does not
 * have visibility into mixin-patched {@code net.minecraft} classes.</p>
 */
public final class SensorUtils {

    /**
     * Hidden constructor to prevent instantiation.
     */
    private SensorUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a comparator that sorts entities by squared distance to a source entity.
     *
     * @param <T>    type of entity being compared
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