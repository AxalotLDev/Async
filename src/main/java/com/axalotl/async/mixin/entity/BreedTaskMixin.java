package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.ai.brain.task.BreedTask;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(BreedTask.class)
public class BreedTaskMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "run(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/passive/AnimalEntity;J)V")
    private void run(ServerWorld serverWorld, AnimalEntity animalEntity, long l, Operation<Void> original) {
        synchronized (lock) {
            original.call(serverWorld, animalEntity, l);
        }
    }
}
