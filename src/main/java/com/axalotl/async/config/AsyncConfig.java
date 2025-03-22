package com.axalotl.async.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AsyncConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger("Async Config");

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static ForgeConfigSpec SPEC;

    public static boolean disabled = false;
    public static int paraMax = -1;
    public static boolean enableEntityMoveSync = false;
    public static boolean enableAsyncSpawn = false;
    public static Set<ResourceLocation> synchronizedEntities = new HashSet<>(Set.of(
            Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "tnt")),
            Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "item")),
            Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "experience_orb"))
    ));

    private static final ForgeConfigSpec.ConfigValue<Boolean> disabledv;
    private static final ForgeConfigSpec.ConfigValue<Integer> paraMaxv;
    private static final ForgeConfigSpec.ConfigValue<Boolean> enableEntityMoveSyncv;
    private static final ForgeConfigSpec.ConfigValue<List<String>> synchronizedEntitiesv;
    private static final ForgeConfigSpec.ConfigValue<Boolean> enableAsyncSpawnv;

    static {
        BUILDER.push("Async Configs");

        disabledv = BUILDER.comment("Globally disable all toggleable functionality within the async system. Set to true to stop all asynchronous operations.")
                .define("disabled", false);

        paraMaxv = BUILDER.comment("Maximum number of threads to use for parallel processing. Set to -1 to use default value.")
                .define("paraMax", -1);

        synchronizedEntitiesv = BUILDER.comment("Disables Item entity parallelization.")
                .define("synchronizedEntities", synchronizedEntities.stream().map(ResourceLocation::toString).toList());

        enableEntityMoveSyncv = BUILDER.comment("Modifies entity movement processing: true for synchronous movement (vanilla mechanics intact, less performance), false for asynchronous movement (better performance, may break mechanics).")
                .define("enableEntityMoveSync", false);

        enableAsyncSpawnv = BUILDER.comment("Enables parallel processing of entity spawns. Warning, incompatible with VMP mod && Carpet mod lagFreeSpawning rule.")
                .define("enableAsyncSpawn", false);

        BUILDER.pop();
        SPEC = BUILDER.build();
        LOGGER.info("Configuration successfully loaded.");
    }

    public static void castConfig() {
        disabled = disabledv.get();
        paraMax = paraMaxv.get();
        synchronizedEntities = new HashSet<>();
        SPEC.getSpec().<List<String>>getOptional("synchronizedEntities").ifPresentOrElse(ids -> {
            for (String id : ids) {
                ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
                if (resourceLocation != null) {
                    synchronizedEntities.add(resourceLocation);
                }
            }
        }, () -> synchronizedEntities = new HashSet<>(Set.of(
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "tnt")),
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "item")),
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "experience_orb"))
        )));
        enableEntityMoveSync = enableEntityMoveSyncv.get();
        enableAsyncSpawn = enableAsyncSpawnv.get();
        LOGGER.info("Config Casted");
    }

    public static void saveConfig() {
        disabledv.set(disabled);
        paraMaxv.set(paraMax);
        synchronizedEntitiesv.set(synchronizedEntities.stream().map(ResourceLocation::toString).toList());
        enableEntityMoveSyncv.set(enableEntityMoveSync);
        enableAsyncSpawnv.set(enableAsyncSpawn);
        LOGGER.info("Configuration successfully saved.");
    }

    public static int getParallelism() {
        if (paraMax <= 0) return Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), paraMax));
    }

    public static void syncEntity(ResourceLocation entityId) {
        if (synchronizedEntities.add(entityId)) {
            saveConfig();
            LOGGER.info("Sync entity class: {}", entityId);
        } else {
            LOGGER.warn("Entity class already synchronized: {}", entityId);
        }
    }
    public static void asyncEntity(ResourceLocation entityId) {
        if (synchronizedEntities.remove(entityId)) {
            saveConfig();
            LOGGER.info("Enable async process entity class: {}", entityId);
        } else {
            LOGGER.warn("Entity class not found: {}", entityId);
        }
    }
}
