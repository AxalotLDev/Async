package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract BlockPos blockPosition();

    @WrapMethod(method = "setRemoved")
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (this) {
            original.call(reason);
        }
    }

    @WrapMethod(method = "getInBlockState")
    private BlockState wrapGetInBlockState(Operation<BlockState> original) {
        BlockState state = original.call();
        if (state != null) {
            return state;
        }
        state = original.call();
        return state != null ? state : getBlockStateNow();
    }

    @Unique
    private BlockState getBlockStateNow() {
        Level level = this.level();
        BlockPos pos = this.blockPosition();
        if (!level.isInValidBounds(pos)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        if (level instanceof ServerLevel serverLevel) {
            ChunkAccess chunk = serverLevel.getChunkSource()
                    .getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null) {
                return chunk.getBlockState(pos);
            }
        }
        return Blocks.AIR.defaultBlockState();
    }


    @WrapMethod(method = "addPassenger")
    private void addPassenger(Entity passenger, Operation<Void> original) {
        synchronized (this) {
            original.call(passenger);
        }
    }

    @WrapMethod(method = "removePassenger")
    private void removePassenger(Entity passenger, Operation<Void> original) {
        synchronized (this) {
            original.call(passenger);
        }
    }

    @WrapMethod(method = "ejectPassengers")
    private void ejectPassengers(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }

    @WrapMethod(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z")
    private boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(entityToRide, force, sendEventAndTriggers);
        }
    }

    @WrapMethod(method = "removeVehicle")
    private void removeVehicle(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }
}