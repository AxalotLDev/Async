package com.axalotl.async.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = World.class, priority = 1500)
public abstract class WorldMixin implements WorldAccess, AutoCloseable {
    @Shadow
    @Final
    private Thread thread;

    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @Redirect(method = "getBlockEntity", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"))
    private Thread overwriteCurrentThread() {
        return this.thread;
    }

    @WrapMethod(method = "createExplosion(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/world/World$ExplosionSourceType;)V")
    private void createExplosion(Entity entity, double x, double y, double z, float power, World.ExplosionSourceType explosionSourceType, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity, x, y, z, power, explosionSourceType);
        }
    }

    @WrapMethod(method = "createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/world/explosion/ExplosionBehavior;DDDFZLnet/minecraft/world/World$ExplosionSourceType;)V")
    private void createExplosion(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType);
        }
    }

    @WrapMethod(method = "createExplosion(Lnet/minecraft/entity/Entity;DDDFZLnet/minecraft/world/World$ExplosionSourceType;)V")
    private void createExplosion(Entity entity, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity, x, y, z, power, createFire, explosionSourceType);
        }
    }

    @WrapMethod(method = "createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/world/explosion/ExplosionBehavior;Lnet/minecraft/util/math/Vec3d;FZLnet/minecraft/world/World$ExplosionSourceType;)V")
    private void createExplosion(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, Vec3d pos, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, Operation<Void> original) {
        synchronized (lock) {
            original.call(entity, damageSource, behavior, pos, power, createFire, explosionSourceType);
        }
    }
}