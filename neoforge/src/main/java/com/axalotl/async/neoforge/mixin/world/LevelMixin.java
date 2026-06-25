package com.axalotl.async.neoforge.mixin.world;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;

@Mixin(Level.class)
public class LevelMixin {

    @Shadow
    public ArrayList<BlockSnapshot> capturedBlockSnapshots;

    @Redirect(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;add(Ljava/lang/Object;)Z", remap = false))
    private synchronized boolean overwriteAdd(ArrayList<BlockSnapshot> instance, Object object) {
        return capturedBlockSnapshots.add((BlockSnapshot) object);
    }

    @Redirect(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;remove(Ljava/lang/Object;)Z", remap = false))
    private synchronized boolean overwriteRemove(ArrayList<BlockSnapshot> instance, Object object) {
        return capturedBlockSnapshots.remove((BlockSnapshot) object);
    }
}
