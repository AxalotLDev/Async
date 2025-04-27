package com.axalotl.async.fabric.mixin.utils;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collector;

@Mixin(value = ClassInstanceMultiMap.class)
public abstract class ClassInstanceMultiMapMixin<T> extends AbstractCollection<T> {

    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    @Shadow
    private final Map<Class<?>, List<T>> byClass = new ConcurrentHashMap<>();

    @Shadow
    private final List<T> allInstances = new CopyOnWriteArrayList<>();

    @ModifyArg(method = "method_15217", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Collector<T, ?, List<T>> overwriteCollectToList(Collector<T, ?, List<T>> collector) {
        return ConcurrentCollections.toList();
    }

    @WrapMethod(method = "add")
    private boolean add(Object e, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(e);
        }
    }

    @WrapMethod(method = "remove")
    private boolean remove(Object o, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "find")
    private <S extends T> Collection<S> find(Class<S> type, Operation<Collection<S>> original) {
        synchronized (async$lock) {
            return original.call(type);
        }
    }
}