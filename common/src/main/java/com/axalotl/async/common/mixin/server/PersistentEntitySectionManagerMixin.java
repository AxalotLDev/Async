package com.axalotl.async.common.mixin.server;

import com.axalotl.async.api.fastutil.Long2ObjectConcurrentHashMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.jspecify.annotations.NonNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin implements AutoCloseable {
    @Shadow
    private final Set<UUID> knownUuids = ConcurrentHashMap.newKeySet();

    @Shadow
    @Final
    @Mutable
    private Long2ObjectMap<Visibility> chunkVisibility;

    @Shadow
    @Final
    @Mutable
    @SuppressWarnings("rawtypes")
    private Long2ObjectMap chunkLoadStatuses;

    @Unique
    @SuppressWarnings("rawtypes")
    private Long2ObjectConcurrentHashMap concurrentLoadStatuses;

    @Unique
    private Object freshStatus;

    @Unique
    private Object pendingStatus;

    @ModifyExpressionValue(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;chunkVisibility:Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;"
            )
    )
    private Long2ObjectMap<Visibility> useConcurrentChunkVisibility(Long2ObjectMap<Visibility> original) {
        if (original instanceof Long2ObjectConcurrentHashMap) {
            return original;
        }

        Long2ObjectConcurrentHashMap<Visibility> replacement = new Long2ObjectConcurrentHashMap<>();
        replacement.defaultReturnValue(original.defaultReturnValue());
        replacement.putAll(original);
        this.chunkVisibility = replacement;
        return replacement;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void useConcurrentLoadStatuses(CallbackInfo ci) {
        this.freshStatus = this.chunkLoadStatuses.defaultReturnValue();
        if (this.freshStatus instanceof Enum<?> fresh) {
            Object[] constants = fresh.getDeclaringClass().getEnumConstants();
            if (constants.length > 1) this.pendingStatus = constants[1];
        }

        Long2ObjectConcurrentHashMap replacement = new Long2ObjectConcurrentHashMap();
        replacement.defaultReturnValue(this.freshStatus);
        replacement.putAll(this.chunkLoadStatuses);
        this.concurrentLoadStatuses = replacement;
        this.chunkLoadStatuses = replacement;
    }

    @WrapMethod(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V")
    private void updateChunkStatus(ChunkPos pos, Visibility chunkStatus, @NonNull Operation<Void> original) {
        synchronized (this) {
            original.call(pos, chunkStatus);
        }
    }

    @WrapMethod(method = "requestChunkLoad(J)V")
    @SuppressWarnings("unchecked")
    private void requestChunkLoad(long chunkKey, @NonNull Operation<Void> original) {
        if (pendingStatus == null) {
            original.call(chunkKey);
            return;
        }
        Object previous = concurrentLoadStatuses.putIfAbsent(chunkKey, pendingStatus);
        if (previous == freshStatus) {
            original.call(chunkKey);
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