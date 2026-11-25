package com.axalotl.async.common.ticking;

import com.axalotl.async.api.utils.AsyncCompatible;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.minecraft.resources.Identifier;
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
import java.util.concurrent.ConcurrentHashMap;

public class TickingPolicy {

    private static final Set<Class<?>> BLOCKED_ENTITIES = Set.of(
            FallingBlockEntity.class,
            Shulker.class,
            AbstractBoat.class,
            EnderDragon.class,
            AbstractMinecart.class
    );

    private static final Map<Class<?>, Boolean> ASYNC_COMPATIBLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Boolean> SYNC_BY_TYPE = new ConcurrentHashMap<>();

    public static boolean shouldTickSynchronously(Entity entity) {
        if (ParallelProcessor.isShuttingDown() || AsyncConfig.disabled || entity.portalProcess != null || entity.level().isClientSide()) {
            return true;
        }

        Identifier typeId = EntityType.getKey(entity.getType());
        Boolean cached = SYNC_BY_TYPE.get(typeId);
        if (cached != null) {
            return cached;
        }

        boolean sync = !isAsyncEligible(entity.getClass(), typeId.getNamespace())
                || entity instanceof Projectile
                || entity instanceof Player
                || isBlockedEntityClass(entity.getClass())
                || AsyncConfig.isEntitySynchronized(typeId);
        SYNC_BY_TYPE.put(typeId, sync);
        return sync;
    }

    public static void onSyncRulesChanged() {
        SYNC_BY_TYPE.clear();
    }

    public static void reset() {
        ASYNC_COMPATIBLE_CACHE.clear();
        SYNC_BY_TYPE.clear();
    }

    private static boolean isAsyncEligible(Class<?> entityClass, String namespace) {
        if (AsyncConfig.isModAsyncEligible(namespace)) {
            return true;
        }
        return ASYNC_COMPATIBLE_CACHE.computeIfAbsent(entityClass,
                cls -> cls.isAnnotationPresent(AsyncCompatible.class));
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
