package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;
import java.util.stream.Stream;

@Mixin(EntitySection.class)
public class EntitySectionMixin<T extends EntityAccess> {

    @Shadow
    @Final
    private ClassInstanceMultiMap<T> storage;

    @Shadow
    private volatile Visibility chunkStatus;

    @WrapMethod(method = "add")
    private void add(EntityAccess entity, Operation<Void> original) {
        synchronized (this) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private boolean remove(EntityAccess entity, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> getEntities(Operation<Stream<T>> original) {
        synchronized (this) {
            return storage.stream()
                    .filter(Objects::nonNull)
                    .toList()
                    .stream();
        }
    }

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
    private AbortableIterationConsumer.Continuation getEntities(AABB bb, AbortableIterationConsumer<T> entities, Operation<AbortableIterationConsumer.Continuation> original) {
        synchronized (this) {
            return original.call(bb, entities);
        }
    }

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
    private <U extends T> AbortableIterationConsumer.Continuation getEntities(EntityTypeTest<T, U> type, AABB bb, AbortableIterationConsumer<? super U> consumer, Operation<AbortableIterationConsumer.Continuation> original) {
        synchronized (this) {
            return original.call(type, bb, consumer);
        }
    }
}