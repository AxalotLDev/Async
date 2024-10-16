package com.axalotl.async.parallelised;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class ConcurrentCollections {

    private static final Logger LOGGER = LogManager.getLogger();

    public static <T> Set<T> newHashSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public static <T, U> Map<T, U> newHashMap() {
        return new ConcurrentHashMap<>();
    }

    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.toCollection(CopyOnWriteArrayList::new);
    }

    public static <T> Queue<T> newArrayDeque() {
        LOGGER.info("Concurrent \"array\" deque created");
        return new ConcurrentLinkedDeque<>();
    }

}
