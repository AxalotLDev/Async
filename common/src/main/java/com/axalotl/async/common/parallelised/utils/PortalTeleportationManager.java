package com.axalotl.async.common.parallelised.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalTeleportationManager {

    private static final Logger LOGGER = LogManager.getLogger("Async-Portal");

    private static volatile MinecraftServer server;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Queue<Runnable> PENDING = new ConcurrentLinkedQueue<>();

    private PortalTeleportationManager() {}

    public static void init(MinecraftServer mcServer) {
        if (initialized.compareAndSet(false, true)) {
            server = mcServer;
            LOGGER.info("Portal teleportation manager initialized");
        }
    }

    public static void shutdown() {
        if (initialized.compareAndSet(true, false)) {
            server = null;
            IN_FLIGHT.clear();
            PENDING.clear();
            LOGGER.info("Portal teleportation manager shut down");
        }
    }

    public static boolean submit(Entity entity, Portal portal, BlockPos entryPos, ServerLevel sourceLevel) {
        MinecraftServer srv = sourceLevel.getServer();
        if (srv == null || Thread.currentThread() == srv.getRunningThread()) {
            executePortalTeleport(entity, portal, entryPos, sourceLevel);
            return true;
        }

        final UUID id = entity.getUUID();
        if (!IN_FLIGHT.add(id)) {
            return false;
        }

        PENDING.offer(() -> {
            try {
                executePortalTeleport(entity, portal, entryPos, sourceLevel);
            } catch (Throwable t) {
                LOGGER.error("Deferred portal teleport failed for entity {}", id, t);
            } finally {
                IN_FLIGHT.remove(id);
            }
        });
        return false;
    }

    public static void drainPending() {
        Runnable task;
        while ((task = PENDING.poll()) != null) {
            try {
                task.run();
            } catch (Throwable t) {
                LOGGER.error("Portal teleport drain error", t);
            }
        }
    }

    private static void executePortalTeleport(Entity entity, Portal portal, BlockPos entryPos, ServerLevel sourceLevel) {
        if (entity.isRemoved()) {
            LOGGER.debug("Portal teleport skipped: entity {} already removed", entity.getId());
            return;
        }

        TeleportTransition transition = portal.getPortalDestination(sourceLevel, entity, entryPos);
        if (transition == null) {
            LOGGER.warn("Portal teleport failed: null transition for entity {} at {}",
                    entity.getId(), entryPos);
            return;
        }

        ServerLevel newLevel = transition.newLevel();
        if (!sourceLevel.isAllowedToEnterPortal(newLevel)) {
            LOGGER.warn("Portal teleport blocked: not allowed to enter {} for entity {}",
                    newLevel.dimension().identifier(), entity.getId());
            return;
        }

        if (newLevel.dimension() == sourceLevel.dimension() || entity.canTeleport(sourceLevel, newLevel)) {
            entity.teleport(transition);
        } else {
            LOGGER.warn("Portal teleport blocked: canTeleport returned false for entity {} ({} -> {})",
                    entity.getId(), sourceLevel.dimension().identifier(), newLevel.dimension().identifier());
        }
    }
}