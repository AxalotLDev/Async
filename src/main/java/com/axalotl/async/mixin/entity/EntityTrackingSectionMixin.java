package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityTrackingSection.class)
public class EntityTrackingSectionMixin<T extends EntityLike> {

    @WrapMethod(method = "add")
    private synchronized void onAdd(T entity, Operation<Void> original) {
        try {
            original.call(entity);
        } catch (Exception ignored) {
        }
    }

    @WrapMethod(method = "remove")
    private boolean onRemove(T entity, Operation<Boolean> original) {
        try {
            return original.call(entity);
        } catch (Exception e) {
            return false;
        }
    }
}
