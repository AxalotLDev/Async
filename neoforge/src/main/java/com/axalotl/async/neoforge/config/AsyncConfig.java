package com.axalotl.async.neoforge.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.List;

import static com.axalotl.async.common.config.AsyncConfig.*;

public class AsyncConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.ConfigValue<Boolean> disabledv;
    private static final ModConfigSpec.ConfigValue<Integer> paraMaxv;
    private static final ModConfigSpec.ConfigValue<Boolean> enableEntityMoveSyncv;
    private static final ModConfigSpec.ConfigValue<List<String>> synchronizedEntitiesv;
    private static final ModConfigSpec.ConfigValue<Boolean> enableAsyncSpawnv;
    private static final ModConfigSpec.ConfigValue<Boolean> recoverFromErrorsv;

    static {
        BUILDER.push("Async Config");

        disabledv = BUILDER.comment("Globally disable all toggleable functionality within the async system. Set to true to stop all asynchronous operations.")
                .define(disabled.getKey(), disabled.getValue());

        paraMaxv = BUILDER.comment("Maximum number of threads to use for parallel processing. Set to -1 to use default value.")
                .define(paraMax.getKey(), paraMax.getValue());

        synchronizedEntitiesv = BUILDER.comment("Disables Item entity parallelization.")
                .define(synchronizedEntities.getKey(), synchronizedEntities.getValue().stream().map(ResourceLocation::toString).toList());

        enableEntityMoveSyncv = BUILDER.comment("Modifies entity movement processing: true for synchronous movement (vanilla mechanics intact, less performance), false for asynchronous movement (better performance, may break mechanics).")
                .define(enableEntityMoveSync.getKey(), enableEntityMoveSync.getValue());

        enableAsyncSpawnv = BUILDER.comment("Enables parallel processing of entity spawns. Warning, incompatible with VMP mod && Carpet mod lagFreeSpawning rule.")
                .define(enableAsyncSpawn.getKey(), enableAsyncSpawn.getValue());

        recoverFromErrorsv = BUILDER.comment("Tries to recover from entity processing errors instead of crashing.")
                .define(recoverFromErrors.getKey(), recoverFromErrors.getValue());

        BUILDER.pop();
        SPEC = BUILDER.build();
        LOGGER.info("Configuration successfully loaded.");
    }

    public static void loadConfig() {
        disabled.setValue(disabledv.get());
        paraMax.setValue(paraMaxv.get());
        enableAsyncSpawn.setValue(enableAsyncSpawnv.get());
        recoverFromErrors.setValue(recoverFromErrorsv.get());
        synchronizedEntities.setValue(new HashSet<>());
        SPEC.getSpec().<List<String>>getOptional(synchronizedEntities.getKey()).ifPresentOrElse(ids -> {
            for (String id : ids) {
                ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
                if (resourceLocation != null) {
                    synchronizedEntities.getValue().add(resourceLocation);
                }
            }
        }, () -> synchronizedEntities.setValue(getDefaultSynchronizedEntities()));
        enableEntityMoveSync.setValue(enableEntityMoveSyncv.get());
    }

    public static void saveConfig() {
        disabledv.set(disabled.getValue());
        paraMaxv.set(paraMax.getValue());
        enableAsyncSpawnv.set(enableAsyncSpawn.getValue());
        recoverFromErrorsv.set(recoverFromErrors.getValue());
        synchronizedEntitiesv.set(synchronizedEntities.getValue().stream().map(ResourceLocation::toString).toList());
        enableEntityMoveSyncv.set(enableEntityMoveSync.getValue());
        LOGGER.info("Configuration successfully saved.");
    }
}
