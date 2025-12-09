package com.axalotl.async.common.config;

import com.axalotl.async.common.parallelised.utils.ModCompatible;
import com.axalotl.async.common.platform.PlatformUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AsyncConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger(AsyncConfig.class);

    public static boolean disabled = false;
    public static int maxThreads = -1;
    public static boolean enableAsyncSpawn = true;
    public static boolean enableAsyncRandomTicks = false;
    public static Set<String> synchronizedEntities = getDefaultSynchronizedEntities();

    // Caches
    private static final Map<Identifier, Boolean> syncCache = new ConcurrentHashMap<>();
    private static final Set<String> exactEntities = new HashSet<>();
    private static final Set<String> namespaceWildcards = new HashSet<>();

    public static Set<String> getDefaultSynchronizedEntities() {
        final Set<String> defaultSynchronizedEntities = new HashSet<>(ModCompatible.addUnsupportedMods());
        defaultSynchronizedEntities.addAll(Set.of(
                "minecraft:tnt",
                "minecraft:item",
                "minecraft:experience_orb"
        ));
        return defaultSynchronizedEntities;
    }

    public static int getParallelism() {
        if (maxThreads <= 0) return Runtime.getRuntime().availableProcessors();
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), maxThreads));
    }

    public static boolean isNamespaceWildcard(String input) {
        if (input == null) return false;
        int colon = input.indexOf(':');
        if (colon <= 0) return false;
        return input.substring(colon + 1).equals("*");
    }

    public static boolean existsNamespace(String namespace) {
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            if (id.getNamespace().equals(namespace)) return true;
        }
        return false;
    }

    public static boolean matchesExistingNamespaceWildcard(String input) {
        if (!isNamespaceWildcard(input)) return false;
        String ns = input.substring(0, input.indexOf(':'));
        return existsNamespace(ns);
    }

    public static void syncEntity(String entity) {
        if (synchronizedEntities.add(entity)) {
            rebuildCaches();
            PlatformUtils.saveConfig();
            LOGGER.info("Added sync entity: {}", entity);
        } else {
            LOGGER.warn("Entity already synchronized: {}", entity);
        }
    }

    public static void removeEntity(String entity) {
        if (synchronizedEntities.remove(entity)) {
            rebuildCaches();
            PlatformUtils.saveConfig();
            LOGGER.info("Removed sync entity: {}", entity);
        } else {
            LOGGER.warn("Entity not found: {}", entity);
        }
    }

    private static void rebuildCaches() {
        syncCache.clear();
        exactEntities.clear();
        namespaceWildcards.clear();

        for (String entry : synchronizedEntities) {
            if (isNamespaceWildcard(entry)) {
                String ns = entry.substring(0, entry.indexOf(':'));
                namespaceWildcards.add(ns);
            } else {
                exactEntities.add(entry);
            }
        }
    }

    public static boolean isEntitySynchronized(Identifier entityId) {
        Boolean cached = syncCache.get(entityId);
        if (cached != null) return cached;

        String idString = entityId.toString();
        if (exactEntities.contains(idString)) {
            syncCache.put(entityId, true);
            return true;
        }

        if (namespaceWildcards.contains(entityId.getNamespace())) {
            syncCache.put(entityId, true);
            return true;
        }

        syncCache.put(entityId, false);
        return false;
    }

    public static void onConfigLoaded() {
        rebuildCaches();
        LOGGER.info("Configuration loaded.");
    }

    public static void clearCaches() {
        syncCache.clear();
    }
}