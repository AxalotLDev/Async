package com.axalotl.async.mixin.world;

import com.axalotl.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;


@Mixin(value = ServerChunkManager.class, priority = 1500)
public abstract class ServerChunkManagerMixin extends ChunkManager {
    @Shadow
    @Final
    Thread serverThread;

    @Shadow
    public abstract @Nullable ChunkHolder getChunkHolder(long pos);

    @Shadow
    @Final
    public ServerChunkLoadingManager chunkLoadingManager;

    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;", at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<Chunk> cir) {
        if (Thread.currentThread() != this.serverThread) {
            final ChunkHolder holder = this.getChunkHolder(ChunkPos.toLong(x, z));
            if (holder != null) {
                final CompletableFuture<OptionalChunk<Chunk>> future = holder.load(leastStatus, this.chunkLoadingManager);
                Chunk chunk = future.getNow(ChunkHolder.UNLOADED).orElse(null);
                if (chunk instanceof WrapperProtoChunk readOnlyChunk) chunk = readOnlyChunk.getWrappedChunk();
                if (chunk != null) {
                    cir.setReturnValue(chunk);
                    return;
                }
            }
        }
    }

    @Inject(method = "getWorldChunk", at = @At("HEAD"), cancellable = true)
    private void shortcutGetWorldChunk(int chunkX, int chunkZ, CallbackInfoReturnable<WorldChunk> cir) {
        if (Thread.currentThread() != this.serverThread) {
            final ChunkHolder holder = this.getChunkHolder(ChunkPos.toLong(chunkX, chunkZ));
            if (holder != null) {
                final CompletableFuture<OptionalChunk<Chunk>> future = holder.load(ChunkStatus.FULL, this.chunkLoadingManager);
                Chunk chunk = future.getNow(ChunkHolder.UNLOADED).orElse(null);
                if (chunk instanceof WorldChunk worldChunk) {
                    cir.setReturnValue(worldChunk);
                    return;
                }
            }
        }
    }

    @Redirect(method = "tickChunks(Lnet/minecraft/util/profiler/Profiler;JLjava/util/List;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SpawnHelper;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;Ljava/util/List;)V"))
    private void tickChunks(ServerWorld world, WorldChunk worldChunk, SpawnHelper.Info info, List<SpawnGroup> spawnableGroups) {
        ParallelProcessor.asyncSpawn(world, worldChunk, info, spawnableGroups);
    }

    @WrapMethod(method = "putInCache")
    private synchronized void syncPutInCache(long pos, Chunk chunk, ChunkStatus status, Operation<Void> original) {
        original.call(pos, chunk, status);
    }
}