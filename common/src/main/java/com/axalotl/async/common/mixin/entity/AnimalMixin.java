package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(Animal.class)
public abstract class AnimalMixin extends Entity {

    @Unique
    private final AtomicBoolean async$breedingFlag = new AtomicBoolean(false);
    @Unique
    private final AtomicBoolean async$breedingBabyFlag = new AtomicBoolean(false);

    public AnimalMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @WrapMethod(method = "spawnChildFromBreeding")
    private void breed(ServerLevel world, Animal other, Operation<Void> original) {
        if (this.getId() > other.getId()) return;
        AnimalMixin otherMixin = (AnimalMixin) (Object) other;
        if (this.async$breedingFlag.compareAndSet(false, true) && otherMixin.async$breedingFlag.compareAndSet(false, true)) {
            try {
                original.call(world, other);
            } finally {
                this.async$breedingFlag.set(false);
                otherMixin.async$breedingFlag.set(false);
            }
        }
    }

    @WrapMethod(method = "finalizeSpawnChildFromBreeding")
    private void breed(ServerLevel world, Animal other, AgeableMob baby, Operation<Void> original) {
        if (this.getId() > other.getId()) return;
        AnimalMixin otherMixin = (AnimalMixin) (Object) other;
        if (this.async$breedingBabyFlag.compareAndSet(false, true) && otherMixin.async$breedingBabyFlag.compareAndSet(false, true)) {
            try {
                original.call(world, other, baby);
            } finally {
                this.async$breedingBabyFlag.set(false);
                otherMixin.async$breedingBabyFlag.set(false);
            }
        }
    }
}