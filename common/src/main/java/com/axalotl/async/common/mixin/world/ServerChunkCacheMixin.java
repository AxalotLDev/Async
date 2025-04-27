package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() != this.mainThread) {
            final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
            if (holder != null) {
                final CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(leastStatus, this.chunkMap);
                ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).orElse(null);
                if (chunk instanceof ImposterProtoChunk readOnlyChunk) chunk = readOnlyChunk.getWrapped();
                if (chunk != null) {
                    cir.setReturnValue(chunk);
                    return;
                }
            }
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() != this.mainThread) {
            final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
            if (holder != null) {
                final CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(ChunkStatus.FULL, this.chunkMap);
                ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).orElse(null);
                if (chunk instanceof LevelChunk worldChunk) {
                    cir.setReturnValue(worldChunk);
                    return;
                }
            }
        }
    }

    @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;ZZZ)V"))
    private void tickChunks(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, boolean spawnFriendlies, boolean spawnMonsters, boolean rareSpawn) {
        ParallelProcessor.asyncSpawn(level, chunk, spawnState, spawnFriendlies, spawnMonsters, rareSpawn);
    }

    @WrapMethod(method = "storeInCache")
    private synchronized void syncPutInCache(long chunkPos, ChunkAccess chunk, ChunkStatus status, Operation<Void> original) {
        original.call(chunkPos, chunk, status);
    }
}