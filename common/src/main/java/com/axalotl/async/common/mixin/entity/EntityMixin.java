package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(Entity.class)
public abstract class EntityMixin implements ParallelProcessor.TickGuard {

    @Shadow
    public abstract Level level();

    @Unique
    private static final Object lock = new Object();

    @Unique
    private final AtomicBoolean async$tickGuard = new AtomicBoolean(false);

    @Override
    public boolean async$tryBeginTick() {
        return async$tickGuard.compareAndSet(false, true);
    }

    @Override
    public void async$endTick() {
        async$tickGuard.set(false);
    }

    @Unique
    private byte async$syncCache = -1;

    @Override
    public byte async$getSyncCache() { return async$syncCache; }

    @Override
    public void async$setSyncCache(byte v) { async$syncCache = v; }

    @WrapMethod(method = "setRemoved")
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (lock) {
            original.call(reason);
        }
    }

    @WrapMethod(method = "getInBlockState")
    private BlockState wrapGetInBlockState(Operation<BlockState> original) {
        BlockState blockState;
        try {
            blockState = original.call();
        } catch (IllegalStateException ise) {
            String msg = ise.getMessage();
            if (msg != null && msg.startsWith("Should always be able to create a chunk")) {
                return Blocks.AIR.defaultBlockState();
            }
            throw ise;
        }
        return blockState != null ? blockState : Blocks.AIR.defaultBlockState();
    }


    @WrapMethod(method = "addPassenger")
    private void addPassenger(Entity passenger, Operation<Void> original) {
        synchronized (lock) {
            original.call(passenger);
        }
    }

    @WrapMethod(method = "removePassenger")
    private void removePassenger(Entity passenger, Operation<Void> original) {
        synchronized (lock) {
            original.call(passenger);
        }
    }

    @WrapMethod(method = "ejectPassengers")
    private void ejectPassengers(Operation<Void> original) {
        synchronized (lock) {
            original.call();
        }
    }

    @WrapMethod(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z")
    private boolean startRiding(Entity entityToRide, boolean force, boolean sendEventAndTriggers, Operation<Boolean> original) {
        synchronized (lock) {
            return original.call(entityToRide, force, sendEventAndTriggers);
        }
    }

    @WrapMethod(method = "removeVehicle")
    private void removeVehicle(Operation<Void> original) {
        synchronized (lock) {
            original.call();
        }
    }
}