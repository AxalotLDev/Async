package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.utils.AsyncLevelRandom;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.server.MinecraftServer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

@Slf4j
@Mixin(value = ServerChunkCache.class, priority = 1500)
public abstract class ServerChunkCacheMixin extends ChunkSource {

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    private Thread mainThread;

    @Shadow
    @Final
    public ServerChunkCache.MainThreadExecutor mainThreadProcessor;

    @Shadow
    public abstract @Nullable ChunkHolder getVisibleChunkIfPresent(long key);

    @Shadow
    protected abstract CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate);

    @Shadow
    private final Set<ChunkHolder> chunkHoldersToBroadcast = ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    protected abstract void getFullChunk(long chunkKey, Consumer<LevelChunk> output);

    @Shadow
    private final List<LevelChunk> spawningChunks = Collections.synchronizedList(new ArrayList<>());

    @Shadow
    private boolean spawnEnemies;

    @Shadow
    public abstract void tickSpawningChunk(LevelChunk chunk, long timeDiff, List<MobCategory> spawningCategories, NaturalSpawner.SpawnState spawnCookie);

    @Unique
    private List<Runnable> batch;

    @Unique
    private ParallelProcessor.ParallelBatch spawnBatchInFlight;

    @Unique
    private static final ThreadLocal<LastChunkSlot> LAST_CHUNK = ThreadLocal.withInitial(LastChunkSlot::new);

    private static final class LastChunkSlot {
        Object owner;
        long pos;
        ChunkStatus status;
        ChunkAccess chunk;
        int tick = Integer.MIN_VALUE;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        this.batch = new ArrayList<>();
    }

    @Unique
    private static final Semaphore CHUNK_WAIT_PERMITS = new Semaphore(2, true);

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;", at = @At("HEAD"), cancellable = true)
    private void getChunk(int x, int z, ChunkStatus targetStatus, boolean loadOrGenerate, CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread) return;
        if (!ParallelProcessor.isServerExecutionThread()) return;

        final MinecraftServer server = ParallelProcessor.getServer();
        final int currentTick = server != null ? server.getTickCount() : Integer.MIN_VALUE;
        final long key = ChunkPos.pack(x, z);
        final LastChunkSlot slot = LAST_CHUNK.get();
        if (slot.owner == this && slot.pos == key && slot.status == targetStatus && slot.tick == currentTick) {
            cir.setReturnValue(slot.chunk);
            return;
        }

        ChunkAccess access = tryGetChunk(x, z, targetStatus);
        if (access != null) {
            slot.owner = this;
            slot.pos = key;
            slot.status = targetStatus;
            slot.chunk = access;
            slot.tick = currentTick;
            cir.setReturnValue(access);
            return;
        }

        if (!loadOrGenerate) return;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        long permitWaitNanos = deadline - System.nanoTime();
        boolean acquired;
        try {
            acquired = permitWaitNanos > 0 && CHUNK_WAIT_PERMITS.tryAcquire(permitWaitNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!acquired) {
            log.warn("Timed out after 5s waiting for a chunk-wait slot for chunk [{}, {}] at status {}; falling back to the vanilla blocking path", x, z, targetStatus);
            return;
        }

        try {
            CompletableFuture<ChunkResult<ChunkAccess>> future = CompletableFuture.supplyAsync(
                    () -> this.getChunkFutureMainThread(x, z, targetStatus, true),
                    this.mainThreadProcessor
            ).thenCompose(f -> f);

            while (!future.isDone()) {
                if (System.nanoTime() > deadline) {
                    future.cancel(false);
                    log.warn("Timed out after 5s waiting for chunk [{}, {}] at status {}; falling back to the vanilla blocking path", x, z, targetStatus);
                    return;
                }
                ChunkAccess cached = tryGetChunk(x, z, targetStatus);
                if (cached != null) {
                    future.cancel(false);
                    cir.setReturnValue(cached);
                    return;
                }
                LockSupport.parkNanos(10_000);
            }

            ChunkAccess chunk = future.join().orElse(null);
            if (chunk instanceof ImposterProtoChunk imp) chunk = imp.getWrapped();
            cir.setReturnValue(chunk);
        } finally {
            CHUNK_WAIT_PERMITS.release();
        }
    }

    @Unique
    private @Nullable ChunkAccess tryGetChunk(int x, int z, ChunkStatus leastStatus) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        if (holder == null) return null;
        ChunkAccess chunk = holder.getChunkIfPresent(leastStatus);
        if (chunk instanceof ImposterProtoChunk imp) return imp.getWrapped();
        return chunk;
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void shortcutGetChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread) return;
        if (!ParallelProcessor.isServerExecutionThread()) return;
        final ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.pack(x, z));
        if (holder != null) {
            ChunkAccess chunk = holder.getChunkIfPresent(ChunkStatus.FULL);
            cir.setReturnValue(chunk instanceof LevelChunk wc ? wc : null);
        } else {
            cir.setReturnValue(null);
        }
    }

    @WrapOperation(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkMap;forEachBlockTickingChunk(Ljava/util/function/Consumer;)V"))
    private void parallelRandomTicks(ChunkMap map, Consumer<LevelChunk> tickingChunkConsumer, Operation<Void> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncRandomTicks) {
            original.call(map, tickingChunkConsumer);
            return;
        }

        List<LevelChunk> chunks = new ArrayList<>();
        original.call(map, (Consumer<LevelChunk>) chunks::add);
        ParallelProcessor.forEachParallel(chunks, chunk -> {
            AsyncLevelRandom.beginChunk(this.level, chunk.getPos().pack());
            tickingChunkConsumer.accept(chunk);
        });
    }

    @WrapMethod(method = "tickSpawningChunk")
    private void redirectTickSpawningChunk(LevelChunk chunk, long timeDiff, List<MobCategory> spawningCategories, NaturalSpawner.SpawnState spawnCookie, Operation<Void> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            original.call(chunk, timeDiff, spawningCategories, spawnCookie);
            return;
        }

        if (!spawningCategories.isEmpty()) {
            spawnCookie.localMobCapCalculator.playersNearChunk.put(chunk.getPos().pack(), playersCloseForSpawning(chunk.getPos()));
        }

        batch.add(() -> {
            AsyncLevelRandom.beginChunk(this.level, chunk.getPos().pack());
            original.call(chunk, timeDiff, spawningCategories, spawnCookie);
        });
    }

    @Unique
    private List<ServerPlayer> playersCloseForSpawning(ChunkPos pos) {
        List<ServerPlayer> players = this.chunkMap.getPlayersCloseForSpawning(pos);
        if (!players.isEmpty()) {
            return players;
        }
        List<ServerPlayer> computed = null;
        double centerX = pos.getMinBlockX() + 8;
        double centerZ = pos.getMinBlockZ() + 8;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            if (dx * dx + dz * dz < 16384.0) {
                if (computed == null) computed = new ArrayList<>();
                computed.add(player);
            }
        }
        return computed != null ? computed : players;
    }

    @Inject(method = "tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V", at = @At("TAIL"))
    private void dispatchSpawnBatch(CallbackInfo ci) {
        ParallelProcessor.finishBatch(spawnBatchInFlight);
        spawnBatchInFlight = null;

        if (batch.isEmpty()) {
            return;
        }

        List<Runnable> ready = batch;
        batch = new ArrayList<>();
        spawnBatchInFlight = ParallelProcessor.submitBatch(() -> ready.forEach(Runnable::run));
    }
}