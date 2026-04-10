package com.axalotl.async.common.parallelised.utils;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class TickStats {
    public static final Map<EntityType<?>, LongAdder> TICK_TIME_NS = new ConcurrentHashMap<>();
    public static final Map<EntityType<?>, LongAdder> TICK_COUNT = new ConcurrentHashMap<>();
    public static final Map<EntityType<?>, LongAdder> ASYNC_TICK_TIME_NS = new ConcurrentHashMap<>();
    public static final Map<EntityType<?>, LongAdder> ASYNC_TICK_COUNT = new ConcurrentHashMap<>();
    public static final AtomicInteger RECORDING_TICKS_LEFT = new AtomicInteger(0);

    public static void startRecording(int ticks) {
        clean();
        RECORDING_TICKS_LEFT.set(ticks);
    }

    public static void clean() {
        TICK_TIME_NS.clear();
        TICK_COUNT.clear();
        ASYNC_TICK_TIME_NS.clear();
        ASYNC_TICK_COUNT.clear();
    }

    public static boolean isRecording() {
        return RECORDING_TICKS_LEFT.get() > 0;
    }

    public static double getMSPTForType(EntityType<?> type, int recordedTicks) {
        if (recordedTicks <= 0) return 0;

        double syncMs = 0;
        double asyncMs = 0;

        LongAdder syncTime = TICK_TIME_NS.get(type);
        if (syncTime != null) {
            syncMs = syncTime.sum() / 1_000_000.0;
        }

        LongAdder asyncTime = ASYNC_TICK_TIME_NS.get(type);
        if (asyncTime != null) {
            int currentPoolSize = ParallelProcessor.getPoolSize();
            double rawAsyncMs = asyncTime.sum() / 1_000_000.0;
            asyncMs = currentPoolSize > 0 ? rawAsyncMs / currentPoolSize : rawAsyncMs;
        }

        return (syncMs + asyncMs) / recordedTicks;
    }

    public static void resetEntityTickStats() {
        TICK_TIME_NS.clear();
        TICK_COUNT.clear();
        ASYNC_TICK_TIME_NS.clear();
        ASYNC_TICK_COUNT.clear();
        RECORDING_TICKS_LEFT.set(0);
    }
}