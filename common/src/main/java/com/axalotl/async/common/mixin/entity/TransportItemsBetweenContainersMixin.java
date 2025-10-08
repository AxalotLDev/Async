package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(TransportItemsBetweenContainers.class)
public class TransportItemsBetweenContainersMixin {

    @Unique
    private static final Object async$lock = new Object();
    @Unique
    private static final Map<BlockPos, AtomicBoolean> async$containerFlags = new ConcurrentHashMap<>();

    @WrapMethod(method = "pickUpItems")
    private void pickUpItems(PathfinderMob mob, Container counter, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(mob, counter);
        }
    }

    @WrapMethod(method = "putDownItem")
    private void putDownItem(PathfinderMob mob, Container counter, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(mob, counter);
        }
    }

    @WrapMethod(method = "isAnotherMobInteractingWithTarget")
    private boolean async$isAnotherMobInteractingWithTarget(
            TransportItemsBetweenContainers.TransportItemTarget target,
            Level level,
            Operation<Boolean> original
    ) {
        BlockPos pos = target.pos();
        AtomicBoolean flag = async$containerFlags.computeIfAbsent(pos, p -> new AtomicBoolean(false));

        if (!flag.compareAndSet(false, true)) {
            return true;
        }

        try {
            return original.call(target, level);
        } finally {
            flag.set(false);
            async$containerFlags.remove(pos, flag);
        }
    }
}