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
    private static final Object lock = new Object();
    @Unique
    private static final Map<BlockPos, AtomicBoolean> containerFlags = new ConcurrentHashMap<>();

    @WrapMethod(method = "pickUpItems")
    private void pickUpItems(PathfinderMob body, Container container, Operation<Void> original) {
        synchronized (lock) {
            original.call(body, container);
        }
    }

    @WrapMethod(method = "putDownItem")
    private void putDownItem(PathfinderMob body, Container container, Operation<Void> original) {
        synchronized (lock) {
            original.call(body, container);
        }
    }

    @WrapMethod(method = "isAnotherMobInteractingWithTarget")
    private boolean isAnotherMobInteractingWithTarget(
            TransportItemsBetweenContainers.TransportItemTarget target,
            Level level,
            Operation<Boolean> original
    ) {
        BlockPos pos = target.pos();
        AtomicBoolean flag = containerFlags.computeIfAbsent(pos, _ -> new AtomicBoolean(false));

        if (!flag.compareAndSet(false, true)) {
            return true;
        }

        try {
            return original.call(target, level);
        } finally {
            flag.set(false);
            containerFlags.remove(pos, flag);
        }
    }
}