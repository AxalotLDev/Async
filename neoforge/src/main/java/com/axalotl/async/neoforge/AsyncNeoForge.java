package com.axalotl.async.neoforge;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.commands.AsyncCommand;
import com.axalotl.async.neoforge.platform.NeoForgePermissions;
import com.mojang.brigadier.CommandDispatcher;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.javafmlmod.FMLModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

import static com.axalotl.async.common.config.AsyncConfig.getParallelism;
import static com.axalotl.async.neoforge.config.AsyncConfig.SPEC;
import static com.axalotl.async.neoforge.config.AsyncConfig.loadConfig;

@Mod(AsyncNeoForge.MOD_ID)
@Slf4j
public class AsyncNeoForge extends AsyncCommon {
    public static final String MOD_ID = "async";

    public AsyncNeoForge(FMLModContainer container) {
        log.info("Initializing Async...");
        NeoForge.EVENT_BUS.register(this);
        log.info("Initializing Async Config...");
        container.registerConfig(ModConfig.Type.COMMON, SPEC, "async.toml");
        log.info("Async Initialized Successfully!");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        log.info("Async Setting up thread-pool...");
        this.initialize();
        loadConfig();
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
        ParallelProcessor.stop();
    }

    @SubscribeEvent
    public void handlePermissionNodesGather(PermissionGatherEvent.Nodes event) {
        NeoForgePermissions.addNodes(event);
    }
}