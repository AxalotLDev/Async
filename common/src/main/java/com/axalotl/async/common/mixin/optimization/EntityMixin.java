package com.axalotl.async.common.mixin.optimization;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    private Vec3 position;

    @Shadow
    public abstract Level level();

    @Shadow
    public Optional<BlockPos> mainSupportingBlockPos;

    @WrapMethod(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;")
    private BlockPos getOnPos(float yOffset, Operation<BlockPos> original) {
        if (this.mainSupportingBlockPos.isPresent()) {
            BlockPos blockpos = this.mainSupportingBlockPos.get();
            LevelChunk chunk = this.level().getChunkAt(blockpos);
            if (!(yOffset > 1.0E-5F)) {
                return blockpos;
            } else {
                BlockState blockState = chunk.getBlockState(blockpos);
                return (!((double) yOffset <= (double) 0.5F) || !blockState.is(BlockTags.FENCES)) && !blockState.is(BlockTags.WALLS) && !(blockState.getBlock() instanceof FenceGateBlock) ? blockpos.atY(Mth.floor(this.position.y - (double) yOffset)) : blockpos;
            }
        } else {
            int floor = Mth.floor(this.position.x);
            int floor1 = Mth.floor(this.position.y - yOffset);
            int floor2 = Mth.floor(this.position.z);
            return new BlockPos(floor, floor1, floor2);
        }
    }
}
