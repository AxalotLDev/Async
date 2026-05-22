package com.axalotl.async.neoforge.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.axalotl.async.common.config.AsyncConfig.*;

public class AsyncConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue disabled;
    private static final ModConfigSpec.IntValue maxThreads;
    private static final ModConfigSpec.IntValue threadPriority;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> synchronizedEntities;
    private static final ModConfigSpec.BooleanValue enableAsyncSpawn;
    private static final ModConfigSpec.BooleanValue enableAsyncRandomTicks;
    private static final ModConfigSpec.BooleanValue enableAsyncVmpTracking;
    private static final ModConfigSpec.BooleanValue enableAsyncChunkSend;

    static {
        BUILDER.push("Async Config");

        disabled = BUILDER.comment("Enables parallel processing of entities.")
                .define("disabled", com.axalotl.async.common.config.AsyncConfig.disabled);

        maxThreads = BUILDER.comment("Maximum worker threads. -1 = auto (uses all cores).")
                .defineInRange("maxThreads", com.axalotl.async.common.config.AsyncConfig.maxThreads, -1, Integer.MAX_VALUE);

        threadPriority = BUILDER.comment("Worker-thread OS priority (1..10). Default 4 is below server/render thread (5) so they keep responsiveness on saturated CPUs.")
                .defineInRange("threadPriority", com.axalotl.async.common.config.AsyncConfig.threadPriority, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);

        synchronizedEntities = BUILDER.comment("""
                        List of entity IDs or namespaces (*):
                          - 'minecraft:zombie' = specific entity
                          - 'minecraft:*'      = all entities in namespace""")
                .defineListAllowEmpty(
                        "synchronizedEntities",
                        () -> new ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities),
                        () -> "",
                        obj -> obj instanceof String
                );

        enableAsyncSpawn = BUILDER.comment("Reserved flag. No-op currently — parallel spawn was removed after profiling showed it was a net regression.")
                .define("enableAsyncSpawn", com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);

        enableAsyncRandomTicks = BUILDER.comment("Experimental! Enables async random ticks.")
                .define("enableAsyncRandomTicks", com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);

        enableAsyncVmpTracking = BUILDER.comment("Offloads VMP's NearbyEntityTracking work (both tryTickTracker AND per-(tracker,player) tryUpdateTracker) to the async pool, grouped per-tracker to avoid races on seenBy state. No effect without VMP installed.")
                .define("enableAsyncVmpTracking", com.axalotl.async.common.config.AsyncConfig.enableAsyncVmpTracking);

        enableAsyncChunkSend = BUILDER.comment("Pre-builds ClientboundLevelChunkWithLightPacket on the worker pool inside PlayerChunkSender.sendNextChunks — removes the main-thread cost of chunk packet construction on player-join bursts. Off by default: modded BlockEntity.getUpdateTag() may not be thread-safe.")
                .define("enableAsyncChunkSend", com.axalotl.async.common.config.AsyncConfig.enableAsyncChunkSend);

        BUILDER.pop();
        SPEC = BUILDER.build();
        LOGGER.info("Configuration initialized.");
    }

    public static void loadConfig() {
        com.axalotl.async.common.config.AsyncConfig.disabled = disabled.get();
        com.axalotl.async.common.config.AsyncConfig.maxThreads = maxThreads.get();
        com.axalotl.async.common.config.AsyncConfig.threadPriority = threadPriority.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn = enableAsyncSpawn.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks = enableAsyncRandomTicks.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncVmpTracking = enableAsyncVmpTracking.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncChunkSend = enableAsyncChunkSend.get();

        List<? extends String> entries = synchronizedEntities.get();
        Set<String> entities = new HashSet<>();
        if (!entries.isEmpty()) {
            entities.addAll(entries);
        }

        com.axalotl.async.common.config.AsyncConfig.synchronizedEntities = entities.isEmpty()
                ? getDefaultSynchronizedEntities()
                : entities;
    }

    public static void saveConfig() {
        disabled.set(com.axalotl.async.common.config.AsyncConfig.disabled);
        maxThreads.set(com.axalotl.async.common.config.AsyncConfig.maxThreads);
        threadPriority.set(com.axalotl.async.common.config.AsyncConfig.threadPriority);
        enableAsyncSpawn.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);
        enableAsyncRandomTicks.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);
        enableAsyncVmpTracking.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncVmpTracking);
        enableAsyncChunkSend.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncChunkSend);
        synchronizedEntities.set(new ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities));
        SPEC.save();
        com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
    }
}