package com.axalotl.async.neoforge.mixin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = CommonHooks.class, priority = 1001)
public class CommonHooksMixin {

    @Inject(method = "isLivingOnLadder", at = @At("HEAD"), cancellable = true)
    private static void isLivingOnLadder(BlockState state, Level level, BlockPos pos, LivingEntity entity, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (state == null) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
