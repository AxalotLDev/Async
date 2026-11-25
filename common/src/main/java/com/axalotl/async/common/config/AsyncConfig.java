package com.axalotl.async.common.config;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.platform.PlatformUtils;
import com.axalotl.async.common.ticking.TickingPolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AsyncConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger(AsyncConfig.class);

    public static final String ANY_MOD = "*";
    public static final String VANILLA_NAMESPACE = "minecraft";

    public static boolean disabled = false;
    public static int maxThreads = -1;
    public static boolean enableAsyncSpawn = false;
    public static boolean enableAsyncRandomTicks = false;
    public static Set<String> synchronizedEntities = getDefaultSynchronizedEntities();
    public static Set<String> asyncMods = getDefaultAsyncMods();

    // Caches
    private static final Map<Identifier, Boolean> syncCache = new ConcurrentHashMap<>();
    private static final Set<String> exactEntities = new HashSet<>();
    private static final Set<String> namespaceWildcards = new HashSet<>();
    private static final Set<String> asyncModIds = new HashSet<>();
    private static boolean asyncModsWildcard;
    private static boolean modsInitialized;

    private static final Set<String> warnedAsyncMods = new HashSet<>();
    private static String loggedAsyncEligibleMods = "";

    public static Set<String> getDefaultSynchronizedEntities() {
        return new HashSet<>(Set.of(
                "minecraft:tnt",
                "minecraft:item",
                "minecraft:experience_orb"
        ));
    }

    public static Set<String> getDefaultAsyncMods() {
        return new HashSet<>();
    }

    public static int getParallelism() {
        int cores = Runtime.getRuntime().availableProcessors();
        if (maxThreads > 0) {
            return Math.clamp(maxThreads, 1, cores);
        }

        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        int threads = (int) (cores / (windows ? 1.6 : 1.3));

        MinecraftServer server = ParallelProcessor.getServer();
        if (server != null && !server.isDedicatedServer()) {
            threads--;
        }
        return Math.max(1, threads);
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

    public static boolean isModAsyncEligible(String namespace) {
        if (namespace == null) return false;
        return VANILLA_NAMESPACE.equals(namespace)
                || asyncModsWildcard
                || asyncModIds.contains(namespace);
    }

    public static void syncEntity(String entity) {
        if (synchronizedEntities.add(entity)) {
            persistChanges();
            LOGGER.info("Added sync entity: {}", entity);
        } else {
            LOGGER.warn("Entity already synchronized: {}", entity);
        }
    }

    public static void removeEntity(String entity) {
        if (synchronizedEntities.remove(entity)) {
            persistChanges();
            LOGGER.info("Removed sync entity: {}", entity);
        } else {
            LOGGER.warn("Entity not found: {}", entity);
        }
    }

    public static void addAsyncMod(String modId) {
        if (ANY_MOD.equals(modId)) {
            asyncMods.retainAll(Set.of(ANY_MOD));
        }

        if (asyncMods.add(modId)) {
            persistChanges();
            LOGGER.info("Added async-eligible mod: {}", modId);
        }
    }

    public static void removeAsyncMod(String modId) {
        if (asyncMods.remove(modId)) {
            persistChanges();
            LOGGER.info("Removed async-eligible mod: {}", modId);
        }
    }

    public static void clearAsyncMods() {
        if (asyncMods.isEmpty()) return;
        asyncMods.clear();
        persistChanges();
        LOGGER.info("Cleared async-eligible mods");
    }

    private static void persistChanges() {
        rebuildCaches();
        PlatformUtils.saveConfig();
    }

    private static void rebuildCaches() {
        clearCaches();

        for (String entry : synchronizedEntities) {
            if (isNamespaceWildcard(entry)) {
                String ns = entry.substring(0, entry.indexOf(':'));
                namespaceWildcards.add(ns);
            } else {
                exactEntities.add(entry);
            }
        }

        for (String entry : asyncMods) {
            if (ANY_MOD.equals(entry)) {
                asyncModsWildcard = true;
            } else if (!PlatformUtils.isModLoaded(entry)) {
                warnAsyncModOnce(entry, "is not loaded, skipping");
            } else if (modsInitialized && !existsNamespace(entry)) {
                warnAsyncModOnce(entry, "has no entities, skipping");
            } else {
                asyncModIds.add(entry);
            }
        }

        logAsyncEligibleMods();
    }

    private static void warnAsyncModOnce(String modId, String reason) {
        if (warnedAsyncMods.add(modId)) {
            LOGGER.warn("Mod '{}' {}.", modId, reason);
        }
    }

    private static void logAsyncEligibleMods() {
        if (asyncModsWildcard) {
            logAsyncEligibleMods("every mod");
            return;
        }

        Set<String> eligibleMods = new HashSet<>(asyncModIds);
        eligibleMods.add(VANILLA_NAMESPACE);
        logAsyncEligibleMods(eligibleMods.toString());
    }

    private static void logAsyncEligibleMods(String state) {
        if (loggedAsyncEligibleMods.equals(state)) return;
        loggedAsyncEligibleMods = state;
        LOGGER.info("Async-eligible mods: {}", state);
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
        ParallelProcessor.rebuildThreadPool();
        LOGGER.info("Configuration loaded.");
    }

    public static void onServerStarted() {
        modsInitialized = true;
        rebuildCaches();
    }

    public static void clearCaches() {
        TickingPolicy.onSyncRulesChanged();
        syncCache.clear();
        exactEntities.clear();
        namespaceWildcards.clear();
        asyncModIds.clear();
        asyncModsWildcard = false;
    }
}