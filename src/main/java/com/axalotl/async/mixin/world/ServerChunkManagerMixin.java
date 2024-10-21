package com.axalotl.async.mixin.world;

import com.axalotl.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.chunk.ChunkStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin extends ChunkManager {

    @Redirect(method = {"getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", "getWorldChunk"}, at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerChunkManager;serverThread:Ljava/lang/Thread;", opcode = Opcodes.GETFIELD))
    private Thread overwriteServerThread(ServerChunkManager mgr) {
        return Thread.currentThread();
    }

    @WrapMethod(method = "putInCache")
    private synchronized void syncPutInCache(long pos, Chunk chunk, ChunkStatus status, Operation<Void> original) {
        original.call(pos, chunk, status);
    }

    @Redirect(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;visit(Ljava/lang/String;)V"))
    private void overwriteProfilerVisit(Profiler instance, String s) {
        instance.visit("getChunkCacheMiss");
    }

    @Inject(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;shuffle(Ljava/util/List;Lnet/minecraft/util/math/random/Random;)V"))
    private void preChunkTick(CallbackInfo ci) {
        ParallelProcessor.preChunkTick();
    }
}