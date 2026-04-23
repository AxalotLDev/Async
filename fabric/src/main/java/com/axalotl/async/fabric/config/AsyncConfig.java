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
            "threadPriority",
            "synchronizedEntities",
            "enableAsyncSpawn",
            "enableAsyncRandomTicks",
            "enableAsyncVmpTracking"
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
        setWithComment("disabled", disabled, "Enables parallel processing of entities.");
        setWithComment("maxThreads", maxThreads, "Maximum worker threads. -1 = auto (uses all cores).");
        setWithComment("threadPriority", threadPriority, "Worker-thread OS priority (1..10). Default 4 is below server/render thread (5) so they keep responsiveness on saturated CPUs.");
        setWithComment("synchronizedEntities", new ArrayList<>(synchronizedEntities),
                """
                        List of entity IDs or namespaces (*):
                          - 'minecraft:zombie' = specific entity
                          - 'minecraft:*'      = all entities in namespace""");
        setWithComment("enableAsyncSpawn", enableAsyncSpawn,
                "Reserved flag. No-op currently — parallel spawn was removed after profiling showed it was a net regression.");
        setWithComment("enableAsyncRandomTicks", enableAsyncRandomTicks,
                "Experimental! Enables async random ticks.");
        setWithComment("enableAsyncVmpTracking", enableAsyncVmpTracking,
                "Experimental! Offloads VMP's NearbyEntityTracking tryTick calls to the async pool. No effect without VMP installed.");

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
        threadPriority = CONFIG.getOrElse("threadPriority", threadPriority);
        enableAsyncSpawn = CONFIG.getOrElse("enableAsyncSpawn", enableAsyncSpawn);
        enableAsyncRandomTicks = CONFIG.getOrElse("enableAsyncRandomTicks", enableAsyncRandomTicks);
        enableAsyncVmpTracking = CONFIG.getOrElse("enableAsyncVmpTracking", enableAsyncVmpTracking);

        List<String> entries = CONFIG.get("synchronizedEntities");
        if (entries != null) {
            synchronizedEntities = new HashSet<>(entries);
        }

        restoreComments();
        CONFIG.save();
    }

    private static void restoreComments() {
        setCommentIfExists("disabled", "Enables parallel processing of entities.");
        setCommentIfExists("maxThreads", "Maximum worker threads. -1 = auto (uses all cores).");
        setCommentIfExists("threadPriority", "Worker-thread OS priority (1..10). Default 4 is below server/render thread (5) so they keep responsiveness on saturated CPUs.");
        setCommentIfExists("synchronizedEntities", """
                List of entity IDs or namespaces (*):
                  - 'minecraft:zombie' = specific entity
                  - 'minecraft:*'      = all entities in namespace""");
        setCommentIfExists("enableAsyncSpawn", "Reserved flag. No-op currently — parallel spawn was removed after profiling showed it was a net regression.");
        setCommentIfExists("enableAsyncRandomTicks", "Experimental! Enables async random ticks.");
        setCommentIfExists("enableAsyncVmpTracking", "Experimental! Offloads VMP's NearbyEntityTracking tryTick calls to the async pool. No effect without VMP installed.");
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
        threadPriority = Thread.NORM_PRIORITY - 1;
        enableAsyncSpawn = false;
        enableAsyncRandomTicks = false;
        enableAsyncVmpTracking = false;
        synchronizedEntities = getDefaultSynchronizedEntities();
    }
}