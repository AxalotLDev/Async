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
    private Visibility chunkStatus;
    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "add")
    private void add(EntityAccess entity, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private boolean remove(EntityAccess entity, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "getStatus")
    private Visibility getStatus(Operation<Visibility> original) {
        return this.chunkStatus != null ? this.chunkStatus : Visibility.HIDDEN;
    }

    @WrapMethod(method = "updateChunkStatus")
    private Visibility updateChunkStatus(Visibility status, Operation<Visibility> original) {
        synchronized (async$lock) {
            if (this.chunkStatus == null) {
                this.chunkStatus = Visibility.HIDDEN;
            }
            Visibility safeStatus = (status != null ? status : Visibility.HIDDEN);
            return original.call(safeStatus);
        }
    }

    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> getEntities(Operation<Stream<T>> original) {
        return storage.stream()
                .filter(Objects::nonNull)
                .toList()
                .stream();
    }
}