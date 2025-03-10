package com.axalotl.async.mixin.world;

import com.axalotl.async.ParallelProcessor;
import com.axalotl.async.parallelised.ConcurrentCollections;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.BlockEvent;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.*;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.tick.TickManager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(value = ServerWorld.class, priority = 1500)
public abstract class ServerWorldMixin extends World implements StructureWorldAccess {
    @Unique
    ConcurrentLinkedQueue<BlockEvent> syncedBlockEventQueue;
    @Shadow
    @Final
    @Mutable
    Set<MobEntity> loadedMobs;

    @Shadow
    public abstract TickManager getTickManager();

    @Shadow
    @Final
    private ServerChunkManager chunkManager;

    @Shadow
    public abstract void tickEntity(Entity entity);

    protected ServerWorldMixin(MutableWorldProperties properties, RegistryKey<World> registryRef, DynamicRegistryManager registryManager, RegistryEntry<DimensionType> dimensionEntry, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates) {
        super(properties, registryRef, registryManager, dimensionEntry, isClient, debugWorld, seed, maxChainedNeighborUpdates);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        loadedMobs = ConcurrentCollections.newHashSet();
        syncedBlockEventQueue = new ConcurrentLinkedQueue<>();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/EntityList;forEach(Ljava/util/function/Consumer;)V"))
    private void overwriteEntityTicking(EntityList entityList, Consumer<Entity> action) {
        Profiler profiler = Profilers.get();
        entityList.forEach(entity -> {
            if (!entity.isRemoved()) {
                if (!this.getTickManager().shouldSkipTick(entity)) {
                    profiler.push("checkDespawn");
                    entity.checkDespawn();
                    profiler.pop();
                    if (entity instanceof ServerPlayerEntity || this.chunkManager.chunkLoadingManager.getTicketManager().shouldTickEntities(entity.getChunkPos().toLong())) {
                        Entity entity2 = entity.getVehicle();
                        if (entity2 != null) {
                            if (!entity2.isRemoved() && entity2.hasPassenger(entity)) {
                                return;
                            }

                            entity.stopRiding();
                        }

                        profiler.push("tick");
                        ParallelProcessor.callEntityTick(this::tickEntity, entity);
                        profiler.pop();
                    }
                }
            }
        });
        profiler.push("tick");
        ParallelProcessor.postEntityTick();
        profiler.pop();
    }

    @Redirect(
            method = "addSyncedBlockEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;add(Ljava/lang/Object;)Z",
                    remap = false
            )
    )
    private boolean overwriteQueueAdd(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet, Object object) {
        return syncedBlockEventQueue.add((BlockEvent) object);
    }

    @Redirect(
            method = "clearUpdatesInArea",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeIf(Ljava/util/function/Predicate;)Z",
                    remap = false
            )
    )
    private boolean overwriteQueueRemoveIf(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet, Predicate<BlockEvent> filter) {
        return syncedBlockEventQueue.removeIf(filter);
    }

    @Redirect(
            method = "processSyncedBlockEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;isEmpty()Z",
                    remap = false
            )
    )
    private boolean overwriteEmptyCheck(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet) {
        return syncedBlockEventQueue.isEmpty();
    }

    @Redirect(
            method = "processSyncedBlockEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeFirst()Ljava/lang/Object;",
                    remap = false
            )
    )
    private Object overwriteQueueRemoveFirst(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet) {
        return syncedBlockEventQueue.poll();
    }

    @Redirect(
            method = "processSyncedBlockEvents",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;addAll(Ljava/util/Collection;)Z",
                    remap = false
            )
    )
    private boolean overwriteQueueAddAll(ObjectLinkedOpenHashSet<BlockEvent> instance, Collection<? extends BlockEvent> c) {
        return syncedBlockEventQueue.addAll(c);
    }

    @Redirect(method = "updateListeners", at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerWorld;duringListenerUpdate:Z", opcode = Opcodes.PUTFIELD))
    private void skipSendBlockUpdatedCheck(ServerWorld instance, boolean value) {
    }
}