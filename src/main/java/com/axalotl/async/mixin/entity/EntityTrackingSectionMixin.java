package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.function.LazyIterationConsumer;
import net.minecraft.util.math.Box;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.EntityTrackingStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Mixin(EntityTrackingSection.class)
public class EntityTrackingSectionMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "add")
    private void add(EntityLike entity, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private boolean remove(EntityLike entity, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "swapStatus")
    private EntityTrackingStatus swapStatus(EntityTrackingStatus status, Operation<EntityTrackingStatus> original) {
        synchronized (lock) {
            return original.call(status);
        }
    }

    @WrapMethod(method = "getStatus")
    private EntityTrackingStatus getStatus(Operation<EntityTrackingStatus> original) {
        synchronized (lock) {
            return original.call();
        }
    }

    @WrapMethod(method = "isEmpty")
    private boolean isEmpty(Operation<Boolean> original) {
        synchronized (lock) {
            return original.call();
        }
    }

    @WrapMethod(method = "stream")
    private <T extends EntityLike> Stream<T> stream(Operation<Stream<T>> original) {
        synchronized (lock) {
            return original.call();
        }
    }

    @WrapMethod(method = "forEach(Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;")
    private <T extends EntityLike> LazyIterationConsumer.NextIteration forEach(Box box, LazyIterationConsumer<T> consumer, Operation<LazyIterationConsumer.NextIteration> original) {
        synchronized (lock) {
            return original.call(box, consumer);
        }
    }

    @WrapMethod(method = "forEach(Lnet/minecraft/util/TypeFilter;Lnet/minecraft/util/math/Box;Lnet/minecraft/util/function/LazyIterationConsumer;)Lnet/minecraft/util/function/LazyIterationConsumer$NextIteration;")
    private <T extends EntityLike>  LazyIterationConsumer.NextIteration forEach(TypeFilter<T, T> type, Box box, LazyIterationConsumer<? super T> consumer, Operation<LazyIterationConsumer.NextIteration> original) {
        synchronized (lock) {
            return original.call(type, box, consumer);
        }
    }

    @WrapMethod(method = "size")
    private int size(Operation<Integer> original) {
        synchronized (lock) {
            return original.call();
        }
    }
}