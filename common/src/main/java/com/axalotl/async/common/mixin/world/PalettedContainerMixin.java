package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {

    @Unique
    private final StampedLock lock = new StampedLock();

    @WrapMethod(method = "get(III)Ljava/lang/Object;")
    private T get(int x, int y, int z, Operation<T> original) {
        long stamp = lock.tryOptimisticRead();
        if (stamp != 0L) {
            try {
                T result = original.call(x, y, z);
                if (lock.validate(stamp)) {
                    return result;
                }
            } catch (Throwable ignored) {
            }
        }
        stamp = lock.readLock();
        try {
            return original.call(x, y, z);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @WrapMethod(method = "getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;")
    private T getAndSet(int x, int y, int z, T value, Operation<T> original) {
        long stamp = lock.writeLock();
        try {
            return original.call(x, y, z, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @WrapMethod(method = "getAndSetUnchecked")
    private T getAndSetUnchecked(int x, int y, int z, T value, Operation<T> original) {
        long stamp = lock.writeLock();
        try {
            return original.call(x, y, z, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @WrapMethod(method = "set(IIILjava/lang/Object;)V")
    private void set(int x, int y, int z, T value, Operation<Void> original) {
        long stamp = lock.writeLock();
        try {
            original.call(x, y, z, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @WrapMethod(method = "read")
    private void read(FriendlyByteBuf buffer, Operation<Void> original) {
        long stamp = lock.writeLock();
        try {
            original.call(buffer);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @WrapMethod(method = "write")
    private void write(FriendlyByteBuf buffer, Operation<Void> original) {
        long stamp = lock.readLock();
        try {
            original.call(buffer);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @WrapMethod(method = "pack")
    private PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy, Operation<PalettedContainerRO.PackedData<T>> original) {
        long stamp = lock.readLock();
        try {
            return original.call(strategy);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @WrapMethod(method = "getAll")
    private void getAll(Consumer<T> consumer, Operation<Void> original) {
        long stamp = lock.readLock();
        try {
            original.call(consumer);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @WrapMethod(method = "count")
    private void count(PalettedContainer.CountConsumer<T> output, Operation<Void> original) {
        long stamp = lock.readLock();
        try {
            original.call(output);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @WrapMethod(method = "copy")
    private PalettedContainer<T> copy(Operation<PalettedContainer<T>> original) {
        long stamp = lock.readLock();
        try {
            return original.call();
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @WrapMethod(method = "maybeHas")
    private boolean maybeHas(Predicate<T> predicate, Operation<Boolean> original) {
        long stamp = lock.readLock();
        try {
            return original.call(predicate);
        } finally {
            lock.unlockRead(stamp);
        }
    }
}