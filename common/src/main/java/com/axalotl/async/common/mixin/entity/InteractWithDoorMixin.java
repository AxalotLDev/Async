package com.axalotl.async.common.mixin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(InteractWithDoor.class)
public class InteractWithDoorMixin {

    /**
     * @author FurryMileon
     * @reason Fix null path/node in door check for async AI
     */
    @Overwrite
    private static boolean isMobComingThroughDoor(Brain<?> brain, BlockPos pos) {
        Path path = brain.getMemory(MemoryModuleType.PATH).orElse(null);
        if (path == null || path.isDone()) {
            return false;
        }

        Node prevNode = path.getPreviousNode();
        if (prevNode == null) {
            return false;
        }

        Node nextNode = path.getNextNode();
        return pos.equals(prevNode.asBlockPos()) || pos.equals(nextNode.asBlockPos());
    }
}