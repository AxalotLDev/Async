package com.axalotl.async.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.axalotl.async.common.config.AsyncConfig.*;
import static com.axalotl.async.common.config.AsyncConfig.getDefaultSynchronizedEntities;

public class AsyncConfig {
    private static final Supplier<CommentedFileConfig> configSupplier =
            () -> CommentedFileConfig.builder(FabricLoader.getInstance().getConfigDir().resolve("async.toml"))
                    .preserveInsertionOrder()
                    .sync()
                    .build();

    private static CommentedFileConfig CONFIG;

    public static void init() {
        LOGGER.info("Initializing Async Config...");
        CONFIG = configSupplier.get();
        try {
            if (!CONFIG.getFile().exists()) {
                LOGGER.warn("Configuration file not found, creating default configuration.");
                setDefaultValues();
                saveConfig();
            } else {
                CONFIG.load();
                loadConfigValues();
                saveConfig();
                LOGGER.info("Configuration successfully loaded.");
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading configuration, resetting to default values.", t);
            setDefaultValues();
            saveConfig();
        }
    }

    public static void saveConfig() {
        CONFIG.set(disabled.getKey(), disabled.getValue());
        CONFIG.setComment(disabled.getKey(), "Globally disable all toggleable functionality within the async system. Set to true to stop all asynchronous operations.");

        CONFIG.set(paraMax.getKey(), paraMax.getValue());
        CONFIG.setComment(paraMax.getKey(), "Maximum number of threads to use for parallel processing. Set to -1 to use default value. Note: If 'virtualThreads' is enabled, this setting will be ignored.");

        CONFIG.set(enableEntityMoveSync.getKey(), enableEntityMoveSync.getValue());
        CONFIG.setComment(enableEntityMoveSync.getKey(), "Modifies entity movement processing: true for synchronous movement (vanilla mechanics intact, less performance), false for asynchronous movement (better performance, may break mechanics).");

        CONFIG.set(synchronizedEntities.getKey(), synchronizedEntities.getValue().stream().map(ResourceLocation::toString).toList());
        CONFIG.setComment(synchronizedEntities.getKey(), "List of entity class for sync processing.");

        CONFIG.set(enableAsyncSpawn.getKey(), enableAsyncSpawn.getValue());
        CONFIG.setComment(enableAsyncSpawn.getKey(), "Enables parallel processing of entity spawns. Warning, incompatible with VMP mod && Carpet mod lagFreeSpawning rule.");

        CONFIG.set(recoverFromErrors.getKey(), recoverFromErrors.getValue());
        CONFIG.setComment(recoverFromErrors.getKey(), "Tries to recover from entity processing errors instead of crashing.");

        CONFIG.save();
        LOGGER.info("Configuration saved successfully.");
    }

    private static void loadConfigValues() {
        Set<String> processedKeys = new HashSet<>(List.of(
                disabled.getKey(),
                paraMax.getKey(),
                enableEntityMoveSync.getKey(),
                synchronizedEntities.getKey()));

        disabled.setValue(CONFIG.getOrElse(disabled.getKey(), disabled.getValue()));
        paraMax.setValue(CONFIG.getOrElse(paraMax.getKey(), paraMax.getValue()));
        enableEntityMoveSync.setValue(CONFIG.getOrElse(enableEntityMoveSync.getKey(), enableEntityMoveSync.getValue()));
        enableAsyncSpawn.setValue(CONFIG.getOrElse(enableAsyncSpawn.getKey(), enableAsyncSpawn.getValue()));
        recoverFromErrors.setValue(CONFIG.getOrElse(recoverFromErrors.getKey(), recoverFromErrors.getValue()));

        synchronizedEntities.setValue(new HashSet<>());
        CONFIG.<List<String>>getOptional(synchronizedEntities.getKey()).ifPresentOrElse(ids -> {
            for (String id : ids) {
                ResourceLocation identifier = ResourceLocation.tryParse(id);
                if (identifier != null) {
                    synchronizedEntities.getValue().add(identifier);
                }
            }
        }, () -> synchronizedEntities.setValue(getDefaultSynchronizedEntities()));

        Set<String> keysToRemove = new HashSet<>();
        for (CommentedConfig.Entry entry : CONFIG.entrySet()) {
            String key = entry.getKey();
            if (!processedKeys.contains(key)) {
                keysToRemove.add(key);
            }
        }

        for (String key : keysToRemove) {
            LOGGER.warn("Removing unused configuration key: {}", key);
            CONFIG.remove(key);
        }

        CONFIG.save();
    }

    private static void setDefaultValues() {
        disabled.setValue(false);
        paraMax.setValue(-1);
        enableEntityMoveSync.setValue(false);
        enableAsyncSpawn.setValue(false);
        synchronizedEntities.setValue(getDefaultSynchronizedEntities());
    }
}