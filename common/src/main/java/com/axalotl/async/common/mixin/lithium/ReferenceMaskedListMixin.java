package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.caffeinemc.mods.lithium.common.util.collections.ReferenceMaskedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;

@Mixin(value = ReferenceMaskedList.class, priority = 1500, remap = false)
public class ReferenceMaskedListMixin<E> {

    @Final
    @Shadow
    private Reference2IntOpenHashMap<E> element2Index;

    @WrapMethod(method = "add(Ljava/lang/Object;)Z")
    private boolean add(E e, Operation<Boolean> original) {
        synchronized (this) {
            if (element2Index.containsKey(e)) return false;
            return original.call(e);
        }
    }

    @WrapMethod(method = "remove")
    private boolean remove(Object o, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "addOrSet")
    private void addOrSet(E element, boolean visible, Operation<Void> original) {
        synchronized (this) {
            original.call(element, visible);
        }
    }

    @WrapMethod(method = "setVisible")
    private void setVisible(E element, boolean visible, Operation<Void> original) {
        synchronized (this) {
            original.call(element, visible);
        }
    }

    @WrapMethod(method = "iterator")
    private Iterator<E> iterator(Operation<Iterator<E>> original) {
        synchronized (this) {
            List<E> snapshot = new ArrayList<>();
            Iterator<E> it = original.call();
            while (it.hasNext()) {
                snapshot.add(it.next());
            }
            return snapshot.iterator();
        }
    }

    @WrapMethod(method = "totalSize")
    private int totalSize(Operation<Integer> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "get")
    private E get(int index, Operation<E> original) {
        synchronized (this) {
            return original.call(index);
        }
    }

    @WrapMethod(method = "size")
    private int size(Operation<Integer> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "spliterator")
    private Spliterator<E> spliterator(Operation<Spliterator<E>> original) {
        synchronized (this) {
            List<E> snapshot = new ArrayList<>();
            original.call().forEachRemaining(snapshot::add);
            return snapshot.spliterator();
        }
    }
}