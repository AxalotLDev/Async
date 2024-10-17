package com.axalotl.async.mixin;

import com.axalotl.async.ParallelProcessor;
import com.axalotl.async.parallelised.ConcurrentCollections;
import com.axalotl.async.parallelised.ParaServerChunkProvider;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.BlockEventS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.BlockEvent;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Mixin(value = ServerWorld.class, priority = 1100)
public abstract class ServerWorldMixin implements StructureWorldAccess {
    @Shadow
    @Final
    @Mutable
    Set<MobEntity> loadedMobs = ConcurrentCollections.newHashSet();
    @Unique
    ConcurrentLinkedDeque<BlockEvent> syncedBlockEventCLinkedQueue = new ConcurrentLinkedDeque<>();
    @Unique
    ServerWorld thisWorld = (ServerWorld) (Object) this;

    @Redirect(method = "tickEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;tick()V"))
    private void overwriteEntityTicking(Entity entity) {
        ParallelProcessor.callEntityTick(entity);
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/level/storage/LevelStorage$Session;Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/structure/StructureTemplateManager;Ljava/util/concurrent/Executor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;IIZLnet/minecraft/server/WorldGenerationProgressListener;Lnet/minecraft/world/chunk/ChunkStatusChangeListener;Ljava/util/function/Supplier;)Lnet/minecraft/server/world/ServerChunkManager;"))
    private ServerChunkManager overwriteServerChunkManager(ServerWorld world, LevelStorage.Session session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor workerExecutor, ChunkGenerator chunkGenerator, int viewDistance, int simulationDistance, boolean dsync, WorldGenerationProgressListener worldGenerationProgressListener, ChunkStatusChangeListener chunkStatusChangeListener, Supplier persistentStateManagerFactory) {
        if (FabricLoader.getInstance().isModLoaded("c2me")) {
            return new ServerChunkManager(
                    this.toServerWorld(),
                    session,
                    dataFixer,
                    server.getStructureTemplateManager(),
                    workerExecutor,
                    chunkGenerator,
                    server.getPlayerManager().getViewDistance(),
                    server.getPlayerManager().getSimulationDistance(),
                    server.syncChunkWrites(),
                    worldGenerationProgressListener,
                    this.entityManager::updateTrackingStatus,
                    () -> server.getOverworld().getPersistentStateManager()
            );
        } else {
            return new ParaServerChunkProvider(world, session, dataFixer, structureTemplateManager, workerExecutor, chunkGenerator, viewDistance, simulationDistance, dsync, worldGenerationProgressListener, chunkStatusChangeListener, persistentStateManagerFactory);
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/EntityList;forEach(Ljava/util/function/Consumer;)V"))
    private void tickStart(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ParallelProcessor.startTick(this.getServer());
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;tickBlockEntities()V", shift = At.Shift.AFTER))
    private void tickEnd(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ParallelProcessor.endTick(this.getServer());
    }

    @Redirect(method = "processSyncedBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;addAll(Ljava/util/Collection;)Z"))
    private boolean overwriteQueueAddAll(ObjectLinkedOpenHashSet<BlockEvent> instance, Collection<? extends BlockEvent> c) {
        return syncedBlockEventCLinkedQueue.addAll(c);
    }

    @Redirect(method = "addSyncedBlockEvent", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;add(Ljava/lang/Object;)Z"))
    private boolean overwriteQueueAdd(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet, Object object) {
        return syncedBlockEventCLinkedQueue.add((BlockEvent) object);
    }

    @Redirect(method = "clearUpdatesInArea", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeIf(Ljava/util/function/Predicate;)Z"))
    private boolean overwriteQueueRemoveIf(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet, Predicate<BlockEvent> filter) {
        return syncedBlockEventCLinkedQueue.removeIf(filter);
    }

    @Redirect(method = "processSyncedBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;isEmpty()Z"))
    private boolean overwriteEmptyCheck(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet) {
        return syncedBlockEventCLinkedQueue.isEmpty();
    }

    @Redirect(method = "processSyncedBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;removeFirst()Ljava/lang/Object;"))
    private Object overwriteQueueRemoveFirst(ObjectLinkedOpenHashSet<BlockEvent> objectLinkedOpenHashSet) {
        BlockEvent blockEvent = syncedBlockEventCLinkedQueue.removeFirst();
        Iterator<BlockEvent> bed = syncedBlockEventCLinkedQueue.iterator();
        while (bed.hasNext()) {
            BlockEvent BlockEvent = bed.next();
            if (thisWorld.processBlockEvent(BlockEvent)) {
                this.getServer().getPlayerManager().sendToAround(null, BlockEvent.pos().getX(), BlockEvent.pos().getY(), BlockEvent.pos().getZ(), 64.0D, thisWorld.getRegistryKey(), new BlockEventS2CPacket(BlockEvent.pos(), BlockEvent.block(), BlockEvent.type(), BlockEvent.data()));
            }
            bed.remove();
        }
        return blockEvent;
    }

    @Shadow
    volatile boolean duringListenerUpdate;

    @Shadow
    public abstract @NotNull MinecraftServer getServer();

    @Shadow
    @Final
    private MinecraftServer server;
    @Shadow
    @Final
    private ServerEntityManager<Entity> entityManager;
    @Unique
    private final ReentrantLock lock = new ReentrantLock();

    @Inject(method = "updateListeners", at = @At("HEAD"))
    private void lockUpdateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        lock.lock();
    }

    @Inject(method = "updateListeners", at = @At("RETURN"))
    private void unlockUpdateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        lock.unlock();
    }

    @Inject(method = "updateListeners", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;error(Ljava/lang/String;Ljava/lang/Throwable;)V"), cancellable = true)
    private void unlockInError(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        duringListenerUpdate = false;
        lock.unlock();
        ci.cancel();
    }
}