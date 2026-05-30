package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.api.fastutil.ConcurrentLongSortedSet;
import com.axalotl.async.api.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@Mixin(value = EntitySectionStorage.class, priority = 1500)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

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
        this.sections = new Long2ObjectConcurrentHashMap<>();
        this.sectionIds = new ConcurrentLongSortedSet();
    }

    @WrapMethod(method = "forEachAccessibleNonEmptySection")
    private void forEachAccessibleNonEmptySection(AABB bb, AbortableIterationConsumer<EntitySection<T>> output, Operation<Void> original) {
        synchronized (this) {
            original.call(bb, output);
        }
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
        synchronized (this) {
            return original.call(key);
        }
    }

    @WrapMethod(method = "remove")
    private void remove(long sectionKey, Operation<Void> original) {
        synchronized (this) {
            original.call(sectionKey);
        }
    }
}