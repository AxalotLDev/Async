package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongSortedSet;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@Mixin(value = EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Shadow
    @Final
    @Mutable
    private Long2ObjectMap<EntitySection<T>> sections;

    @Shadow
    @Final
    @Mutable
    private LongSortedSet sectionIds;

    @Shadow
    public abstract LongStream getExistingSectionPositionsInChunk(long pos);

    @Unique
    private final StampedLock async$sectionGuard = new StampedLock();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$replaceMaps(CallbackInfo ci) {
        Long2ObjectConcurrentHashMap<EntitySection<T>> newSections = new Long2ObjectConcurrentHashMap<>();
        newSections.putAll(this.sections);
        this.sections = newSections;

        ConcurrentLongSortedSet newIds = new ConcurrentLongSortedSet();
        newIds.addAll(this.sectionIds);
        this.sectionIds = newIds;
    }

    @WrapMethod(method = "getOrCreateSection")
    private EntitySection<T> async$guardGetOrCreate(long sectionPos, Operation<EntitySection<T>> original) {
        long stamp = async$sectionGuard.writeLock();
        EntitySection<T> result = original.call(sectionPos);
        async$sectionGuard.unlockWrite(stamp);
        return result;
    }

    @WrapMethod(method = "remove")
    private void async$guardRemove(long sectionId, Operation<Void> original) {
        long stamp = async$sectionGuard.writeLock();
        original.call(sectionId);
        async$sectionGuard.unlockWrite(stamp);
    }

    @WrapMethod(method = "getExistingSectionsInChunk")
    private Stream<EntitySection<T>> getExistingSections(long pos, Operation<Stream<EntitySection<T>>> original) {
        return this.getExistingSectionPositionsInChunk(pos)
                .mapToObj(this.sections::get)
                .filter(Objects::nonNull)
                .toList()
                .stream();
    }
}