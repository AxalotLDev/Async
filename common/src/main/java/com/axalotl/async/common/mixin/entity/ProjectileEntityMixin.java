package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Consumer;

@Mixin(Projectile.class)
public class ProjectileEntityMixin {
    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "spawnProjectile(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/world/entity/projectile/Projectile;")
    private static <T extends Projectile> T spawn(T projectile, ServerLevel level, ItemStack stack, Consumer<T> adapter, Operation<T> original) {
        synchronized (async$lock) {
            return original.call(projectile, level, stack, adapter);
        }
    }
}