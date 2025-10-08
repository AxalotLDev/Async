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
    private static final ModConfigSpec.ConfigValue<Integer> paraMax;
    private static final ModConfigSpec.ConfigValue<List<String>> synchronizedEntities;
    private static final ModConfigSpec.ConfigValue<Boolean> enableAsyncSpawn;
    private static final ModConfigSpec.ConfigValue<Boolean> enableAsyncRandomTicks;

    static {
        BUILDER.push("Async Config");

        disabled = BUILDER.comment("Enables parallel processing of entity.")
                .define("disabled", com.axalotl.async.common.config.AsyncConfig.disabled);

        paraMax = BUILDER.comment("Maximum number of threads to use for parallel processing. Set to -1 to use default value.")
                .define("paraMax", com.axalotl.async.common.config.AsyncConfig.paraMax);

        synchronizedEntities = BUILDER.comment("List of entity class for sync processing.")
                .define("synchronizedEntities", new java.util.ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.stream().map(ResourceLocation::toString).toList()));

        enableAsyncSpawn = BUILDER.comment("Enables parallel processing of entity spawns.")
                .define("enableAsyncSpawn", com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);

        enableAsyncRandomTicks = BUILDER.comment("Experimental! Enables async processing of random ticks.")
                .define("enableAsyncRandomTicks", com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);

        BUILDER.pop();
        SPEC = BUILDER.build();
        LOGGER.info("Configuration successfully loaded.");
    }

    public static void loadConfig() {
        com.axalotl.async.common.config.AsyncConfig.disabled = disabled.get();
        com.axalotl.async.common.config.AsyncConfig.paraMax = paraMax.get();
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
        paraMax.set(com.axalotl.async.common.config.AsyncConfig.paraMax);
        enableAsyncSpawn.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn);
        enableAsyncRandomTicks.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncRandomTicks);
        synchronizedEntities.set(new java.util.ArrayList<>(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.stream().map(ResourceLocation::toString).toList()));
        SPEC.save();
        LOGGER.info("Configuration successfully saved.");
    }
}