package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;


@Mixin(EntityTickList.class)
public class EntityTickListMixin {
    @Shadow
    private Int2ObjectMap<Entity> passive = new Int2ObjectConcurrentHashMap<>();
    @Shadow
    private Int2ObjectMap<Entity> active;
    @Shadow
    private Int2ObjectMap<Entity> iterated;

    @Unique
    private final Object async$lock = new Object();

    @WrapMethod(method = "add")
    private void add(Entity entity, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private void remove(Entity entity, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(entity);
        }
    }

    @Redirect(
            method = "forEach",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;values()Lit/unimi/dsi/fastutil/objects/ObjectCollection;"
            )
    )
    private ObjectCollection<Entity> redirectForEachValues(Int2ObjectMap<Entity> instance) {
        return new ObjectArrayList<>(instance.values());
    }
}