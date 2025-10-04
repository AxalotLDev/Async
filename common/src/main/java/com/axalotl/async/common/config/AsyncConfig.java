package com.axalotl.async.common.config;

import com.axalotl.async.common.platform.PlatformEvents;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AsyncConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger("Async Config");

    public static boolean disabled = false;
    public static int paraMax = -1;
    public static boolean enableAsyncSpawn = true;
    public static boolean enableAsyncRandomTicks = false;

    public static Set<ResourceLocation> synchronizedEntities = getDefaultSynchronizedEntities();

    public static Set<ResourceLocation> getDefaultSynchronizedEntities() {
        return Set.of(
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "tnt")),
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "item")),
                Objects.requireNonNull(ResourceLocation.tryBuild("minecraft", "experience_orb"))
        );
    }

    public static int getParallelism() {
        if (paraMax <= 0) return Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), paraMax));
    }

    public static void syncEntity(ResourceLocation entityId) {
        if (synchronizedEntities.add(entityId)) {
            PlatformEvents.getInstance().saveConfig();
            LOGGER.info("Sync entity class: {}", entityId);
        } else {
            LOGGER.warn("Entity class already synchronized: {}", entityId);
        }
    }

    public static void asyncEntity(ResourceLocation entityId) {
        if (synchronizedEntities.remove(entityId)) {
            PlatformEvents.getInstance().saveConfig();
            LOGGER.info("Enable async process entity class: {}", entityId);
        } else {
            LOGGER.warn("Entity class not found: {}", entityId);
        }
    }
}