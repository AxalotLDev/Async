package com.axalotl.async.mixin;

import com.axalotl.async.ParallelProcessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantThreadExecutor<ServerTask> implements CommandOutput, AutoCloseable {
    @Shadow
    public abstract ServerWorld getOverworld();

    @Final
    @Shadow
    @Mutable
    private Thread serverThread;

    public MinecraftServerMixin(String string) {
        super(string);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void getServerObject(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ParallelProcessor.setServer((MinecraftServer) (Object) this);
    }

    @Redirect(method = "reloadResources", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isOnThread()Z"))
    private boolean onServerExecutionThreadPatch(MinecraftServer minecraftServer) {
        return ParallelProcessor.serverExecutionThreadPatch();
    }

    @Redirect(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I"))
    private int initialChunkCountBypass(ServerChunkManager instance) {
        int loaded = this.getOverworld().getChunkManager().getLoadedChunkCount();
        return Math.min(loaded, 441);
    }

    @Override
    public synchronized boolean isOnThread() {
        Thread getThreadThread = Thread.currentThread();
        String name = getThreadThread.getName();
        if (name.startsWith("Async") || name.startsWith("Worker")) {
            return true;
        }
        return getThreadThread == serverThread;
    }

    @Override
    public synchronized Thread getThread() {
        Thread getThreadThread = Thread.currentThread();
        String name = getThreadThread.getName();
        if (name.startsWith("Async") || name.startsWith("Worker")) {
            return getThreadThread;
        }
        return serverThread;
    }
}

