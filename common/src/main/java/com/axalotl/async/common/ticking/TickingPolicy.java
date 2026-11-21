package com.axalotl.async.common.ticking;

import com.axalotl.async.api.utils.AsyncCompatible;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TickingPolicy {

    private static final Set<UUID> BLACKLISTED_ENTITIES = ConcurrentHashMap.newKeySet();
    private static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class,
            EnderDragon.class,
            AbstractMinecart.class
    );

    private static final Map<Class<?>, Boolean> ASYNC_API_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> SYNC_BY_CLASS = new ConcurrentHashMap<>();

    public static boolean shouldTickSynchronously(Entity entity) {
        if (ParallelProcessor.isShuttingDown() || AsyncConfig.disabled || entity.portalProcess != null || entity.level().isClientSide()) {
            return true;
        }

        if (!BLACKLISTED_ENTITIES.isEmpty() && BLACKLISTED_ENTITIES.contains(entity.getUUID())) {
            return true;
        }

        Boolean cached = SYNC_BY_CLASS.get(entity.getClass());
        if (cached != null) {
            return cached;
        }

        boolean sync = entitySupportsAsyncApi(entity)
                || entity instanceof Projectile
                || entity instanceof Player
                || isBlockedEntityClass(entity.getClass())
                || AsyncConfig.isEntitySynchronized(EntityType.getKey(entity.getType()));
        SYNC_BY_CLASS.put(entity.getClass(), sync);
        return sync;
    }

    public static boolean entitySupportsAsyncApi(Entity entity) {
        Class<?> cls = entity.getClass();
        Boolean cached = ASYNC_API_CACHE.get(cls);
        if (cached != null) return cached;
        boolean result = !"minecraft".equals(EntityType.getKey(entity.getType()).getNamespace()) && !cls.isAnnotationPresent(AsyncCompatible.class);
        ASYNC_API_CACHE.put(cls, result);
        return result;
    }

    public static void onSyncRulesChanged() {
        SYNC_BY_CLASS.clear();
    }

    public static void reset() {
        BLACKLISTED_ENTITIES.clear();
        ASYNC_API_CACHE.clear();
        SYNC_BY_CLASS.clear();
    }

    private static boolean isBlockedEntityClass(Class<?> entityClass) {
        for (Class<?> current = entityClass; current != null; current = current.getSuperclass()) {
            if (BLOCKED_ENTITIES.contains(current)) {
                return true;
            }
        }
        return false;
    }
}
