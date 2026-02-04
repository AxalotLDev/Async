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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@Mixin(value = EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Shadow
    private final Long2ObjectMap<EntitySection<T>> sections = new Long2ObjectConcurrentHashMap<>();

    @Shadow
    private final LongSortedSet sectionIds = new ConcurrentLongSortedSet();

    @Shadow
    public abstract LongStream getExistingSectionPositionsInChunk(long pos);

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "getExistingSectionsInChunk")
    private Stream<EntitySection<T>> getExistingSections(long pos, Operation<Stream<EntitySection<T>>> original) {
        return this.getExistingSectionPositionsInChunk(pos)
                .mapToObj(this.sections::get)
                .filter(Objects::nonNull)
                .toList()
                .stream();
    }

    @WrapMethod(method = "getOrCreateSection")
    private EntitySection<T> getOrCreateSection(long pos, Operation<EntitySection<T>> original) {
        EntitySection<T> existing = this.sections.get(pos);
        if (existing != null) return existing;
        synchronized (async$lock) {
            return original.call(pos);
        }
    }
}