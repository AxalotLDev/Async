package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.caffeinemc.mods.lithium.common.util.collections.ReferenceMaskedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mixin(value = ReferenceMaskedList.class, priority = 1500, remap = false)
public class ReferenceMaskedListMixin<E> {

    @Final
    @Shadow
    private Reference2IntOpenHashMap<E> element2Index;

    @Unique
    private final Object async$lock = new Object();

    @WrapMethod(method = "add(Ljava/lang/Object;)Z")
    private boolean async$add(E element, Operation<Boolean> original) {
        synchronized (async$lock) {
            if (element2Index.containsKey(element)) return false;
            return original.call(element);
        }
    }

    @WrapMethod(method = "remove")
    private boolean async$remove(Object element, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(element);
        }
    }

    @WrapMethod(method = "addOrSet")
    private void async$addOrSet(E element, boolean visible, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(element, visible);
        }
    }

    @WrapMethod(method = "setVisible")
    private void async$setVisible(E element, boolean visible, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(element, visible);
        }
    }

    @WrapMethod(method = "iterator")
    private Iterator<E> async$iterator(Operation<Iterator<E>> original) {
        synchronized (async$lock) {
            List<E> snapshot = new ArrayList<>();
            Iterator<E> it = original.call();
            while (it.hasNext()) {
                snapshot.add(it.next());
            }
            return snapshot.iterator();
        }
    }

    @WrapMethod(method = "totalSize")
    private int async$totalSize(Operation<Integer> original) {
        synchronized (async$lock) {
            return original.call();
        }
    }
}