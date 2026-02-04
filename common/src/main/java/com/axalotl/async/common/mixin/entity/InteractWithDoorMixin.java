package com.axalotl.async.common.mixin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InteractWithDoor.class)
public class InteractWithDoorMixin {

    @Inject(
            method = "isMobComingThroughDoor",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void injectIsMobComingThroughDoor(
            Brain<?> brain,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Path path = brain.getMemory(MemoryModuleType.PATH).orElse(null);

        if (path == null || path.isDone()) {
            cir.setReturnValue(false);
            return;
        }

        Node prev = path.getPreviousNode();
        Node next = path.getNextNode();

        cir.setReturnValue(
                prev != null && pos.equals(prev.asBlockPos()) ||
                        pos.equals(next.asBlockPos())
        );
    }
}
