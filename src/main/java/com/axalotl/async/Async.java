package com.axalotl.async;

import com.axalotl.async.commands.AsyncCommand;
import com.axalotl.async.commands.StatsCommand;
import com.axalotl.async.config.AsyncConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Async implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger(Async.class);
    public static final boolean VMP = FabricLoader.getInstance().isModLoaded("vmp");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Async...");
        AsyncConfig.init();

        if (VMP && AsyncConfig.enableAsyncSpawn) {
            LOGGER.error("Incompatible configuration: Async spawn enabled while VMP mod is active. Crashing to prevent instability.");
            throw new RuntimeException("Crashing due to VMP mod incompatibility with Async Spawn Configuration.");
        }

        StatsCommand.runStatsThread();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            LOGGER.info("Async Setting up thread-pool...");
            ParallelProcessor.setServer(server);
            ParallelProcessor.setupThreadPool(AsyncConfig.getParallelism());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> AsyncCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("Shutting down Async thread pool...");
            ParallelProcessor.stop();
            StatsCommand.shutdown();
        });

        LOGGER.info("Async Initialized successfully!");
    }
}