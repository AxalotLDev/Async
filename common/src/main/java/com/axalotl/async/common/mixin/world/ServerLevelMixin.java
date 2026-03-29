package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.mixin.lithium.LithiumServerLevel;
import com.axalotl.async.common.parallelised.utils.ItemFluidPrecompute;
import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.axalotl.async.common.parallelised.ConcurrentList;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.WritableLevelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
    private static final Object lock = new Object();

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


    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void overwriteEntityTicking(EntityTickList entityTickList, Consumer<Entity> consumer) {
        ProfilerFiller profilerfiller = Profiler.get();

        List<Entity> toTick = new ArrayList<>();
        List<Entity> toDespawnCheck = new ArrayList<>();

        this.entityTickList.forEach(entity -> {
            if (entity == null || entity.isRemoved()) return;
            if (this.tickRateManager().isEntityFrozen(entity)) return;

            if (!AsyncConfig.disabled && AsyncConfig.enableAsyncSpawn) {
                toDespawnCheck.add(entity);
            } else {
                profilerfiller.push("checkDespawn");
                entity.checkDespawn();
                profilerfiller.pop();
            }

            if (!this.chunkSource.chunkMap.getDistanceManager()
                    .inEntityTickingRange(entity.chunkPosition().toLong())) return;

            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) return;
                entity.stopRiding();
            }

            toTick.add(entity);
        });

        if (!toDespawnCheck.isEmpty()) {
            int poolSize = ParallelProcessor.getPoolSize();
            int chunkSize = Math.max(1, (toDespawnCheck.size() + poolSize - 1) / poolSize);
            List<Future<Void>> despawnFutures = new ArrayList<>();
            for (int i = 0; i < toDespawnCheck.size(); i += chunkSize) {
                List<Entity> chunk = toDespawnCheck.subList(i, Math.min(i + chunkSize, toDespawnCheck.size()));
                despawnFutures.add(ParallelProcessor.tickPool.submit(() -> {
                    for (Entity e : chunk) e.checkDespawn();
                    return (Void) null;
                }));
            }
            boolean allDone;
            do {
                allDone = true;
                for (Future<Void> f : despawnFutures) {
                    if (!f.isDone()) { allDone = false; break; }
                }
                if (!allDone) {
                    boolean pumped = false;
                    for (ServerLevel lvl : ParallelProcessor.getServer().getAllLevels()) {
                        pumped |= lvl.getChunkSource().pollTask();
                    }
                    if (!pumped) Thread.onSpinWait();
                }
            } while (!allDone);
        }

        async$precomputeItemFluidStates(toTick);

        profilerfiller.push("tick");
        ParallelProcessor.callEntityTickBatch(this.getLevel(), toTick);
        profilerfiller.pop();
    }

    @Unique
    private void async$precomputeItemFluidStates(List<Entity> toTick) {
        if (AsyncConfig.disabled || toTick.isEmpty()) return;

        LongOpenHashSet posSet = new LongOpenHashSet();
        for (Entity e : toTick) {
            if (e instanceof ItemEntity) {
                posSet.add(e.blockPosition().asLong());
            }
        }
        if (posSet.isEmpty()) return;

        long[] positions = posSet.toLongArray();
        FluidState[] results = new FluidState[positions.length];
        ServerLevel self = this.getLevel();

        int poolSize = ParallelProcessor.getPoolSize();
        int chunkSize = Math.max(1, (positions.length + poolSize - 1) / poolSize);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < positions.length; i += chunkSize) {
            final int start = i;
            final int end = Math.min(i + chunkSize, positions.length);
            futures.add(ParallelProcessor.tickPool.submit(() -> {
                for (int j = start; j < end; j++) {
                    results[j] = self.getFluidState(BlockPos.of(positions[j]));
                }
                return null;
            }));
        }

        boolean allDone;
        do {
            allDone = true;
            for (Future<?> f : futures) {
                if (!f.isDone()) { allDone = false; break; }
            }
            if (!allDone) {
                boolean pumped = false;
                for (ServerLevel lvl : ParallelProcessor.getServer().getAllLevels()) {
                    pumped |= lvl.getChunkSource().pollTask();
                }
                if (!pumped) Thread.onSpinWait();
            }
        } while (!allDone);

        Long2ObjectOpenHashMap<FluidState> fluidMap = new Long2ObjectOpenHashMap<>(positions.length);
        for (int i = 0; i < positions.length; i++) {
            fluidMap.put(positions[i], results[i]);
        }
        ItemFluidPrecompute.activate(fluidMap);
    }

    @WrapMethod(method = "addFreshEntity")
    private boolean wrapAddFreshEntity(Entity entity, Operation<Boolean> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return original.call(entity);
        }

        synchronized (ParallelProcessor.getEntityAddLock()) {
            return original.call(entity);
        }
    }

    @Inject(method = "canSpawnEntitiesInChunk", at = @At("HEAD"), cancellable = true)
    private void async$canSpawnEntitiesInChunk(ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet set = ParallelProcessor.spawnableChunkPositions;
        if (set != null && ParallelProcessor.isServerExecutionThread()) {
            cir.setReturnValue(set.contains(pos.toLong()));
        }
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
        synchronized (lock) {
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