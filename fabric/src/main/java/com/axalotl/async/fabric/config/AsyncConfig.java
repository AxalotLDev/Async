package com.axalotl.async.fabric.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
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
                LOGGER.info("Configuration successfully loaded.");
            }
        } catch (Throwable t) {
            LOGGER.error("Error loading configuration, resetting to default values.", t);
            setDefaultValues();
            saveConfig();
        }
    }

    public static void saveConfig() {
        CONFIG.set("disabled", disabled);
        CONFIG.setComment("disabled", "Enables parallel processing of entity.");

        CONFIG.set("paraMax", paraMax);
        CONFIG.setComment("paraMax", "Maximum number of threads to use for parallel processing. Set to -1 to use default value. Note: If 'virtualThreads' is enabled, this setting will be ignored.");

        CONFIG.set("synchronizedEntities", synchronizedEntities.stream().map(ResourceLocation::toString).toList());
        CONFIG.setComment("synchronizedEntities", "List of entity class for sync processing.");

        CONFIG.set("enableAsyncSpawn", enableAsyncSpawn);
        CONFIG.setComment("enableAsyncSpawn", "Enables parallel processing of entity spawns. Warning, incompatible with Carpet mod lagFreeSpawning rule.");

        CONFIG.set("enableAsyncRandomTicks", enableAsyncRandomTicks);
        CONFIG.setComment("enableAsyncRandomTicks", "Experimental! Enables async processing of random ticks.");

        CONFIG.save();
        LOGGER.info("Configuration saved successfully.");
    }

    private static void loadConfigValues() {
        disabled = CONFIG.getOrElse("disabled", disabled);
        paraMax = CONFIG.getOrElse("paraMax", paraMax);
        enableAsyncSpawn = CONFIG.getOrElse("enableAsyncSpawn", enableAsyncSpawn);
        enableAsyncRandomTicks = CONFIG.getOrElse("enableAsyncRandomTicks", enableAsyncRandomTicks);

        List<String> ids = CONFIG.get("synchronizedEntities");
        if (ids != null) {
            HashSet<ResourceLocation> set = new HashSet<>();
            for (String id : ids) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null) {
                    set.add(rl);
                }
            }
            com.axalotl.async.common.config.AsyncConfig.synchronizedEntities = set;
        }
    }

    private static void setDefaultValues() {
        disabled = false;
        paraMax = -1;
        enableAsyncSpawn = true;
        enableAsyncRandomTicks = false;
        synchronizedEntities = getDefaultSynchronizedEntities();
    }
}