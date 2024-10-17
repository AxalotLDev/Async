package com.axalotl.async.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow
    private Optional<BlockPos> climbingPos;

    @Shadow
    protected abstract boolean canEnterTrapdoor(BlockPos pos, BlockState state);

    @Inject(method = "isClimbing", at = @At(value = "HEAD"), cancellable = true)
    private void modifyIsInClimbable(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (this.isSpectator()) {
                cir.setReturnValue(false);
            } else {
                BlockPos blockPos = this.getBlockPos();
                BlockState blockState = this.getBlockStateAtPos();
                if (blockState.isIn(BlockTags.CLIMBABLE)) {
                    this.climbingPos = Optional.of(blockPos);
                    cir.setReturnValue(true);
                } else if (blockState.getBlock() instanceof TrapdoorBlock && this.canEnterTrapdoor(blockPos, blockState)) {
                    this.climbingPos = Optional.of(blockPos);
                    cir.setReturnValue(true);
                } else {
                    cir.setReturnValue(false);
                }
            }
        } catch (Exception e) {
            cir.setReturnValue(false);
        }
    }
}
