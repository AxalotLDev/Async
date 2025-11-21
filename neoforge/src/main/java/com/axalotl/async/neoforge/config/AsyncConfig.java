package com.axalotl.async.neoforge.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;

import static com.axalotl.async.common.config.AsyncConfig.*;

public class AsyncConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.ConfigValue<Boolean> disabled;
    private static final ModConfigSpec.ConfigValue<Integer> maxThreads;
    private static final ModConfigSpec.ConfigValue<List<String>> synchronizedEntities;
    private static final ModConfigSpec.ConfigValue<Boolean> enableAsyncSpawn;
    private static final ModConfigSpec.ConfigValue<Boolean> enableAsyncRandomTicks;

    static {
        BUILDER.push("Async Config");

        disabled = BUILDER.comment("Enables parallel processing of entities.")
                .define("disabled", com.axalotl.async.common.config.AsyncConfig.disabled);

        maxThreads = BUILDER.comment("Maximum worker threads. -1 = auto.")
                .define("maxThreads", com.axalotl.async.common.config.AsyncConfig.maxThreads);

        synchronizedEntities = BUILDER.comment("List of entity IDs that must ALWAYS tick synchronously.")
                .define("synchronizedEntities", new java.util.ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.stream().map(ResourceLocation::toString).toList()));

        enableAsyncSpawn = BUILDER.comment("Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.")
                .define("enableAsyncSpawn", com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);

        enableAsyncRandomTicks = BUILDER.comment("Experimental! Enables async random ticks.")
                .define("enableAsyncRandomTicks", com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);

        BUILDER.pop();
        SPEC = BUILDER.build();
        LOGGER.info("Configuration saved.");
    }

    public static void loadConfig() {
        com.axalotl.async.common.config.AsyncConfig.disabled = disabled.get();
        com.axalotl.async.common.config.AsyncConfig.maxThreads = maxThreads.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn = enableAsyncSpawn.get();
        com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks = enableAsyncRandomTicks.get();
        com.axalotl.async.common.config.AsyncConfig.synchronizedEntities = new HashSet<>();
        List<String> ids = synchronizedEntities.get();
        HashSet<ResourceLocation> set = new HashSet<>();

        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                set.add(rl);
            }
        }

        com.axalotl.async.common.config.AsyncConfig.synchronizedEntities = set.isEmpty()
                ? getDefaultSynchronizedEntities()
                : set;
    }

    public static void saveConfig() {
        disabled.set(com.axalotl.async.common.config.AsyncConfig.disabled);
        maxThreads.set(com.axalotl.async.common.config.AsyncConfig.maxThreads);
        enableAsyncSpawn.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);
        enableAsyncRandomTicks.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);
        synchronizedEntities.set(new java.util.ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.stream().map(ResourceLocation::toString).toList()));
        SPEC.save();
        LOGGER.info("Configuration saved.");
    }
}