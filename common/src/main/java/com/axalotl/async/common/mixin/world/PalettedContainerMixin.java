package com.axalotl.async.common.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> {

    @Unique
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @WrapMethod(method = "get(III)Ljava/lang/Object;")
    private T get(int x, int y, int z, Operation<T> original) {
        lock.readLock().lock();
        try {
            return original.call(x, y, z);
        } finally {
            lock.readLock().unlock();
        }
    }

    @WrapMethod(method = "getAndSet(IIILjava/lang/Object;)Ljava/lang/Object;")
    private T getAndSet(int x, int y, int z, T value, Operation<T> original) {
        lock.writeLock().lock();
        try {
            return original.call(x, y, z, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "getAndSetUnchecked")
    private T getAndSetUnchecked(int x, int y, int z, T value, Operation<T> original) {
        lock.writeLock().lock();
        try {
            return original.call(x, y, z, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "set(IIILjava/lang/Object;)V")
    private void set(int x, int y, int z, T value, Operation<Void> original) {
        lock.writeLock().lock();
        try {
            original.call(x, y, z, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "read")
    private void read(FriendlyByteBuf buffer, Operation<Void> original) {
        lock.writeLock().lock();
        try {
            original.call(buffer);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @WrapMethod(method = "write")
    private void write(FriendlyByteBuf buffer, Operation<Void> original) {
        lock.readLock().lock();
        try {
            original.call(buffer);
        } finally {
            lock.readLock().unlock();
        }
    }

    @WrapMethod(method = "pack")
    private PalettedContainerRO.PackedData<T> pack(Strategy<T> strategy,
                                                         Operation<PalettedContainerRO.PackedData<T>> original) {
        lock.readLock().lock();
        try {
            return original.call(strategy);
        } finally {
            lock.readLock().unlock();
        }
    }

    @WrapMethod(method = "getAll")
    private void getAll(Consumer<T> consumer, Operation<Void> original) {
        lock.readLock().lock();
        try {
            original.call(consumer);
        } finally {
            lock.readLock().unlock();
        }
    }

    @WrapMethod(method = "count")
    private void count(PalettedContainer.CountConsumer<T> output, Operation<Void> original) {
        lock.readLock().lock();
        try {
            original.call(output);
        } finally {
            lock.readLock().unlock();
        }
    }

    @WrapMethod(method = "copy")
    private PalettedContainer<T> copy(Operation<PalettedContainer<T>> original) {
        lock.readLock().lock();
        try {
            return original.call();
        } finally {
            lock.readLock().unlock();
        }
    }

    @WrapMethod(method = "maybeHas")
    private boolean maybeHas(Predicate<T> predicate, Operation<Boolean> original) {
        lock.readLock().lock();
        try {
            return original.call(predicate);
        } finally {
            lock.readLock().unlock();
        }
    }
}