package com.axalotl.async.fabric;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.common.commands.StatsCommand;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.fabric.platform.FabricPlatformEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.axalotl.async.common.config.AsyncConfig.enableAsyncSpawn;

public class Async implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger(Async.class);
    public static final boolean VMP = FabricLoader.getInstance().isModLoaded("vmp");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Async...");
        com.axalotl.async.fabric.config.AsyncConfig.init();
        FabricPlatformEvents.init();
        if (VMP && enableAsyncSpawn.getValue()) {
            LOGGER.error("Incompatible configuration: Async spawn enabled while VMP mod is active. Crashing to prevent instability.");
            throw new RuntimeException("Crashing due to VMP mod incompatibility with Async Spawn Configuration.");
        }

        StatsCommand.runStatsThread();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Async Setting up thread-pool...");
            ParallelProcessor.setServer(server);
            ParallelProcessor.setupThreadPool(AsyncConfig.getParallelism(), this.getClass());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AsyncCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Shutting down Async thread pool...");
            ParallelProcessor.stop();
            StatsCommand.shutdown();
        });

        LOGGER.info("Async Initialized successfully");
    }
}