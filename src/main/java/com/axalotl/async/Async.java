package com.axalotl.async;

import com.axalotl.async.commands.AsyncCommand;
import com.axalotl.async.commands.StatsCommand;
import com.axalotl.async.config.AsyncConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Async.MOD_ID)
public class Async {

    public static final String MOD_ID = "async";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static Boolean c2me = false;
    public static final boolean VMP = false;

    public Async(FMLJavaModLoadingContext context) {
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Initializing Async...");
        c2me = ModList.get().isLoaded("c2me");
        context.registerConfig(ModConfig.Type.COMMON, AsyncConfig.SPEC, "async.toml");

        if (VMP && AsyncConfig.enableAsyncSpawn) {
            LOGGER.error("Incompatible configuration: Async spawn enabled while VMP mod is active. Crashing to prevent instability.");
            throw new RuntimeException("Crashing due to VMP mod incompatibility with Async Spawn Configuration.");
        }
        LOGGER.info("Async Initialized successfully");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Async Setting up thread-pool...");
        AsyncConfig.castConfig();
        StatsCommand.runStatsThread();
        ParallelProcessor.setServer(event.getServer());
        ParallelProcessor.setupThreadPool(AsyncConfig.getParallelism());
    }

    @SubscribeEvent
    public void registerCommandsEvent(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        AsyncCommand.register(dispatcher);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Shutting down Async thread pool...");
        ParallelProcessor.stop();
        StatsCommand.shutdown();
    }
}