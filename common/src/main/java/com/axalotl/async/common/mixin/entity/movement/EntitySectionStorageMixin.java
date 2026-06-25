package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.api.fastutil.ConcurrentLongSortedSet;
import com.axalotl.async.api.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@Mixin(value = EntitySectionStorage.class, priority = 1500)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Unique
    private ReentrantReadWriteLock.WriteLock writeLock;

    @Mutable
    @Final
    @Shadow
    private Long2ObjectMap<EntitySection<T>> sections;

    @Mutable
    @Final
    @Shadow
    private LongSortedSet sectionIds;

    @Shadow
    public abstract LongStream getExistingSectionPositionsInChunk(long chunkKey);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceWithConcurrentCollections(CallbackInfo ci) {
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        this.writeLock = rwLock.writeLock();
        this.sections = new Long2ObjectConcurrentHashMap<>();
        this.sectionIds = new ConcurrentLongSortedSet();
    }

    @WrapMethod(method = "getExistingSectionsInChunk")
    private Stream<EntitySection<T>> getExistingSections(long chunkKey, Operation<Stream<EntitySection<T>>> original) {
        return this.getExistingSectionPositionsInChunk(chunkKey)
                .mapToObj(this.sections::get)
                .filter(Objects::nonNull)
                .toList()
                .stream();
    }

    @WrapMethod(method = "getOrCreateSection")
    private EntitySection<T> getOrCreateSection(long key, Operation<EntitySection<T>> original) {
        EntitySection<T> existing = sections.get(key);
        if (existing != null) return existing;
        writeLock.lock();
        try {
            existing = sections.get(key);
            if (existing != null) return existing;
            return original.call(key);
        } finally {
            writeLock.unlock();
        }
    }


    @WrapMethod(method = "remove")
    private void remove(long sectionKey, Operation<Void> original) {
        writeLock.lock();
        try {
            original.call(sectionKey);
        } finally {
            writeLock.unlock();
        }
    }
}