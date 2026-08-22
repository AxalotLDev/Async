package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.caffeinemc.mods.lithium.common.entity.item.ItemEntityList;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Stream;

@Mixin(value = ItemEntityList.class, remap = false)
public class LithiumItemEntityListMixin {

    @WrapMethod(method = "size")
    private int size(Operation<Integer> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "isEmpty")
    private boolean isEmpty(Operation<Boolean> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "contains")
    private boolean contains(Object o, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "toArray()[Ljava/lang/Object;")
    private Object[] toArray(Operation<Object[]> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "toArray([Ljava/lang/Object;)[Ljava/lang/Object;")
    private <U> U[] toArray(U[] a, Operation<U[]> original) {
        synchronized (this) {
            return original.call((Object) a);
        }
    }

    @WrapMethod(method = "toArray(Ljava/util/function/IntFunction;)[Ljava/lang/Object;")
    private <U> U[] toArray(IntFunction<U[]> generator, Operation<U[]> original) {
        synchronized (this) {
            return original.call(generator);
        }
    }

    @WrapMethod(method = "add(Lnet/minecraft/world/entity/item/ItemEntity;)Z")
    private boolean add(ItemEntity element, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(element);
        }
    }

    @WrapMethod(method = "remove(Ljava/lang/Object;)Z")
    private boolean remove(Object o, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "remove(I)Lnet/minecraft/world/entity/item/ItemEntity;")
    private ItemEntity removeAt(int index, Operation<ItemEntity> original) {
        synchronized (this) {
            return original.call(index);
        }
    }

    @WrapMethod(method = "containsAll")
    private boolean containsAll(Collection<?> c, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(c);
        }
    }

    @WrapMethod(method = "clear")
    private void clear(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }

    @WrapMethod(method = "equals")
    private boolean equals(Object o, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "hashCode")
    private int hashCode(Operation<Integer> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "get(I)Lnet/minecraft/world/entity/item/ItemEntity;")
    private ItemEntity get(int index, Operation<ItemEntity> original) {
        synchronized (this) {
            return original.call(index);
        }
    }

    @WrapMethod(method = "set(ILnet/minecraft/world/entity/item/ItemEntity;)Lnet/minecraft/world/entity/item/ItemEntity;")
    private ItemEntity set(int i, ItemEntity newElement, Operation<ItemEntity> original) {
        synchronized (this) {
            return original.call(i, newElement);
        }
    }

    @WrapMethod(method = "indexOf")
    private int indexOf(Object o, Operation<Integer> original) {
        synchronized (this) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "lastIndexOf")
    private int lastIndexOf(Object o, Operation<Integer> original) {
        synchronized (this) {
            return original.call(o);
        }
    }

    @WrapMethod(method = "stream")
    private Stream<ItemEntity> stream(Operation<Stream<ItemEntity>> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "parallelStream")
    private Stream<ItemEntity> parallelStream(Operation<Stream<ItemEntity>> original) {
        synchronized (this) {
            return original.call();
        }
    }

    @WrapMethod(method = "forEach")
    private void forEach(Consumer<? super ItemEntity> action, Operation<Void> original) {
        synchronized (this) {
            original.call(action);
        }
    }

    @WrapMethod(method = "lithium$notify(Lnet/minecraft/world/entity/item/ItemEntity;I)V")
    private void notify(ItemEntity publisher, int subscriberData, Operation<Void> original) {
        synchronized (this) {
            original.call(publisher, subscriberData);
        }
    }

    @WrapMethod(method = "lithium$forceUnsubscribe(Lnet/minecraft/world/entity/item/ItemEntity;I)V")
    private void forceUnsubscribe(ItemEntity publisher, int subscriberData, Operation<Void> original) {
        synchronized (this) {
            original.call(publisher, subscriberData);
        }
    }

    @WrapMethod(method = "lithium$notifyCount(Lnet/minecraft/world/entity/item/ItemEntity;II)V")
    private void notifyCount(ItemEntity element, int index, int newCount, Operation<Void> original) {
        synchronized (this) {
            original.call(element, index, newCount);
        }
    }

    @WrapMethod(method = "consumeForEntityStacking")
    private AbortableIterationConsumer.Continuation consumeForEntityStacking(ItemEntity searchingEntity, AbortableIterationConsumer<ItemEntity> itemEntityConsumer, Operation<AbortableIterationConsumer.Continuation> original) {
        synchronized (this) {
            return original.call(searchingEntity, itemEntityConsumer);
        }
    }
}