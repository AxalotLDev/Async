package com.axalotl.async.mixin.world;

import com.axalotl.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;


@Mixin(value = ServerChunkCache.class, priority = 99999)
public abstract class ServerChunkManagerMixin extends ChunkSource {
    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    public ServerLevel level;

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() != this.mainThread) {
            final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
            if (holder != null) {
                final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getOrScheduleFuture(leastStatus, this.chunkMap);
                ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).left().orElse(null);
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
                final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future = holder.getOrScheduleFuture(ChunkStatus.FULL, this.chunkMap);
                ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).left().orElse(null);
                if (chunk instanceof LevelChunk worldChunk) {
                    cir.setReturnValue(worldChunk);
                    return;
                }
            }
        }
    }

    // 1.20.1 line 357 NaturalSpawner.spawnForChunk(this.level, levelchunk1, naturalspawner$spawnstate, this.spawnFriendlies, this.spawnEnemies, flag1);
//        @Redirect(method = "tickChunks(Lnet/minecraft/util/profiler/Profiler;JLjava/util/List;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SpawnHelper;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;Ljava/util/List;)V"))
//    private void tickChunks(ServerWorld world, WorldChunk worldChunk, SpawnHelper.Info info, List<SpawnGroup> spawnableGroups) {
//        ParallelProcessor.asyncSpawn(world, worldChunk, info, spawnableGroups);
//    }

    @WrapMethod(method = "storeInCache")
    private synchronized void syncPutInCache(long chunkPos, ChunkAccess chunk, ChunkStatus status, Operation<Void> original) {
        original.call(chunkPos, chunk, status);
    }
}