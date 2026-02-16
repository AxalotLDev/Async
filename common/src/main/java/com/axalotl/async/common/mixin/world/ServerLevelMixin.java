package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
import com.axalotl.async.common.parallelised.ConcurrentCollections;
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
import java.util.concurrent.CompletableFuture;
import io.netty.util.internal.shaded.org.jctools.queues.MpscUnboundedArrayQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("all")
@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelMixin extends Level implements WorldGenLevel {

    @Shadow
    @Final
    public EntityTickList entityTickList;

    @Unique
    MpscUnboundedArrayQueue<BlockEventData> async$syncedBlockEventQueue;

    @Shadow
    @Final
    @Mutable
    Set<Mob> navigatingMobs;

    @Shadow
    @Final
    private ServerChunkCache chunkSource;

    protected ServerLevelMixin(WritableLevelData levelData, ResourceKey<Level> dimension, RegistryAccess registryAccess, Holder<DimensionType> dimensionTypeRegistration, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionTypeRegistration, isClientSide, isDebug, biomeZoomSeed, maxChainedNeighborUpdates);
    }

    @Shadow
    public abstract @NotNull ServerLevel getLevel();

    @Shadow
    @Mutable
    @Final
    private List<ServerPlayer> players;

    @Unique
    private static final Object lock = new Object();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        navigatingMobs = ConcurrentCollections.newHashSet();
        async$syncedBlockEventQueue = new MpscUnboundedArrayQueue<>(256);
        players = new CopyOnWriteArrayList<>();
    }

    // --- Entity ticking (all three run in parallel, then wait with task pumping) ---

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

        // Fire all three concurrently
        ParallelProcessor.submitSpawnCycle();

        CompletableFuture<Void> despawnFuture = ParallelProcessor.callDespawnBatch(toDespawnCheck);

        profilerfiller.push("tick");
        CompletableFuture<Void> entityFuture = ParallelProcessor.callEntityTickBatch(this.getLevel(), toTick);

        // Wait for despawn + entity tick while pumping chunk tasks
        ParallelProcessor.waitWithPumping(despawnFuture, entityFuture);
        profilerfiller.pop();
    }

    // --- addFreshEntity lock (from furry, needed for async spawn safety) ---

    @WrapMethod(method = "addFreshEntity")
    private boolean wrapAddFreshEntity(Entity entity, Operation<Boolean> original) {
        if (AsyncConfig.disabled || !AsyncConfig.enableAsyncSpawn) {
            return original.call(entity);
        }

        synchronized (ParallelProcessor.getEntityAddLock()) {
            return original.call(entity);
        }
    }

    // --- Block event concurrent queue (both branches identical) ---

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

    // --- Navigation / block update safety (both branches identical) ---

    @Redirect(method = "sendBlockUpdated", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;isUpdatingNavigations:Z", opcode = Opcodes.PUTFIELD))
    private void skipSendBlockUpdatedCheck(ServerLevel instance, boolean value) {
    }

    // --- Explosion sync (both branches identical) ---

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
            original.call(
                    source, damageSource, damageCalculator,
                    x, y, z, radius, fire,
                    explosionInteraction,
                    smallExplosionParticles,
                    largeExplosionParticles,
                    particleInfo,
                    explosionSound
            );
        }
    }
}