package com.axalotl.async.mixin.server;

import com.axalotl.async.ParallelProcessor;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MinecraftServer.class, priority = Integer.MAX_VALUE)
public abstract class MinecraftServerMixin extends ReentrantBlockableEventLoop<TickTask> implements CommandSource, AutoCloseable {

    @Shadow
    @Final
    private Thread serverThread;

    public MinecraftServerMixin(String string) {
        super(string);
    }

    @Redirect(method = "reloadResources", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;isSameThread()Z"))
    private boolean onServerExecutionThreadPatch(MinecraftServer minecraftServer) {
        return ParallelProcessor.isServerExecutionThread();
    }


    @Override
    public synchronized boolean isSameThread() {
        Thread getThreadThread = Thread.currentThread();
        String name = getThreadThread.getName();
        if (name.startsWith("Async") || name.startsWith("Worker")) {
            return true;
        }
        return getThreadThread == serverThread;
    }
}

