package com.axalotl.async.common.mixin.lithium;

import net.caffeinemc.mods.lithium.common.entity.EntityClassGroup;
import net.caffeinemc.mods.lithium.common.world.chunk.ClassGroupFilterableList;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = ClassInstanceMultiMap.class, priority = 1500)
public abstract class AsyncLithiumClassGroupFilterableListMixin<T> extends AbstractCollection<T>
        implements ClassGroupFilterableList<T> {

    @Shadow
    @Final
    private Class<T> baseClass;

    @Final
    @Shadow
    private List<T> allInstances;

    @Unique
    private final Map<EntityClassGroup, Set<T>> async$entitiesByGroup = new ConcurrentHashMap<>();

    @Unique
    private boolean async$applicable() {
        return Entity.class.isAssignableFrom(this.baseClass);
    }

    @Unique
    private Set<T> async$createGroupSet(EntityClassGroup group) {
        Set<T> set = ConcurrentHashMap.newKeySet();
        for (T candidate : this.allInstances) {
            if (candidate != null && group.contains((Entity) candidate)) {
                set.add(candidate);
            }
        }
        return set;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<T> lithium$getAllOfGroupType(EntityClassGroup group) {
        if (!async$applicable()) return (Collection<T>) java.util.Collections.emptyList();
        Set<T> existing = this.async$entitiesByGroup.get(group);
        if (existing != null) return existing;
        return this.async$entitiesByGroup.computeIfAbsent(group, this::async$createGroupSet);
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void async$onAdd(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (!async$applicable() || this.async$entitiesByGroup.isEmpty()) return;
        Entity asEntity = (Entity) entity;
        for (Map.Entry<EntityClassGroup, Set<T>> e : this.async$entitiesByGroup.entrySet()) {
            if (e.getKey().contains(asEntity)) {
                e.getValue().add(entity);
            }
        }
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void async$onRemove(Object entity, CallbackInfoReturnable<Boolean> cir) {
        if (!async$applicable() || this.async$entitiesByGroup.isEmpty()) return;
        for (Set<T> set : this.async$entitiesByGroup.values()) {
            set.remove(entity);
        }
    }
}
