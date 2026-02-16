package com.axalotl.async.common.mixin.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    private ImmutableList<Entity> passengers;

    @Shadow
    public abstract Level level();

    @Shadow
    private @Nullable BlockState inBlockState;

    @Shadow
    public abstract BlockPos blockPosition();

    @Unique
    private final AtomicReference<ImmutableList<Entity>> async$passengersRef = new AtomicReference<>(ImmutableList.of());

    @WrapMethod(method = "addPassenger")
    private void addPassenger(Entity passenger, Operation<Void> original) {
        if (passenger.getVehicle() != (Object) this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        }

        while (true) {
            ImmutableList<Entity> current = async$passengersRef.get();
            ImmutableList<Entity> updated;

            if (current.isEmpty()) {
                updated = ImmutableList.of(passenger);
            } else {
                List<Entity> temp = Lists.newArrayList(current);
                if (!this.level().isClientSide()
                        && passenger instanceof Player
                        && !(current.getFirst() instanceof Player)) {
                    temp.addFirst(passenger);
                } else {
                    temp.add(passenger);
                }
                updated = ImmutableList.copyOf(temp);
            }

            if (async$passengersRef.compareAndSet(current, updated)) {
                this.passengers = updated;
                return;
            }
        }
    }

    @WrapMethod(method = "removePassenger")
    private void removePassenger(Entity passenger, Operation<Void> original) {
        if (passenger.getVehicle() == (Object) this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        }

        while (true) {
            ImmutableList<Entity> current = async$passengersRef.get();
            ImmutableList<Entity> updated;

            if (current.size() == 1 && current.getFirst() == passenger) {
                updated = ImmutableList.of();
            } else {
                updated = current.stream()
                        .filter(e -> e != passenger)
                        .collect(ImmutableList.toImmutableList());
            }

            if (async$passengersRef.compareAndSet(current, updated)) {
                this.passengers = updated;
                passenger.boardingCooldown = 60;
                return;
            }
        }
    }

    @WrapMethod(method = "ejectPassengers")
    private void ejectPassengers(Operation<Void> original) {
        ImmutableList<Entity> snapshot = async$passengersRef.getAndSet(ImmutableList.of());
        this.passengers = ImmutableList.of();

        for (int i = snapshot.size() - 1; i >= 0; --i) {
            snapshot.get(i).stopRiding();
        }
    }

    @WrapMethod(method = "getPassengers")
    private List<Entity> getPassengers(Operation<List<Entity>> original) {
        return async$passengersRef.get();
    }

    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "setRemoved")
    private void setRemoved(Entity.RemovalReason reason, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(reason);
        }
    }

    @WrapMethod(method = "getInBlockState")
    private BlockState wrapGetInBlockState(Operation<BlockState> original) {
        BlockState state = this.inBlockState;
        if (state != null) {
            return state;
        }
        state = this.level().getBlockState(this.blockPosition());
        this.inBlockState = state;
        return state;
    }
}