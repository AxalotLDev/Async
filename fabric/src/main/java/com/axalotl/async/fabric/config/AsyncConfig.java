package com.axalotl.async.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
            "enableAsyncRandomTicks"
    );

    public static void init() {
        LOGGER.info("Initializing Async Config...");
        CONFIG = configSupplier.get();

        try {
            if (!CONFIG.getFile().exists()) {
                LOGGER.warn("Configuration not found. Creating defaults...");
                setDefaultValues();
                saveConfig();
            } else {
                CONFIG.load();
                loadConfigValues();
                LOGGER.info("Configuration loaded.");
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading configuration. Resetting to defaults.", t);
            setDefaultValues();
            saveConfig();
        }
    }

    public static void saveConfig() {
        setWithComment("disabled", disabled, "Enables parallel processing of entities.");
        setWithComment("maxThreads", maxThreads, "Maximum worker threads. -1 = auto.");
        setWithComment("synchronizedEntities",
                synchronizedEntities.stream().map(ResourceLocation::toString).toList(),
                "List of entity IDs that must ALWAYS tick synchronously.");
        setWithComment("enableAsyncSpawn", enableAsyncSpawn,
                "Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.");
        setWithComment("enableAsyncRandomTicks", enableAsyncRandomTicks,
                "Experimental! Enables async random ticks.");

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

        List<String> ids = CONFIG.get("synchronizedEntities");
        if (ids != null) {
            synchronizedEntities = ids.stream()
                    .map(ResourceLocation::tryParse)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        restoreComments();
        CONFIG.save();
    }

    private static void restoreComments() {
        setCommentIfExists("disabled", "Enables parallel processing of entities.");
        setCommentIfExists("maxThreads", "Maximum worker threads. -1 = auto.");
        setCommentIfExists("synchronizedEntities", "List of entity IDs that must ALWAYS tick synchronously.");
        setCommentIfExists("enableAsyncSpawn", "Enables async entity spawning. WARNING: incompatible with Carpet's lagFreeSpawning.");
        setCommentIfExists("enableAsyncRandomTicks", "Experimental! Enables async random ticks.");
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
        enableAsyncSpawn = true;
        enableAsyncRandomTicks = false;
        synchronizedEntities = getDefaultSynchronizedEntities();
    }
}