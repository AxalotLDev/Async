package com.axalotl.async.common.mixin.server;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin implements AutoCloseable {
    @Shadow
    private final Set<UUID> knownUuids = ConcurrentHashMap.newKeySet();

    @WrapMethod(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V")
    private void updateChunkStatus(ChunkPos pos, Visibility chunkStatus, @NotNull Operation<Void> original) {
        synchronized (this) {
            original.call(pos, chunkStatus);
        }
    }

    @WrapMethod(method = "getEffectiveStatus")
    private static <T extends EntityAccess> Visibility getEffectiveStatus(T entity, Visibility status, Operation<Visibility> original) {
        Visibility result = original.call(entity, status);
        if (result == null) {
            return entity.isAlwaysTicking() ? Visibility.TICKING : Visibility.TRACKED;
        }
        return result;
    }
}
