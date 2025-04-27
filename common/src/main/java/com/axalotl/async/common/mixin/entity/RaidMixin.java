package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Mixin(Raid.class)
public class RaidMixin {

    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    @Shadow
    private final Map<Integer, Set<Raider>> groupRaiderMap = ConcurrentCollections.newHashMap();

    @WrapMethod(method = "addWaveMob(ILnet/minecraft/world/entity/raid/Raider;)Z")
    private boolean addWaveMob(int wave, Raider entity, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(wave, entity);
        }
    }

    @WrapMethod(method = "addWaveMob(ILnet/minecraft/world/entity/raid/Raider;Z)Z")
    private boolean addWaveMob(int wave, Raider entity, boolean countHealth, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(wave, entity, countHealth);
        }
    }

    @Redirect(method = "addWaveMob(ILnet/minecraft/world/entity/raid/Raider;Z)Z", at =
    @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object redirectComputeIfAbsent(Map<Integer, Set<Raider>> instance, Object k, Function<?, ?> key) {
        return instance.computeIfAbsent((Integer) k, wave -> ConcurrentCollections.newHashSet());
    }
}