package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import com.axalotl.async.common.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Mixin(value = ChunkMap.class, priority = 1500)
public abstract class ChunkMapMixin extends SimpleRegionStorage implements ChunkHolder.PlayerProvider {

    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;

    @Shadow
    @Final
    @Mutable
    private List<ChunkGenerationTask> pendingGenerationTasks;

    @Shadow
    @Final
    @Mutable
    private LongSet chunksToEagerlySave;

    @Shadow
    @Final
    private ChunkMap.DistanceManager distanceManager;

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Shadow
    @Final
    ServerLevel level;

    public ChunkMapMixin(RegionStorageInfo p_326109_, Path p_321582_, DataFixer p_321815_, boolean p_321788_, DataFixTypes p_321522_) {
        super(p_326109_, p_321582_, p_321815_, p_321788_, p_321522_);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci) {
        entityMap = new Int2ObjectConcurrentHashMap<>();
        pendingGenerationTasks = new CopyOnWriteArrayList<>();
        chunksToEagerlySave = new ConcurrentLongLinkedOpenHashSet();
    }

    @WrapMethod(method = "addEntity")
    private synchronized void addEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @WrapMethod(method = "removeEntity")
    private synchronized void removeEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @WrapMethod(method = "releaseGeneration")
    private synchronized void releaseGeneration(GenerationChunkHolder chunk, Operation<Void> original) {
        original.call(chunk);
    }

    @Inject(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;pauseInIde(Ljava/lang/Throwable;)Ljava/lang/Throwable;"), cancellable = true)
    private void skipThrowLoadEntity(Entity entity, CallbackInfo ci) {
        ci.cancel();
    }

    @WrapMethod(method = "collectSpawningChunks")
    private void async$optimizedCollectSpawningChunks(List<LevelChunk> result, Operation<Void> original) {
        List<ServerPlayer> players = this.level.players();
        double[] playerX = new double[players.size()];
        double[] playerZ = new double[players.size()];
        int playerCount = 0;

        for (int i = 0, size = players.size(); i < size; i++) {
            ServerPlayer player = players.get(i);
            if (!player.isSpectator()) {
                Vec3 pos = player.position();
                playerX[playerCount] = pos.x;
                playerZ[playerCount] = pos.z;
                playerCount++;
            }
        }

        if (playerCount == 0) return;

        LongIterator it = this.distanceManager.getSpawnCandidateChunks();

        while (it.hasNext()) {
            ChunkHolder holder = this.visibleChunkMap.get(it.nextLong());
            if (holder == null) continue;

            ChunkAccess chunk = holder.getTickingChunk();
            if (!(chunk instanceof LevelChunk lc)) continue;

            ChunkPos pos = holder.getPos();
            double cx = SectionPos.sectionToBlockCoord(pos.x, 8);
            double cz = SectionPos.sectionToBlockCoord(pos.z, 8);

            for (int i = 0; i < playerCount; i++) {
                double dx = cx - playerX[i];
                double dz = cz - playerZ[i];
                if (dx * dx + dz * dz < 16384.0) {
                    result.add(lc);
                    break;
                }
            }
        }
    }

    @WrapMethod(method = "forEachBlockTickingChunk")
    private void forEachBlockTickingChunk(Consumer<LevelChunk> action, Operation<Void> original) {
        if (!AsyncConfig.disabled && AsyncConfig.enableAsyncRandomTicks) {
            // Snapshot chunk positions for thread-safe iteration
            List<Long> keys = new ArrayList<>();
            distanceManager.forEachEntityTickingChunk(keys::add);

            for (long chunkPos : keys) {
                ChunkHolder holder = visibleChunkMap.get(chunkPos);
                if (holder != null) {
                    LevelChunk chunk = holder.getTickingChunk();
                    if (chunk != null) {
                        CompletableFuture<Void> future = CompletableFuture.runAsync(
                                () -> {
                                    if (chunk.getLevel() != null) {
                                        action.accept(chunk);
                                    }
                                },
                                ParallelProcessor.tickPool
                        ).exceptionally(e -> {
                            ParallelProcessor.LOGGER.error("Error in async random tick", e);
                            return null;
                        });
                        ParallelProcessor.addTask(future);
                    }
                }
            }
        } else {
            original.call(action);
        }
    }
}