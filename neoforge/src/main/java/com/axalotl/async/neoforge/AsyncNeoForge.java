package com.axalotl.async.neoforge;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.common.commands.StatsCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.javafmlmod.FMLModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import static com.axalotl.async.common.config.AsyncConfig.getParallelism;
import static com.axalotl.async.neoforge.config.AsyncConfig.SPEC;
import static com.axalotl.async.neoforge.config.AsyncConfig.loadConfig;

@Mod(AsyncNeoForge.MOD_ID)
public class AsyncNeoForge {
    public static final String MOD_ID = "async";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AsyncNeoForge(FMLModContainer container) {
        LOGGER.info("Initializing Async...");
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Initializing Async Config...");
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "async.toml");
        LOGGER.info("Async Initialized Successfully!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Async Setting up thread-pool...");
        loadConfig();
        StatsCommand.runStatsThread();
        ParallelProcessor.setServer(event.getServer());
        ParallelProcessor.setupThreadPool(getParallelism(), this.getClass());
    }

    @SubscribeEvent
    public void registerCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        AsyncCommand.register(dispatcher);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        StatsCommand.shutdown();
        ParallelProcessor.stop();
    }
}