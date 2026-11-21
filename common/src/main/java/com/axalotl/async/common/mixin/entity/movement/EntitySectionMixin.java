package com.axalotl.async.common.mixin.entity.movement;

import com.axalotl.async.common.mixin.lithium.LithiumMovementTrackingLock;
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
import java.util.List;
import java.util.stream.Stream;

@Mixin(EntitySection.class)
public class EntitySectionMixin<T extends EntityAccess> {

    @Unique
    private static final ThreadLocal<ArrayList<Object>> async$matches = ThreadLocal.withInitial(ArrayList::new);

    @Unique
    private static final AbortableIterationConsumer<EntityAccess> async$collector = entity -> {
        async$matches.get().add(entity);
        return AbortableIterationConsumer.Continuation.CONTINUE;
    };

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

    @WrapMethod(method = "updateChunkStatus")
    private Visibility updateChunkStatus(Visibility chunkStatus, Operation<Visibility> original) {
        synchronized (LithiumMovementTrackingLock.OUTSIDE_SECTION) {
            synchronized (this) {
                return original.call(chunkStatus);
            }
        }
    }

    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> getEntities(Operation<Stream<T>> original) {
        synchronized (this) {
            return storage.stream()
                    .toList()
                    .stream();
        }
    }

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
    private AbortableIterationConsumer.Continuation getEntities(AABB bb, AbortableIterationConsumer<T> entities, Operation<AbortableIterationConsumer.Continuation> original) {
        ArrayList<Object> matches = async$matches.get();
        int from = matches.size();
        synchronized (this) {
            original.call(bb, async$collector);
        }
        return drain(matches, from, entities);
    }

    @WrapMethod(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;")
    private <U extends T> AbortableIterationConsumer.Continuation getEntities(EntityTypeTest<T, U> type, AABB bb, AbortableIterationConsumer<? super U> consumer, Operation<AbortableIterationConsumer.Continuation> original) {
        ArrayList<Object> matches = async$matches.get();
        int from = matches.size();
        synchronized (this) {
            original.call(type, bb, async$collector);
        }
        return drain(matches, from, consumer);
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static <E> AbortableIterationConsumer.Continuation drain(List<?> matches, int from, AbortableIterationConsumer<E> consumer) {
        boolean aborted = false;
        try {
            for (int i = from; i < matches.size(); i++) {
                if (consumer.accept((E) matches.get(i)).shouldAbort()) {
                    aborted = true;
                    break;
                }
            }
        } finally {
            while (matches.size() > from) {
                matches.removeLast();
            }
        }
        return aborted ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;
    }
}