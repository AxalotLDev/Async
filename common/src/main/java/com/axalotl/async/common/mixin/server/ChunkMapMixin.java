package com.axalotl.async.common.mixin.server;

import com.axalotl.async.api.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

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
    private ChunkMap.DistanceManager distanceManager;

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Mutable
    @Shadow
    @Final
    private LongSet chunksToEagerlySave;

    public ChunkMapMixin(RegionStorageInfo info, Path folder, DataFixer fixerUpper, boolean sync, DataFixTypes dataFixType) {
        super(info, folder, fixerUpper, sync, dataFixType);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci) {
        entityMap = new Int2ObjectConcurrentHashMap<>();
        pendingGenerationTasks = new CopyOnWriteArrayList<>();
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
    private synchronized void releaseGeneration(GenerationChunkHolder chunkHolder, Operation<Void> original) {
        original.call(chunkHolder);
    }

    @WrapMethod(method = "setChunkUnsaved")
    private void setChunkUnsaved(ChunkPos chunkPos, Operation<Void> original) {
        synchronized (this) {
            original.call(chunkPos);
        }
    }

    @WrapMethod(method = "saveChunksEagerly")
    private void saveChunksEagerly(BooleanSupplier haveTime, Operation<Void> original) {
        synchronized (this) {
            original.call(haveTime);
        }
    }

    @WrapMethod(method = "saveAllChunks")
    private void saveAllChunks(boolean flushStorage, Operation<Void> original) {
        synchronized (this) {
            original.call(flushStorage);
        }
    }

    @Inject(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;pauseInIde(Ljava/lang/Throwable;)Ljava/lang/Throwable;"), cancellable = true)
    private void skipThrowLoadEntity(Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}