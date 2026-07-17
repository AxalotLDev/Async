package com.axalotl.async.common.mixin.entity.spawn;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(NaturalSpawner.SpawnState.class)
public class SpawnStateMixin {

    @WrapMethod(method = "canSpawn")
    private boolean canSpawn(EntityType<?> type, BlockPos testPos, ChunkAccess chunk, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(type, testPos, chunk);
        }
    }

    @WrapMethod(method = "afterSpawn")
    private void afterSpawn(Mob mob, ChunkAccess chunk, Operation<Void> original) {
        synchronized (this) {
            original.call(mob, chunk);
        }
    }

    @WrapMethod(method = "canSpawnForCategoryGlobal")
    private boolean canSpawnForCategoryGlobal(MobCategory mobCategory, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(mobCategory);
        }
    }
}