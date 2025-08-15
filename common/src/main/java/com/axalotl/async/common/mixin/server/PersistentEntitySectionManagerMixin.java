package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin implements AutoCloseable {
    @Unique
    private static final Object async$lock = new Object();

    @WrapMethod(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V")
    private void updateChunkStatus(ChunkPos pos, Visibility p_visibility, Operation<Void> original) {
        synchronized (async$lock) {
            original.call(pos, p_visibility);
        }
    }

    @WrapMethod(method = "getEffectiveStatus")
    private static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility visibility, Operation<Visibility> original) {
        Visibility result = original.call(entity, visibility);
        return result != null ? result : Visibility.HIDDEN;
    }
}