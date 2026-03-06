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
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Mixin(EntitySection.class)
public class EntitySectionMixin<T extends EntityAccess> {

    @Shadow
    @Final
    private ClassInstanceMultiMap<T> storage;

    @Shadow
    private volatile Visibility chunkStatus;

    @Unique
    private Object async$getMonitor() {
        return this.storage;
    }

    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> async$getEntities(Operation<Stream<T>> original) {
        List<T> snapshot;
        Object monitor = async$getMonitor();
        synchronized (monitor) {
            snapshot = new ArrayList<>(this.storage);
        }
        return snapshot.stream();
    }

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
    private AbortableIterationConsumer.Continuation async$getEntitiesAABB(AABB bb, AbortableIterationConsumer<T> entities, Operation<AbortableIterationConsumer.Continuation> original) {
        List<T> snapshot;
        Object monitor = async$getMonitor();
        synchronized (monitor) {
            snapshot = new ArrayList<>(this.storage);
        }
        for (T entity : snapshot) {
            if (entity != null && entity.getBoundingBox().intersects(bb)
                    && entities.accept(entity).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
    private <U extends T> AbortableIterationConsumer.Continuation async$getEntitiesTyped(EntityTypeTest<T, U> type, AABB bb, AbortableIterationConsumer<? super U> consumer, Operation<AbortableIterationConsumer.Continuation> original) {
        List<T> snapshot;
        Object monitor = async$getMonitor();
        synchronized (monitor) {
            Collection<? extends T> found = this.storage.find(type.getBaseClass());
            snapshot = new ArrayList<>(found);
        }
        for (T entity : snapshot) {
            if (entity == null) continue;
            U casted = type.tryCast(entity);
            if (casted != null && entity.getBoundingBox().intersects(bb)
                    && consumer.accept(casted).shouldAbort()) {
                return AbortableIterationConsumer.Continuation.ABORT;
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }
}