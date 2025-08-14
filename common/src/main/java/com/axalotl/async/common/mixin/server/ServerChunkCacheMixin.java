package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.AsyncCommon;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.Util;
import net.minecraft.server.level.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NaturalSpawner.SpawnState;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    protected abstract void getFullChunk(long chunkPos, Consumer<LevelChunk> fullChunkGetter);

    @Shadow
    @Final
    public ServerLevel level;
    @Shadow
    private SpawnState lastSpawnState;
    @Shadow
    private boolean spawnEnemies;
    @Shadow
    private boolean spawnFriendlies;
    @Shadow
    @Final
    private List<LevelChunk> spawningChunks;

    @Shadow
    protected abstract void tickSpawningChunk(LevelChunk chunk, long timeInhabited, List<MobCategory> spawnCategories, SpawnState spawnState);

    @Unique
    private NaturalSpawner.SpawnState async$lastCachedSpawnState = null;
    @Unique
    private CompletableFuture<NaturalSpawner.SpawnState> async$futureSpawnState = null;

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunk(int x, int z, ChunkStatus leastStatus, boolean create, CallbackInfoReturnable<ChunkAccess> cir) {
        if (AsyncCommon.LITHIUM) return;
        if (Thread.currentThread() != this.mainThread) {
            final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
            if (holder != null) {
                final CompletableFuture<ChunkResult<ChunkAccess>> future = holder.scheduleChunkGenerationTask(leastStatus, this.chunkMap);
                if (future.isDone()) {
                    ChunkAccess chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).orElse(null);
                    if (chunk instanceof ImposterProtoChunk readOnlyChunk) chunk = readOnlyChunk.getWrapped();
                    if (chunk != null) {
                        cir.setReturnValue(chunk);
                        return;
                    }
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

    @WrapMethod(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V")
    private void tickChunks(ProfilerFiller profiler, long timeInhabited, Operation<Void> original) {
        if (!AsyncConfig.enableAsyncSpawn) {
            original.call(profiler, timeInhabited);
            return;
        }

        int spawnChunkCount = this.distanceManager.getNaturalSpawnChunkCount();

        if (async$futureSpawnState == null || async$futureSpawnState.isDone()) {
            async$futureSpawnState = CompletableFuture.supplyAsync(() -> {
                        profiler.popPush("naturalSpawnCount");
                        return NaturalSpawner.createState(
                                spawnChunkCount,
                                this.level.getAllEntities(),
                                this::getFullChunk,
                                new LocalMobCapCalculator(this.chunkMap)
                        );
                    }, ParallelProcessor.tickPool
            ).exceptionally(e -> {
                profiler.popPush("naturalSpawnCount");
                ParallelProcessor.LOGGER.error("Error in async create state, switching to synchronous", e);
                return NaturalSpawner.createState(
                        spawnChunkCount,
                        this.level.getAllEntities(),
                        this::getFullChunk,
                        new LocalMobCapCalculator(this.chunkMap)
                );
            });

            async$futureSpawnState.thenAccept(result -> async$lastCachedSpawnState = result);
        }

        NaturalSpawner.SpawnState spawnState = async$lastCachedSpawnState;
        if (spawnState == null) {
            profiler.popPush("naturalSpawnCount");
            spawnState = NaturalSpawner.createState(
                    spawnChunkCount,
                    this.level.getAllEntities(),
                    this::getFullChunk,
                    new LocalMobCapCalculator(this.chunkMap)
            );
            async$lastCachedSpawnState = spawnState;
        }

        this.lastSpawnState = spawnState;
        profiler.popPush("spawnAndTick");
        boolean flag = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
        int j = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        List<MobCategory> categories = flag
                ? NaturalSpawner.getFilteredSpawningCategories(spawnState, this.spawnFriendlies, this.spawnEnemies, this.level.getLevelData().getGameTime() % 400L == 0L)
                : List.of();

        List<LevelChunk> spawningChunks = this.spawningChunks;
        try {
            profiler.push("filteringSpawningChunks");
            this.chunkMap.collectSpawningChunks(spawningChunks);
            profiler.popPush("shuffleSpawningChunks");
            Util.shuffle(spawningChunks, this.level.random);
            profiler.popPush("tickSpawningChunks");

            for (LevelChunk chunk : spawningChunks) {
                SpawnState finalSpawnState = spawnState;
                CompletableFuture.runAsync(() -> this.tickSpawningChunk(chunk, timeInhabited, categories, finalSpawnState), ParallelProcessor.tickPool);
            }
        } finally {
            spawningChunks.clear();
        }


        profiler.popPush("tickTickingChunks");
        this.chunkMap.forEachBlockTickingChunk(p_401730_ -> this.level.tickChunk(p_401730_, j));
        profiler.pop();
        profiler.popPush("customSpawners");
        if (flag) {
            CompletableFuture.runAsync(() -> this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies), ParallelProcessor.tickPool);
        }
    }
}