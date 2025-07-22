package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Mixin(Raid.class)
public class RaidMixin {

    @Unique
    private static final Object async$lock = new Object();

    @Shadow
    private final Map<Integer, Set<Raider>> groupRaiderMap = ConcurrentCollections.newHashMap();

    @WrapMethod(method = "addWaveMob(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/entity/raid/Raider;Z)Z")
    private boolean addWaveMob(ServerLevel level, int wave, Raider p_raider, boolean isRecruited, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(level, wave, p_raider, isRecruited);
        }
    }

    @WrapMethod(method = "addWaveMob(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/entity/raid/Raider;)Z")
    private boolean addWaveMob(ServerLevel level, int wave, Raider raider, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(level, wave, raider);
        }
    }

    @Redirect(method = "addWaveMob(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/entity/raid/Raider;Z)Z", at =
    @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object redirectComputeIfAbsent(Map<Integer, Set<Raider>> instance, Object k, Function<?, ?> key) {
        return instance.computeIfAbsent((Integer) k, wave -> ConcurrentCollections.newHashSet());
    }
}