package com.axalotl.async.neoforge;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class AsyncNeoForgeClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AsyncNeoForge.LOGGER.info("Async Setting up client thread-pool...");
        ParallelProcessor.setupClientThreadPool(AsyncConfig.getParallelism(), AsyncNeoForgeClient.class);

        // The game is shutting down, so we need to stop our client thread pool.
        // There is no client stopping event in NeoForge, so we use this instead.
        // This is not ideal, but it's the best we can do.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            AsyncNeoForge.LOGGER.info("Async Stopping client thread-pool...");
            ParallelProcessor.stopClient();
        }));
    }
}
