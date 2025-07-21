package com.axalotl.async.common.config;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.axalotl.async.common.platform.PlatformEventBus.saveConfig;

public class AsyncConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger("Async Config");

    public static Map.Entry<String, Boolean> disabled = new AbstractMap.SimpleEntry<>("disabled", false);
    public static Map.Entry<String, Integer> paraMax = new AbstractMap.SimpleEntry<>("paraMax", -1);
    public static Map.Entry<String, Boolean> enableAsyncSpawn = new AbstractMap.SimpleEntry<>("enableAsyncSpawn", false);
    public static Map.Entry<String, Set<ResourceLocation>> synchronizedEntities = new AbstractMap.SimpleEntry<>("synchronizedEntities", getDefaultSynchronizedEntities());

    public static Set<ResourceLocation> getDefaultSynchronizedEntities() {
        return Set.of(
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "tnt")),
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "item")),
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "experience_orb"))
        );
    }

    public static int getParallelism() {
        if (paraMax.getValue() <= 0) return Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), paraMax.getValue()));
    }

    public static void syncEntity(ResourceLocation entityId) {
        if (synchronizedEntities.getValue().add(entityId)) {
            saveConfig();
            LOGGER.info("Sync entity class: {}", entityId);
        } else {
            LOGGER.warn("Entity class already synchronized: {}", entityId);
        }
    }

    public static void asyncEntity(ResourceLocation entityId) {
        if (synchronizedEntities.getValue().remove(entityId)) {
            saveConfig();
            LOGGER.info("Enable async process entity class: {}", entityId);
        } else {
            LOGGER.warn("Entity class not found: {}", entityId);
        }
    }
}
