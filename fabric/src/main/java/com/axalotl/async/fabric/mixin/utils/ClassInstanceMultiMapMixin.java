package com.axalotl.async.fabric.mixin.utils;

import com.axalotl.async.api.utils.ConcurrentCollections;
import com.axalotl.async.api.utils.ConcurrentList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;

@Mixin(value = ClassInstanceMultiMap.class, priority = 1100)
public abstract class ClassInstanceMultiMapMixin<T> extends AbstractCollection<T> {

    @Final
    @Mutable
    @Shadow
    private Map<Class<?>, List<T>> byClass;

    @Final
    @Mutable
    @Shadow
    private List<T> allInstances;

    @Inject(method = "<init>(Ljava/lang/Class;)V", at = @At("RETURN"))
    private void async$forceConcurrent(Class<T> baseClass, CallbackInfo ci) {
        this.allInstances = new ConcurrentList<>();
        this.byClass = new ConcurrentHashMap<>();
        this.byClass.put(baseClass, this.allInstances);
    }

    @ModifyArg(method = "lambda$find$0", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Collector<T, ?, List<T>> overwriteCollectToList(Collector<T, ?, List<T>> collector) {
        return ConcurrentCollections.toList();
    }

    @WrapOperation(method = "remove", at = @At(value = "INVOKE", target = "Ljava/util/List;remove(Ljava/lang/Object;)Z"))
    private boolean async$nullSafeListRemove(List<T> list, Object obj, Operation<Boolean> original) {
        return list != null && original.call(list, obj);
    }

    @WrapOperation(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean async$nullSafeListAdd(List<T> list, Object obj, Operation<Boolean> original) {
        return list != null && original.call(list, obj);
    }
}
