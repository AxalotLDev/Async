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
    private static final ModConfigSpec.BooleanValue enableAsyncRandomTicks;

    static {
        BUILDER.push("Async Config");

        disabled = BUILDER.comment("Disables the mod. All entities are ticked on the main thread.")
                .define("disabled", com.axalotl.async.common.config.AsyncConfig.disabled);

        maxThreads = BUILDER.comment("Worker threads for parallel ticking. -1 = auto.")
                .defineInRange("maxThreads", com.axalotl.async.common.config.AsyncConfig.maxThreads, -1, Integer.MAX_VALUE);

        synchronizedEntities = BUILDER.comment("""
                        Entities ticked on the main thread instead of in parallel.
                        Entity IDs or namespaces (*):
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
        com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks = enableAsyncRandomTicks.get();

        List<? extends String> entries = synchronizedEntities.get();
        Set<String> entities = new HashSet<>();
        if (!entries.isEmpty()) {
            entities.addAll(entries);
        }

        com.axalotl.async.common.config.AsyncConfig.synchronizedEntities = entities.isEmpty()
                ? getDefaultSynchronizedEntities()
                : entities;

        com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
    }

    public static void saveConfig() {
        disabled.set(com.axalotl.async.common.config.AsyncConfig.disabled);
        maxThreads.set(com.axalotl.async.common.config.AsyncConfig.maxThreads);
        enableAsyncSpawn.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);
        enableAsyncRandomTicks.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);
        synchronizedEntities.set(new ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities));
        SPEC.save();
        com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
    }
}