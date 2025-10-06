package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.coppergolem.CopperGolem;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CopperGolem.class)
public class CopperGolemMixin {
    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "hasContainerOpen")
    private boolean hasContainerOpen(ContainerOpenersCounter counter, BlockPos pos, Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call(counter, pos);
        }
    }

    @WrapMethod(method = "setOpenedChestPos")
    private void setOpenedChestPos(BlockPos pos, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(pos);
        }
    }

    @WrapMethod(method = "clearOpenedChestPos")
    private void clearOpenedChestPos(Operation<Void> original) {
        synchronized (async$lock) {
            original.call();
        }
    }
}