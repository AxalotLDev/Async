package com.axalotl.async.mixin.world;

import com.axalotl.async.Async;
import com.axalotl.async.ParallelProcessor;
import com.axalotl.async.parallelised.ConcurrentCollections;
import com.axalotl.async.parallelised.ParaServerChunkProvider;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.BlockEvent;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.ChunkStatusChangeListener;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.level.storage.LevelStorage;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin implements StructureWorldAccess {
    @Unique
    ConcurrentLinkedDeque<BlockEvent> syncedBlockEventCLinkedQueue = new ConcurrentLinkedDeque<>();
    @Shadow
    @Final
    @Mutable
    Set<MobEntity> loadedMobs = ConcurrentCollections.newHashSet();
    @Shadow
    @Final
    private ServerEntityManager<Entity> entityManager;
    @Shadow
    @Final
    private MinecraftServer server;
    @Unique
    ServerWorld thisWorld = (ServerWorld) (Object) this;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V", ordinal = 2))
    private void preEntityTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        ParallelProcessor.preEntityTick();
    }

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/level/storage/LevelStorage$Session;Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/structure/StructureTemplateManager;Ljava/util/concurrent/Executor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;IIZLnet/minecraft/server/WorldGenerationProgressListener;Lnet/minecraft/world/chunk/ChunkStatusChangeListener;Ljava/util/function/Supplier;)Lnet/minecraft/server/world/ServerChunkManager;"))
    private ServerChunkManager overwriteServerChunkManager(ServerWorld world, LevelStorage.Session session, DataFixer dataFixer, StructureTemplateManager structureTemplateManager, Executor workerExecutor, ChunkGenerator chunkGenerator, int viewDistance, int simulationDistance, boolean dsync, WorldGenerationProgressListener worldGenerationProgressListener, ChunkStatusChangeListener chunkStatusChangeListener, Supplier persistentStateManagerFactory) {
        if (Async.c2me) {
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

    @Redirect(method = "method_31420", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;tickEntity(Ljava/util/function/Consumer;Lnet/minecraft/entity/Entity;)V"))
    private void overwriteEntityTicking(ServerWorld instance, Consumer consumer, Entity entity) {
        ParallelProcessor.callEntityTick(consumer, entity, thisWorld);
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
        return syncedBlockEventCLinkedQueue.removeFirst();
    }

    @Redirect(method = "processSyncedBlockEvents", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;addAll(Ljava/util/Collection;)Z"))
    private boolean overwriteQueueAddAll(ObjectLinkedOpenHashSet<BlockEvent> instance, Collection<? extends BlockEvent> c) {
        return syncedBlockEventCLinkedQueue.addAll(c);
    }

    @Redirect(method = "updateListeners", at = @At(value = "FIELD", target = "Lnet/minecraft/server/world/ServerWorld;duringListenerUpdate:Z", opcode = Opcodes.PUTFIELD))
    private void skipSendBlockUpdatedCheck(ServerWorld instance, boolean value) {

    }

    @WrapMethod(method = "emitGameEvent")
    private synchronized void postBlockEntityTick(RegistryEntry<GameEvent> event, Vec3d emitterPos, GameEvent.Emitter emitter, Operation<Void> original) {
        original.call(event, emitterPos, emitter);
    }
}