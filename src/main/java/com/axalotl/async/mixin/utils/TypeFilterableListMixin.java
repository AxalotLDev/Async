package com.axalotl.async.mixin.utils;

import com.axalotl.async.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.collection.TypeFilterableList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collector;

@Mixin(value = TypeFilterableList.class)
public abstract class TypeFilterableListMixin<T> extends AbstractCollection<T> {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @Shadow
    private final Map<Class<?>, List<T>> elementsByType = new ConcurrentHashMap<>();

    @Shadow
    private final List<T> allElements = new CopyOnWriteArrayList<>();

    @ModifyArg(method = "method_15217", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Collector<T, ?, List<T>> overwriteCollectToList(Collector<T, ?, List<T>> collector) {
        return ConcurrentCollections.toList();
    }

    @WrapMethod(method = "add")
    private boolean add(Object e, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(e);
        }
    }

    @WrapMethod(method = "remove")
    private boolean remove(Object o, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "getAllOfType")
    private <S extends T> Collection<S> getAllOfType(Class<S> type, Operation<Collection<S>> original) {
        synchronized (lock) {
            return original.call(type);
        }
    }

    @WrapMethod(method = "iterator")
    public Iterator<T> iterator(Operation<Iterator<T>> original) {
        synchronized (lock) {
            return original.call();
        }
    }
}