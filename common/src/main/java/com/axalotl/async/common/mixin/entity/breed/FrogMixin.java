package com.axalotl.async.common.mixin.entity.breed;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(Frog.class)
public abstract class FrogMixin extends Animal {

    @Unique
    private final AtomicBoolean async$breedingFlag = new AtomicBoolean(false);

    protected FrogMixin(EntityType<? extends Animal> entityType, Level world) {
        super(entityType, world);
    }

    @WrapMethod(method = "spawnChildFromBreeding")
    private void breed(ServerLevel world, Animal other, Operation<Void> original) {
        if (this.getId() > other.getId()) return;
        FrogMixin otherMixin = (FrogMixin) other;
        if (this.async$breedingFlag.compareAndSet(false, true) && otherMixin.async$breedingFlag.compareAndSet(false, true)) {
            try {
                original.call(world, other);
            } finally {
                this.async$breedingFlag.set(false);
                otherMixin.async$breedingFlag.set(false);
            }
        }
    }
}