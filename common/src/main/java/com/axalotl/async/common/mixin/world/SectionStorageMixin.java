package com.axalotl.async.common.mixin.world;

import com.axalotl.async.common.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import com.axalotl.async.common.parallelised.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin<R> implements AutoCloseable {

    @Shadow
    @Mutable
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectConcurrentHashMap<>();

    @Shadow
    @Mutable
    private final LongLinkedOpenHashSet dirty = new ConcurrentLongLinkedOpenHashSet();

    //Experimental
    @WrapMethod(method = "readColumn(Lnet/minecraft/world/level/ChunkPos;)V")
    private synchronized void release(ChunkPos chunkPos, Operation<Void> original) {
        original.call(chunkPos);
    }
}
