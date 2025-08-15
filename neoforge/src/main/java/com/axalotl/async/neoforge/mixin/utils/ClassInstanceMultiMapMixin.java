package com.axalotl.async.neoforge.mixin.utils;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;

@Mixin(value = ClassInstanceMultiMap.class)
public abstract class ClassInstanceMultiMapMixin<T> extends AbstractCollection<T> {

    @Unique
    private static final Object async$lock = new Object();

    @Shadow
    private final Map<Class<?>, List<T>> byClass = new ConcurrentHashMap<>();

    @Shadow
    private final List<T> allInstances = new CopyOnWriteArrayList<>();

    @ModifyArg(method = {"lambda$find$0", "m_13537_"}, at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
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
}