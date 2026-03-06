package com.axalotl.async.common.mixin.server;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.entity.EntitySectionStorage;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> implements AutoCloseable {

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Visibility> chunkVisibility;

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap chunkLoadStatuses;

    @Mutable
    @Shadow
    @Final
    Set<UUID> knownUuids;

    @Mutable
    @Shadow
    @Final
    private LongSet chunksToUnload;

    @Shadow
    @Final
    public EntitySectionStorage<T> sectionStorage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void async$replaceMaps(CallbackInfo ci) {
        Long2ObjectConcurrentHashMap<Visibility> concurrentVisibility = new Long2ObjectConcurrentHashMap<>();
        concurrentVisibility.defaultReturnValue(Visibility.HIDDEN);
        concurrentVisibility.putAll(this.chunkVisibility);
        this.chunkVisibility = concurrentVisibility;

        this.sectionStorage.intialSectionVisibility = concurrentVisibility;

        Long2ObjectConcurrentHashMap concurrentStatuses = new Long2ObjectConcurrentHashMap();
        concurrentStatuses.defaultReturnValue(this.chunkLoadStatuses.defaultReturnValue());
        concurrentStatuses.putAll(this.chunkLoadStatuses);
        this.chunkLoadStatuses = concurrentStatuses;

        Set<UUID> concurrentUuids = ConcurrentHashMap.newKeySet();
        concurrentUuids.addAll(this.knownUuids);
        this.knownUuids = concurrentUuids;

        this.chunksToUnload = LongSets.synchronize(new LongOpenHashSet(this.chunksToUnload));
    }

    @WrapMethod(method = "getEffectiveStatus")
    private static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility visibility, Operation<Visibility> original) {
        Visibility result = original.call(entity, visibility);
        if (result == null) {
            return entity.isAlwaysTicking() ? Visibility.TICKING : Visibility.TRACKED;
        }
        return result;
    }
}