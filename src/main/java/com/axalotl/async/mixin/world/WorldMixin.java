package com.axalotl.async.mixin.world;

import com.axalotl.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(value = Level.class, priority = 1500)
public abstract class WorldMixin implements LevelAccessor, AutoCloseable {
    @Shadow
    @Final
    private Thread thread;

    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @Shadow @Final protected WritableLevelData levelData;

    @Shadow public abstract DimensionType dimensionType();

    @Shadow @Final private ResourceKey<Level> dimension;

    @Shadow
    @Final
    @Mutable
    protected List<TickingBlockEntity> blockEntityTickers;

    @Shadow
    @Final
    @Mutable
    private List<TickingBlockEntity> pendingBlockEntityTickers;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        blockEntityTickers = Collections.synchronizedList(new ArrayList<>());
        pendingBlockEntityTickers = Collections.synchronizedList(new ArrayList<>());
    }

    @Inject(method = "tickBlockEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private void postEntityPreBlockEntityTick(CallbackInfo ci) {
        if ((Object) this instanceof ServerLevel) {
            ParallelProcessor.postEntityTick();
        }
    }

    @Redirect(method = "getBlockEntity", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"))
    private Thread overwriteCurrentThread() {
        return this.thread;
    }

    @WrapMethod(method = "explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;")
    private Explosion createExplosion(Entity entity, double x, double y, double z, float power, Level.ExplosionInteraction explosionSourceType, Operation<Explosion> original) {
        synchronized (lock) {
            return original.call(entity, x, y, z, power, explosionSourceType);
        }
    }

    @WrapMethod(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;")
    private Explosion createExplosion(Entity entity, DamageSource damageSource, ExplosionDamageCalculator behavior, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType, Operation<Explosion> original) {
        synchronized (lock) {
            return original.call(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType);
        }
    }

    @WrapMethod(method = "explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;")
    private Explosion createExplosion(Entity entity, double x, double y, double z, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType, Operation<Explosion> original) {
        synchronized (lock) {
            return original.call(entity, x, y, z, power, createFire, explosionSourceType);
        }
    }

    @WrapMethod(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;Lnet/minecraft/world/phys/Vec3;FZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;")
    private Explosion createExplosion(Entity entity, DamageSource damageSource, ExplosionDamageCalculator behavior, Vec3 pos, float power, boolean createFire, Level.ExplosionInteraction explosionSourceType, Operation<Explosion> original) {
        synchronized (lock) {
            return original.call(entity, damageSource, behavior, pos, power, createFire, explosionSourceType);
        }
    }
}