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

    static {
        BUILDER.push("Async Config");

        disabled = BUILDER.comment("Globally disable all toggleable functionality within the async system. Set to true to stop all asynchronous operations.")
                .define(com.axalotl.async.common.config.AsyncConfig.disabled.getKey(), com.axalotl.async.common.config.AsyncConfig.disabled.getValue());

        paraMax = BUILDER.comment("Maximum number of threads to use for parallel processing. Set to -1 to use default value.")
                .define(com.axalotl.async.common.config.AsyncConfig.paraMax.getKey(), com.axalotl.async.common.config.AsyncConfig.paraMax.getValue());

        synchronizedEntities = BUILDER.comment("List of entity class for sync processing.")
                .define(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.getKey(), com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.getValue().stream().map(ResourceLocation::toString).toList());

        enableAsyncSpawn = BUILDER.comment("Enables parallel processing of entity spawns.")
                .define(com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn.getKey(), com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn.getValue());

        BUILDER.pop();
        SPEC = BUILDER.build();
        LOGGER.info("Configuration successfully loaded.");
    }

    public static void loadConfig() {
        com.axalotl.async.common.config.AsyncConfig.disabled.setValue(disabled.get());
        com.axalotl.async.common.config.AsyncConfig.paraMax.setValue(paraMax.get());
        com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn.setValue(enableAsyncSpawn.get());
        com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.setValue(new HashSet<>());
        SPEC.getSpec().<List<String>>getOptional(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.getKey()).ifPresentOrElse(ids -> {
            for (String id : ids) {
                ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
                if (resourceLocation != null) {
                    com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.getValue().add(resourceLocation);
                }
            }
        }, () -> com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.setValue(getDefaultSynchronizedEntities()));
    }

    public static void saveConfig() {
        disabled.set(com.axalotl.async.common.config.AsyncConfig.disabled.getValue());
        paraMax.set(com.axalotl.async.common.config.AsyncConfig.paraMax.getValue());
        enableAsyncSpawn.set(com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn.getValue());
        synchronizedEntities.set(com.axalotl.async.common.config.AsyncConfig.synchronizedEntities.getValue().stream().map(ResourceLocation::toString).toList());
        LOGGER.info("Configuration successfully saved.");
    }
}
