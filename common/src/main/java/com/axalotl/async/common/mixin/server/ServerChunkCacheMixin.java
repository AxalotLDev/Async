package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.MobCategory;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
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
    @Final
    public ServerChunkCache.MainThreadExecutor mainThreadProcessor;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus leastStatus, boolean create);


    //TODO: Implement our own getChunk without modifying the vanilla method
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void async$getChunk(int x, int z, ChunkStatus leastStatus, boolean create,
                                CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkAccess fast = async$tryGetChunkFast(x, z, leastStatus);
        if (fast != null) {
            cir.setReturnValue(fast);
            return;
        }

        CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture.supplyAsync(
                        () -> this.getChunkFutureMainThread(x, z, leastStatus, create),
                        this.mainThreadProcessor
                )
                .thenCompose(f -> f);

        ChunkAccess chunk = future.join().orElse(null);
        if (chunk instanceof ImposterProtoChunk imposter) {
            chunk = imposter.getWrapped();
        }

        cir.setReturnValue(chunk);
    }

    @Unique
    private @Nullable ChunkAccess async$tryGetChunkFast(int x, int z, ChunkStatus leastStatus) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        if (holder == null) return null;

        ChunkAccess chunk = holder.getChunkIfPresent(leastStatus);
        if (chunk != null) {
            if (chunk instanceof ImposterProtoChunk imposter) {
                return imposter.getWrapped();
            }
            return chunk;
        }

        CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(leastStatus, this.chunkMap);
        if (future.isDone()) {
            ChunkAccess result = future.join().orElse(null);
            if (result instanceof ImposterProtoChunk imposter) {
                return imposter.getWrapped();
            }
            return result;
        }

        return null;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void async$getChunkNow(int chunkX, int chunkZ, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;

        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(chunkX, chunkZ));
        if (holder != null) {
            ChunkAccess chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
            if (chunk instanceof LevelChunk levelChunk) {
                cir.setReturnValue(levelChunk);
                return;
            }
        }
        cir.setReturnValue(null);
    }

    @Redirect(method = "tickSpawningChunk(Lnet/minecraft/world/level/chunk/LevelChunk;JLjava/util/List;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V"))
    private void tickSpawningChunk(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState, List<MobCategory> categories) {
        ParallelProcessor.asyncSpawnForChunk(level, chunk, spawnState, categories);
    }
}