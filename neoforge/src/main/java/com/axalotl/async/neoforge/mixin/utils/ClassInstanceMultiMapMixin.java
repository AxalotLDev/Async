package com.axalotl.async.neoforge.mixin.utils;

import com.axalotl.async.api.utils.ConcurrentCollections;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.axalotl.async.api.utils.ConcurrentObjectArrayList;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collector;

@Mixin(value = ClassInstanceMultiMap.class)
public abstract class ClassInstanceMultiMapMixin<T> extends AbstractCollection<T> {

    @Mutable
    @Final
    @Shadow
    private Map<Class<?>, List<T>> byClass;

    @Mutable
    @Final
    @Shadow
    private List<T> allInstances;

    @Final
    @Shadow
    private Class<T> baseClass;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void replaceConcurrentCollections(CallbackInfo ci) {
        this.byClass = new ConcurrentHashMap<>();
        this.allInstances = new ConcurrentObjectArrayList<>();
        this.byClass.put(this.baseClass, this.allInstances);
    }

    @ModifyArg(method = "lambda$find$0", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Collector<T, ?, List<T>> overwriteCollectToList(Collector<T, ?, List<T>> collector) {
        return ConcurrentCollections.toList();
    }
}