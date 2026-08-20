package com.axalotl.async.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.axalotl.async.common.config.AsyncConfig.*;

public class AsyncConfig {

    private static final Supplier<CommentedFileConfig> configSupplier =
            () -> CommentedFileConfig.builder(
                            FabricLoader.getInstance().getConfigDir().resolve("async.toml"))
                    .preserveInsertionOrder()
                    .sync()
                    .build();

    private static CommentedFileConfig CONFIG;

    private static final Set<String> VALID_KEYS = Set.of(
            "disabled",
            "maxThreads",
            "synchronizedEntities",
            "enableAsyncSpawn",
            "enableAsyncRandomTicks",
            "synchronizeUnannotatedModEntities"
    );

    public static void init() {
        LOGGER.info("Initializing Async Config...");
        CONFIG = configSupplier.get();

        try {
            if (!CONFIG.getFile().exists()) {
                LOGGER.warn("Configuration not found. Creating defaults...");
                setDefaultValues();
                saveConfig();
                com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
            } else {
                CONFIG.load();
                loadConfigValues();
                saveConfig();
                com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading configuration. Resetting to defaults.", t);
            setDefaultValues();
            saveConfig();
            com.axalotl.async.common.config.AsyncConfig.onConfigLoaded();
        }
    }

    public static void saveConfig() {
        setWithComment("disabled", disabled, "Disables the mod. All entities are ticked on the main thread.");
        setWithComment("maxThreads", maxThreads,
                "Worker threads for parallel ticking. -1 = auto: scaled down from the logical thread count. Capped at the logical processor count.");
        setWithComment("synchronizedEntities", new ArrayList<>(synchronizedEntities),
                """
                        Entities ticked on the main thread instead of in parallel.
                        Entity IDs or namespaces (*):
                          - 'minecraft:zombie' = specific entity
                          - 'minecraft:*'      = all entities in namespace""");
        setWithComment("enableAsyncSpawn", enableAsyncSpawn,
                "Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.");
        setWithComment("enableAsyncRandomTicks", enableAsyncRandomTicks,
                "Experimental! Enables async random ticks.");
        setWithComment("synchronizeUnannotatedModEntities", synchronizeUnannotatedModEntities,
                "Ticks non-Minecraft entities without @AsyncCompatible on the main thread.");

        CONFIG.save();
        LOGGER.info("Configuration saved.");
    }

    private static void setWithComment(String key, Object value, String comment) {
        CONFIG.set(key, value);
        CONFIG.setComment(key, comment);
    }

    private static void loadConfigValues() {
        removeUnusedKeys();

        disabled = CONFIG.getOrElse("disabled", disabled);
        maxThreads = CONFIG.getOrElse("maxThreads", maxThreads);
        enableAsyncSpawn = CONFIG.getOrElse("enableAsyncSpawn", enableAsyncSpawn);
        enableAsyncRandomTicks = CONFIG.getOrElse("enableAsyncRandomTicks", enableAsyncRandomTicks);
        synchronizeUnannotatedModEntities = CONFIG.getOrElse(
                "synchronizeUnannotatedModEntities",
                synchronizeUnannotatedModEntities
        );

        List<String> entries = CONFIG.get("synchronizedEntities");
        if (entries != null) {
            synchronizedEntities = new HashSet<>(entries);
        }

        restoreComments();
    }

    private static void restoreComments() {
        setCommentIfExists("disabled", "Disables the mod. All entities are ticked on the main thread.");
        setCommentIfExists("maxThreads",
                "Worker threads for parallel ticking. -1 = auto: scaled down from the logical thread count. Capped at the logical processor count.");
        setCommentIfExists("synchronizedEntities", """
                Entities ticked on the main thread instead of in parallel.
                Entity IDs or namespaces (*):
                  - 'minecraft:zombie' = specific entity
                  - 'minecraft:*'      = all entities in namespace""");
        setCommentIfExists("enableAsyncSpawn", "Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.");
        setCommentIfExists("enableAsyncRandomTicks", "Experimental! Enables async random ticks.");
        setCommentIfExists("synchronizeUnannotatedModEntities",
                "Ticks non-Minecraft entities without @AsyncCompatible on the main thread.");
    }

    private static void setCommentIfExists(String key, String comment) {
        if (CONFIG.contains(key)) {
            CONFIG.setComment(key, comment);
        }
    }

    private static void removeUnusedKeys() {
        List<String> keysToRemove = new java.util.ArrayList<>();

        for (CommentedConfig.Entry entry : CONFIG.entrySet()) {
            String key = entry.getKey();
            if (!VALID_KEYS.contains(key)) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            CONFIG.remove(key);
            LOGGER.warn("Removed unused config key: {}", key);
        }
    }

    private static void setDefaultValues() {
        disabled = false;
        maxThreads = -1;
        enableAsyncSpawn = false;
        enableAsyncRandomTicks = false;
        synchronizeUnannotatedModEntities = false;
        synchronizedEntities = getDefaultSynchronizedEntities();
    }
}
