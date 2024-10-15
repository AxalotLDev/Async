package com.axalotl.async.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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

import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Shadow private Optional<BlockPos> climbingPos;

    @Shadow protected abstract boolean canEnterTrapdoor(BlockPos pos, BlockState state);

    @ModifyExpressionValue(method = "isClimbing", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
    private boolean modifyIsInClimbable(boolean original) {
        try {
            if (this.isSpectator()) {
                return false;
            } else {
                BlockPos blockPos = this.getBlockPos();
                BlockState blockState = this.getBlockStateAtPos();
                if (blockState.isIn(BlockTags.CLIMBABLE)) {
                    this.climbingPos = Optional.of(blockPos);
                    return true;
                } else if (blockState.getBlock() instanceof TrapdoorBlock && this.canEnterTrapdoor(blockPos, blockState)) {
                    this.climbingPos = Optional.of(blockPos);
                    return true;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

//    /**
//     * @author AxalotL
//     * @reason Null check
//     */
//    @Overwrite
//    public boolean isClimbing() {
//        try {
//            if (this.isSpectator()) {
//                return false;
//            } else {
//                BlockPos blockPos = this.getBlockPos();
//                BlockState blockState = this.getBlockStateAtPos();
//                if (blockState.isIn(BlockTags.CLIMBABLE)) {
//                    this.climbingPos = Optional.of(blockPos);
//                    return true;
//                } else if (blockState.getBlock() instanceof TrapdoorBlock && this.canEnterTrapdoor(blockPos, blockState)) {
//                    this.climbingPos = Optional.of(blockPos);
//                    return true;
//                } else {
//                    return false;
//                }
//            }
//        } catch (Exception e) {
//            return false;
//        }
//    }
}
