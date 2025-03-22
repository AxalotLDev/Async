package com.axalotl.async.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.locks.ReentrantLock;

@Mixin(Villager.class)
public class VillagerEntityMixin {
    @Unique
    private static final ReentrantLock lock = new ReentrantLock();

    @WrapMethod(method = "pickUpItem")
    private void pickUpItem(ItemEntity itemEntity, Operation<Void> original) {
        synchronized (lock) {
            if (!itemEntity.isRemoved()) {
                original.call(itemEntity);
            }
        }
    }

    @WrapMethod(method = "spawnGolemIfNeeded")
    private void spawnGolemIfNeeded(ServerLevel world, long time, int requiredCount, Operation<Void> original) {
        synchronized (lock) {
            original.call(world, time, requiredCount);
        }
    }
}
