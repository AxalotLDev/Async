package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.lithium.LithiumServerLevel;
import com.axalotl.async.api.utils.ConcurrentCollections;
import com.axalotl.async.api.utils.ConcurrentList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("all")
@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelMixin extends Level implements WorldGenLevel {

    @Shadow @Final public EntityTickList entityTickList;
    @Shadow @Final private ServerChunkCache chunkSource;
    @Shadow @Final @Mutable Set<Mob> navigatingMobs;
    @Shadow @Mutable @Final private List<ServerPlayer> players;

    @Shadow public abstract @NotNull ServerLevel getLevel();

    @Unique
    private final Object async$explosionLock = new Object();

    @Unique
    ConcurrentLinkedQueue<BlockEventData> async$syncedBlockEventQueue;

    protected ServerLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        navigatingMobs = ConcurrentCollections.newHashSet();
        async$syncedBlockEventQueue = new ConcurrentLinkedQueue<>();
        players = new ConcurrentList<>();
    }

    @WrapMethod(method = "tickNonPassenger")
    private void async$guardTickNonPassenger(Entity entity, Operation<Void> original) {
        ParallelProcessor.TickGuard guard = (ParallelProcessor.TickGuard) entity;
        if (!guard.async$tryBeginTick()) {
            return;
        }
        try {
            original.call(entity);
        } finally {
            guard.async$endTick();
        }
    }

    @WrapMethod(method = "tickPassenger")
    private void async$guardTickPassenger(Entity vehicle, Entity entity, Operation<Void> original) {
        ParallelProcessor.TickGuard guard = (ParallelProcessor.TickGuard) entity;
        if (!guard.async$tryBeginTick()) {
            return;
        }
        try {
            original.call(vehicle, entity);
        } finally {
            guard.async$endTick();
        }
    }

    @Unique
    private static final Logger ASYNC_LOGGER = LoggerFactory.getLogger("Async-EntityTick");

    @Unique
    private int async$lastBatchSize = 256;

    @Unique
    private int async$lastLiveSize = 256;

    @Unique
    private ArrayList<Entity> async$syncBuf;

    @Unique
    private ArrayList<Entity> async$liveBuf;

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void overwriteEntityTicking(EntityTickList entityTickList, Consumer<Entity> consumer) {
        ProfilerFiller profilerfiller = Profiler.get();
        ServerLevel self = this.getLevel();
        long batchStart = System.nanoTime();

        net.minecraft.server.level.DistanceManager distanceManager = this.chunkSource.chunkMap.getDistanceManager();
        net.minecraft.world.TickRateManager tickRateManager = this.tickRateManager();

        ArrayList<Entity> live = async$liveBuf;
        if (live == null) {
            live = new ArrayList<>(async$lastLiveSize);
            async$liveBuf = live;
        } else {
            live.clear();
        }
        final ArrayList<Entity> liveList = live;
        this.entityTickList.forEach(e -> {
            if (e == null || e.isRemoved()) return;
            if (tickRateManager.isEntityFrozen(e)) return;
            liveList.add(e);
        });
        final int liveCount = live.size();
        async$lastLiveSize = Math.max(64, liveCount + (liveCount >> 2));
        if (AsyncConfig.enableAsyncDespawn && !AsyncConfig.disabled && liveCount > 0) {
            ExecutorService dexec = ParallelProcessor.executor;
            int dpool = Math.max(1, ParallelProcessor.getPoolSize());
            int dchunkSize = Math.max(8, Math.min(64, liveCount / (dpool * 4)));
            int dchunks = (liveCount + dchunkSize - 1) / dchunkSize;
            CompletableFuture<?>[] dfutures = new CompletableFuture<?>[dchunks];
            for (int c = 0, i = 0; c < dchunks; c++, i += dchunkSize) {
                final int from = i;
                final int to = Math.min(i + dchunkSize, liveCount);
                dfutures[c] = CompletableFuture.runAsync(() -> {
                    for (int j = from; j < to; j++) {
                        Entity ent = liveList.get(j);
                        if (ent.isRemoved()) continue;
                        try {
                            ent.checkDespawn();
                        } catch (Exception ex) {
                            ASYNC_LOGGER.error("Async checkDespawn failed for {} ({})",
                                    ent.getType(), ent.getUUID(), ex);
                        }
                    }
                }, dexec);
            }
            ParallelProcessor.pumpUntilDone(CompletableFuture.allOf(dfutures));
        } else {
            profilerfiller.push("checkDespawn");
            for (int i = 0; i < liveCount; i++) {
                Entity ent = live.get(i);
                if (!ent.isRemoved()) ent.checkDespawn();
            }
            profilerfiller.pop();
        }

        ArrayList<Entity> pendingAsync = new ArrayList<>(async$lastBatchSize);
        ArrayList<Entity> syncList = async$syncBuf;
        if (syncList == null) {
            syncList = new ArrayList<>(64);
            async$syncBuf = syncList;
        } else {
            syncList.clear();
        }

        final ArrayList<Entity> syncDeferred = syncList;
        for (int i = 0; i < liveCount; i++) {
            Entity entity = live.get(i);
            if (entity.isRemoved()) continue;
            if (tickRateManager.isEntityFrozen(entity)) continue;

            if (!(entity instanceof ServerPlayer) && !distanceManager.inEntityTickingRange(entity.chunkPosition().pack())) continue;
            if (!(entity instanceof ServerPlayer)) {
                long entityChunkPos = entity.chunkPosition().pack();
                net.minecraft.server.level.ChunkHolder holder = this.chunkSource.getVisibleChunkIfPresent(entityChunkPos);
                if (holder == null || holder.getTickingChunk() == null) continue;
            }

            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) continue;
                entity.stopRiding();
            }

            if (AsyncConfig.disabled || ParallelProcessor.shouldTickSynchronously(entity)) {
                syncDeferred.add(entity);
            } else {
                pendingAsync.add(entity);
            }
        }

        int n = pendingAsync.size();
        async$lastBatchSize = Math.max(64, n + (n >> 2));
        CompletableFuture<Void> all = null;
        if (n > 0) {
            ExecutorService exec = ParallelProcessor.executor;
            int pool = Math.max(1, ParallelProcessor.getPoolSize());
            int chunkSize = Math.max(4, Math.min(32, n / (pool * 8)));
            int chunks = (n + chunkSize - 1) / chunkSize;

            final ArrayList<Entity> pending = pendingAsync;
            CompletableFuture<?>[] futures = new CompletableFuture<?>[chunks];
            for (int c = 0, i = 0; c < chunks; c++, i += chunkSize) {
                final int from = i;
                final int to = Math.min(i + chunkSize, n);
                futures[c] = CompletableFuture.runAsync(() -> {
                    for (int j = from; j < to; j++) {
                        ParallelProcessor.tickEntity(self, pending.get(j), true);
                    }
                }, exec);
            }
            all = CompletableFuture.allOf(futures);
        }
        int sz = syncDeferred.size();
        if (sz > 0) {
            profilerfiller.push("tickSync");
            for (int i = 0; i < sz; i++) {
                ParallelProcessor.tickEntity(self, syncDeferred.get(i), false);
            }
            profilerfiller.pop();
        }
        if (all != null) ParallelProcessor.pumpUntilDone(all);
        ParallelProcessor.onEntityTickBatchEnd(batchStart);
    }

    @Redirect(method = "blockEvent", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;add(Ljava/lang/Object;)Z", remap = false))
    private boolean overwriteQueueAdd(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet, Object object) {
        return async$syncedBlockEventQueue.add((BlockEventData) object);
    }

    @Redirect(method = "clearBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeIf(Ljava/util/function/Predicate;)Z", remap = false))
    private boolean overwriteQueueRemoveIf(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet, Predicate<BlockEventData> filter) {
        return async$syncedBlockEventQueue.removeIf(filter);
    }

    @Redirect(method = "runBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;isEmpty()Z", remap = false))
    private boolean overwriteEmptyCheck(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet) {
        return async$syncedBlockEventQueue.isEmpty();
    }

    @Redirect(method = "runBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeFirst()Ljava/lang/Object;", remap = false))
    private Object overwriteQueueRemoveFirst(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet) {
        return async$syncedBlockEventQueue.poll();
    }

    @Redirect(method = "runBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;addAll(Ljava/util/Collection;)Z", remap = false))
    private boolean overwriteQueueAddAll(ObjectLinkedOpenHashSet<BlockEventData> instance, Collection<? extends BlockEventData> c) {
        return async$syncedBlockEventQueue.addAll(c);
    }

    @Redirect(method = "sendBlockUpdated", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;isUpdatingNavigations:Z", opcode = Opcodes.PUTFIELD))
    private void skipSendBlockUpdatedCheck(ServerLevel instance, boolean value) {
    }

    @WrapMethod(method = "explode")
    private void createExplosion(
            @Nullable Entity source,
            @Nullable DamageSource damageSource,
            @Nullable ExplosionDamageCalculator damageCalculator,
            double x, double y, double z,
            float radius,
            boolean fire,
            Level.ExplosionInteraction explosionInteraction,
            ParticleOptions smallExplosionParticles,
            ParticleOptions largeExplosionParticles,
            WeightedList<ExplosionParticleInfo> particleInfo,
            Holder<SoundEvent> explosionSound,
            Operation<Void> original
    ) {
        synchronized (this.async$explosionLock) {
            ((LithiumServerLevel) (Object) this).async$setSuppress(true);
            original.call(
                    source, damageSource, damageCalculator,
                    x, y, z, radius, fire,
                    explosionInteraction,
                    smallExplosionParticles,
                    largeExplosionParticles,
                    particleInfo,
                    explosionSound
            );
            ((LithiumServerLevel) (Object) this).async$setSuppress(false);
            ((LithiumServerLevel) (Object) this).async$recomputeAllNavigations();
        }
    }
}