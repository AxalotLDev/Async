package com.axalotl.async.common.mixin.world;

import com.axalotl.async.api.utils.ConcurrentCollections;
import com.axalotl.async.common.ParallelProcessor;
import com.axalotl.async.common.config.AsyncConfig;
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
import org.jspecify.annotations.Nullable;
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
import java.util.concurrent.ConcurrentLinkedQueue;
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
    ConcurrentLinkedQueue<BlockEventData> syncedBlockEventQueue;

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

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        navigatingMobs = ConcurrentCollections.newHashSet();
        syncedBlockEventQueue = new ConcurrentLinkedQueue<>();
        players = new CopyOnWriteArrayList<>();
    }


    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
    private void overwriteEntityTicking(EntityTickList entityTickList, Consumer<Entity> consumer) {
        ProfilerFiller profilerfiller = Profiler.get();
        final boolean asyncDespawn = !AsyncConfig.disabled && AsyncConfig.enableAsyncSpawn;

        List<Entity> toTick = new ArrayList<>();
        List<Entity> despawn = new ArrayList<>();

        this.entityTickList.forEach(entity -> {
            if (entity == null || entity.isRemoved()) return;
            if (this.tickRateManager().isEntityFrozen(entity)) return;

            if (!asyncDespawn) {
                profilerfiller.push("checkDespawn");
                entity.checkDespawn();
                profilerfiller.pop();
            }

            if (!(entity instanceof ServerPlayer)
                    && !this.chunkSource.chunkMap.getDistanceManager()
                    .inEntityTickingRange(entity.chunkPosition().pack())) {
                if (asyncDespawn) despawn.add(entity);
                return;
            }

            Entity vehicle = entity.getVehicle();
            if (vehicle != null) {
                if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                    if (asyncDespawn) despawn.add(entity);
                    return;
                }
                entity.stopRiding();
            }

            toTick.add(entity);
        });

        profilerfiller.push("tick");
        if (asyncDespawn) {
            ParallelProcessor.callEntityTickBatch(this.getLevel(), toTick, despawn);
        } else {
            ParallelProcessor.callEntityTickBatch(this.getLevel(), toTick);
        }
        profilerfiller.pop();
    }

    @Redirect(method = "blockEvent", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;add(Ljava/lang/Object;)Z", remap = false))
    private boolean overwriteQueueAdd(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet, Object object) {
        return syncedBlockEventQueue.add((BlockEventData) object);
    }

    @Redirect(method = "clearBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeIf(Ljava/util/function/Predicate;)Z", remap = false))
    private boolean overwriteQueueRemoveIf(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet, Predicate<BlockEventData> filter) {
        return syncedBlockEventQueue.removeIf(filter);
    }

    @Redirect(method = "runBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;isEmpty()Z", remap = false))
    private boolean overwriteEmptyCheck(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet) {
        return syncedBlockEventQueue.isEmpty();
    }

    @Redirect(method = "runBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeFirst()Ljava/lang/Object;", remap = false))
    private Object overwriteQueueRemoveFirst(ObjectLinkedOpenHashSet<BlockEventData> objectLinkedOpenHashSet) {
        return syncedBlockEventQueue.poll();
    }

    @Redirect(method = "runBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;addAll(Ljava/util/Collection;)Z", remap = false))
    private boolean overwriteQueueAddAll(ObjectLinkedOpenHashSet<BlockEventData> instance, Collection<? extends BlockEventData> c) {
        return syncedBlockEventQueue.addAll(c);
    }

    @Redirect(method = "sendBlockUpdated", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerLevel;isUpdatingNavigations:Z", opcode = Opcodes.PUTFIELD))
    private void skipSendBlockUpdatedCheck(ServerLevel instance, boolean value) {
    }

    @WrapMethod(method = "explode")
    private void explode(
            @Nullable final Entity source,
            @Nullable final DamageSource damageSource,
            @Nullable final ExplosionDamageCalculator damageCalculator,
            final double x,
            final double y,
            final double z,
            final float r,
            final boolean fire,
            final ExplosionInteraction interactionType,
            final ParticleOptions smallExplosionParticles,
            final ParticleOptions largeExplosionParticles,
            final WeightedList<ExplosionParticleInfo> blockParticles,
            final Holder<SoundEvent> explosionSound,
            Operation<Void> original
    ) {
        synchronized (this) {
            original.call(
                    source,
                    damageSource,
                    damageCalculator,
                    x,
                    y,
                    z,
                    r,
                    fire,
                    interactionType,
                    smallExplosionParticles,
                    largeExplosionParticles,
                    blockParticles,
                    explosionSound
            );
        }
    }
}