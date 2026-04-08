package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.api.fastutil.Int2ObjectConcurrentHashMap;
import com.axalotl.async.api.utils.ConcurrentCollections;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(EntityLookup.class)
public abstract class EntityLookupMixin<T extends EntityAccess> {

    @Shadow
    @Final
    @Mutable
    private Map<UUID, T> byUuid;

    @Shadow
    @Final
    @Mutable
    private Int2ObjectMap<T> byId;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci) {
        byId = new Int2ObjectConcurrentHashMap<>();
        byUuid = ConcurrentCollections.newHashMap();
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void threadSafeAdd(T entity, CallbackInfo ci) {
        ci.cancel();

        UUID uuid = entity.getUUID();
        int id = entity.getId();

        byUuid.compute(uuid, (_, existing) -> {
            if (existing == null) {
                byId.put(id, entity);
                return entity;
            } else if (existing.getId() == id) {
                byId.put(id, entity);
                return entity;
            } else {
                LOGGER.warn("Duplicate entity UUID {}: existing={}, new={}", uuid, existing, entity);
                return existing;
            }
        });
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void threadSafeRemove(T entity, CallbackInfo ci) {
        ci.cancel();

        UUID uuid = entity.getUUID();
        int id = entity.getId();

        byUuid.computeIfPresent(uuid, (_, existing) -> {
            if (existing.getId() == id) {
                byId.remove(id);
                return null;
            }
            return existing;
        });
    }

    @WrapMethod(method = "getEntity(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;")
    private T getEntity(UUID id, Operation<T> original) {
        return id == null ? null : original.call(id);
    }

    @WrapMethod(method = "getEntity(I)Lnet/minecraft/world/level/entity/EntityAccess;")
    private T getEntity1(int id, Operation<T> original) {
        return id == 0 ? null : original.call(id);
    }
}