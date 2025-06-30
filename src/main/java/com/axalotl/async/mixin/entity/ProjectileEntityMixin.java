package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {

    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "spawn(Lnet/minecraft/entity/projectile/ProjectileEntity;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Ljava/util/function/Consumer;)Lnet/minecraft/entity/projectile/ProjectileEntity;")
    private static <T extends ProjectileEntity> T spawn(T projectile, ServerWorld world, ItemStack projectileStack, Consumer<T> beforeSpawn, Operation<T> original) {
        synchronized (lock) {
            return original.call(projectile, world, projectileStack, beforeSpawn);
        }
    }
}
