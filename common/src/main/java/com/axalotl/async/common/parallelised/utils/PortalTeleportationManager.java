package com.axalotl.async.common.parallelised.utils;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.TeleportTransition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalTeleportationManager {

    private static final Logger LOGGER = LogManager.getLogger("Async-Portal");

    private static volatile DefaultEventExecutor portalExecutor;
    private static volatile MinecraftServer server;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private PortalTeleportationManager() {}

    public static void init(MinecraftServer mcServer) {
        if (initialized.compareAndSet(false, true)) {
            server = mcServer;
            portalExecutor = new DefaultEventExecutor(
                    new DefaultThreadFactory("Async-Portal", true)
            );
            LOGGER.info("Portal teleportation manager initialized");
        }
    }

    public static void shutdown() {
        if (initialized.compareAndSet(true, false)) {
            if (portalExecutor != null) {
                portalExecutor.shutdownGracefully();
                portalExecutor = null;
            }
            server = null;
            LOGGER.info("Portal teleportation manager shut down");
        }
    }

    private static void processRequestOnExecutor(
            Entity entity, Portal portal, BlockPos entryPos,
            ServerLevel sourceLevel
    ) {
        CompletableFuture<Void> mainThreadTask = CompletableFuture.runAsync(() -> executePortalTeleport(entity, portal, entryPos, sourceLevel), sourceLevel.getChunkSource().mainThreadProcessor);
        mainThreadTask.join();
    }

    public static void submitAndAwait(Entity entity, Portal portal, BlockPos entryPos, ServerLevel sourceLevel) {
        DefaultEventExecutor executor = portalExecutor;
        if (executor == null || executor.isShutdown()
                || Thread.currentThread() == sourceLevel.getServer().getRunningThread()) {
            executePortalTeleport(entity, portal, entryPos, sourceLevel);
            return;
        }

        CompletableFuture<Void> result = new CompletableFuture<>();

        executor.execute(() -> {
            processRequestOnExecutor(entity, portal, entryPos, sourceLevel);
            result.complete(null);
        });

        result.join();
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