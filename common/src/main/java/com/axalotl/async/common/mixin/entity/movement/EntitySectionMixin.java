package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Objects;
import java.util.stream.Stream;

@Mixin(EntitySection.class)
public class EntitySectionMixin<T extends EntityAccess> {

    @Shadow
    @Final
    private ClassInstanceMultiMap<T> storage;

    @Shadow
    private volatile Visibility chunkStatus;

    @Unique
    private final Object async$storageLock = new Object();

    @WrapMethod(method = "add")
    private void async$add(EntityAccess entity, Operation<Void> original) {
        synchronized (async$storageLock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private boolean async$remove(EntityAccess entity, Operation<Boolean> original) {
        synchronized (async$storageLock) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> async$getEntities(Operation<Stream<T>> original) {
        synchronized (async$storageLock) {
            return storage.stream()
                    .filter(Objects::nonNull)
                    .toList()
                    .stream();
        }
    }

    @WrapMethod(method = "getStatus")
    private Visibility async$getStatus(Operation<Visibility> original) {
        return original.call();
    }

    @WrapMethod(method = "updateChunkStatus")
    private Visibility async$updateChunkStatus(Visibility status, Operation<Visibility> original) {
        return original.call(status);
    }
}