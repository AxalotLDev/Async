package com.axalotl.async.common.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Bee.class)
public abstract class BeeMixin extends Animal implements NeutralMob, FlyingAnimal {

    @Unique
    private static final ReentrantLock async$lock = new ReentrantLock();

    protected BeeMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @WrapMethod(method = "wantsToEnterHive")
    private boolean loot(Operation<Boolean> original) {
        synchronized (async$lock) {
            return original.call();
        }
    }
}