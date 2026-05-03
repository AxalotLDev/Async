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
    private static final ModConfigSpec.ConfigValue<List<? extends String>> synchronizedEntities;
    private static final ModConfigSpec.BooleanValue enableAsyncSpawn;
    private static final ModConfigSpec.BooleanValue enableAsyncMobSpawning;
    private static final ModConfigSpec.BooleanValue enableAsyncRandomTicks;

    static {
        BUILDER.push("Async Config");

        disabled = BUILDER.comment("Enables parallel processing of entities.")
                .define("disabled", com.axalotl.async.common.config.AsyncConfig.disabled);

        maxThreads = BUILDER.comment("Maximum worker threads. -1 = auto.")
                .defineInRange("maxThreads", com.axalotl.async.common.config.AsyncConfig.maxThreads, -1, Integer.MAX_VALUE);

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

        enableAsyncSpawn = BUILDER.comment("Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.")
                .define("enableAsyncSpawn", com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);

        enableAsyncMobSpawning = BUILDER.comment("Enables async natural mob spawning. Defaults to false when C2ME is detected to avoid chunk-load deadlocks.")
                .define("enableAsyncMobSpawning", com.axalotl.async.common.config.AsyncConfig.enableAsyncMobSpawning);

        enableAsyncRandomTicks = BUILDER.comment("Experimental! Enables async random ticks.")
                .define("enableAsyncRandomTicks", com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);

        BUILDER.pop();
        SPEC = BUILDER.build();
        LOGGER.info("Configuration initialized.");
    }

    public static void loadConfig() {
        com.axalotl.async.common.config.AsyncConfig.disabled = disabled.get();
        com.axalotl.async.common.config.AsyncConfig.maxThreads = maxThreads.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn = enableAsyncSpawn.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncMobSpawning = enableAsyncMobSpawning.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks = enableAsyncRandomTicks.get();

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
        enableAsyncSpawn.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);
        enableAsyncMobSpawning.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncMobSpawning);
        enableAsyncRandomTicks.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);
        synchronizedEntities.set(new ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities));
        SPEC.save();
        com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
    }
}
