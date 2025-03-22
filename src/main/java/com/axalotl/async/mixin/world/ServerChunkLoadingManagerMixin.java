package com.axalotl.async.mixin.world;

import com.axalotl.async.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import com.axalotl.async.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(value = ChunkMap.class, priority = 1500)
public abstract class ServerChunkLoadingManagerMixin extends ChunkStorage implements ChunkHolder.PlayerProvider {
    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<ChunkMap.TrackedEntity> entityMap;

    @Shadow
    @Final
    @Mutable
    private LongSet entitiesInLevel;

    @Shadow
    @Final
    @Mutable
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Shadow
    @Mutable
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Shadow
    @Final
    @Mutable
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    public ServerChunkLoadingManagerMixin(Path directory, DataFixer dataFixer, boolean dsync) {
        super(directory, dataFixer, dsync);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci) {
        entityMap = new Int2ObjectConcurrentHashMap<>();
        entitiesInLevel = new ConcurrentLongLinkedOpenHashSet();
        updatingChunkMap = new Long2ObjectLinkedOpenHashMap<>();
    }

    //"releaseGeneration" method doesn't exist in 1.20.1

    @WrapMethod(method = "addEntity")
    private synchronized void addEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @WrapMethod(method = "removeEntity")
    private synchronized void removeEntity(Entity entity, Operation<Void> original) {
        original.call(entity);
    }

    @Inject(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;pauseInIde(Ljava/lang/Throwable;)Ljava/lang/Throwable;"), cancellable = true)
    private void skipThrowLoadEntity(Entity entity, CallbackInfo ci) {
        ci.cancel();
    }
}