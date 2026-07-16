package com.axalotl.async.api.utils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Factory methods for creating thread-safe collection instances.
 * Provides convenient methods to create concurrent collections with standard interfaces.
 * <p>
 * This class is not intended to be instantiated.
 */
public final class ConcurrentCollections {

    private ConcurrentCollections() {
        throw new AssertionError("No instances");
    }

    /**
     * Creates a new thread-safe set
     *
     * @param <T> the type of elements maintained by this set
     * @return a new concurrent hash set
     */
    public static <T> Set<T> newHashSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    /**
     * Creates a new thread-safe map
     *
     * @param <T> the type of keys maintained by this map
     * @param <U> the type of mapped values
     * @return a new concurrent hash map
     */
    public static <T, U> Map<T, U> newHashMap() {
        return new ConcurrentHashMap<>();
    }

    /**
     * Creates a collector that accumulates elements into a thread-safe list
     *
     * @param <T> the type of elements in the list
     * @return a collector that accumulates elements into a thread-safe list
     */
    public static <T> java.util.stream.Collector<T, ?, List<T>> toList() {
        return Collectors.toCollection(ConcurrentObjectArrayList::new);
    }
}