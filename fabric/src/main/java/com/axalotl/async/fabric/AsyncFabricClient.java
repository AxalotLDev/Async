package com.axalotl.async.fabric;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public class AsyncFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            AsyncFabric.LOGGER.info("Async Setting up client thread-pool...");
            ParallelProcessor.setupClientThreadPool(AsyncConfig.getParallelism(), this.getClass());
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            AsyncFabric.LOGGER.info("Async Stopping client thread-pool...");
            ParallelProcessor.stopClient();
        });
    }
}
