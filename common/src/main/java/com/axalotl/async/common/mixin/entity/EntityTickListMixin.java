package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.utils.IteratorSafeOrderedReferenceSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Consumer;

@Mixin(EntityTickList.class)
public class EntityTickListMixin {
    @Unique
    public final IteratorSafeOrderedReferenceSet<Entity> async$entities = new IteratorSafeOrderedReferenceSet<>();

    @WrapMethod(method = "add")
    private void add(Entity entity, Operation<Void> original) {
        this.async$entities.add(entity);
    }

    @WrapMethod(method = "remove")
    private void remove(Entity entity, Operation<Void> original) {
        this.async$entities.remove(entity);
    }

    @WrapMethod(method = "contains")
    private boolean contains(Entity entity, Operation<Void> original) {
        return this.async$entities.contains(entity);
    }

    @WrapMethod(method = "ensureActiveIsNotIterated")
    private void ensureActiveIsNotIterated(Operation<Void> original) {
    }

    @WrapMethod(method = "forEach")
    private void forEach(Consumer<Entity> p_entity, Operation<Void> original) {
        final IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.async$entities.iterator();
        try {
            while (iterator.hasNext()) {
                p_entity.accept(iterator.next());
            }
        } finally {
            iterator.finishedIterating();
        }
    }
}