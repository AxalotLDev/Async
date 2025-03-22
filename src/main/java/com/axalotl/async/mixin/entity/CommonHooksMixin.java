package com.axalotl.async.mixin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = ForgeHooks.class, priority = 1001)
public class CommonHooksMixin {

    @Inject(method = "isLivingOnLadder", at = @At("HEAD"), cancellable = true, remap = false)
    private static void isLivingOnLadder(BlockState state, Level level, BlockPos pos, LivingEntity entity, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (state == null) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
