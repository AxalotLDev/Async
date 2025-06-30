package com.axalotl.async.mixin.server;

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
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @WrapMethod(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/Chunk;")
    private Chunk shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, Operation<Chunk> original) {
        if (Thread.currentThread() != this.serverThread) {
            final ChunkHolder holder = this.getChunkHolder(ChunkPos.toLong(x, z));
            if (holder != null) {
                final CompletableFuture<OptionalChunk<Chunk>> future = holder.load(leastStatus, this.chunkLoadingManager);
                if (future.isDone()) {
                    OptionalChunk<Chunk> optionalChunk = future.join();
                    Chunk chunk = optionalChunk.orElse(null);
                    if (chunk instanceof WrapperProtoChunk readOnlyChunk) {
                        chunk = readOnlyChunk.getWrappedChunk();
                    }
                    if (chunk != null) {
                        return chunk;
                    }
                }
            }
        }
        return original.call(x, z, leastStatus, create);
    }

    @WrapMethod(method = "getWorldChunk")
    private WorldChunk shortcutGetWorldChunk(int chunkX, int chunkZ, Operation<WorldChunk> original) {
        if (Thread.currentThread() != this.serverThread) {
            final ChunkHolder holder = this.getChunkHolder(ChunkPos.toLong(chunkX, chunkZ));
            if (holder != null) {
                final CompletableFuture<OptionalChunk<Chunk>> future = holder.load(ChunkStatus.FULL, this.chunkLoadingManager);
                if (future.isDone()) {
                    OptionalChunk<Chunk> optionalChunk = future.join();
                    Chunk chunk = optionalChunk.orElse(null);
                    if (chunk instanceof WorldChunk worldChunk) {
                        return worldChunk;
                    }
                }
            }
        }
        return original.call(chunkX, chunkZ);
    }

    @Redirect(method = "tickSpawningChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/SpawnHelper;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Info;Ljava/util/List;)V"))
    private void tickChunks(ServerWorld world, WorldChunk worldChunk, SpawnHelper.Info info, List<SpawnGroup> spawnableGroups) {
        ParallelProcessor.asyncSpawn(world, worldChunk, info, spawnableGroups);
    }
}