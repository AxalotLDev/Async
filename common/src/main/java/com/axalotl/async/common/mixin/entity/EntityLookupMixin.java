package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.axalotl.async.common.parallelised.fastutil.Int2ObjectConcurrentHashMap;
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
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci) {
        byId = new Int2ObjectConcurrentHashMap<>();
        byUuid = ConcurrentCollections.newHashMap();
    }

    //TODO fix duplicate entity UUID
    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
    private void duplicateEntityUUIDDisable(Logger instance, String s, Object uuid, Object entity) {
    }

    @WrapMethod(method = "getEntity(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;")
    private T getEntity(UUID uuid, Operation<T> original) {
        return uuid == null ? null : original.call(uuid);
    }

    @WrapMethod(method = "getEntity(I)Lnet/minecraft/world/level/entity/EntityAccess;")
    private T getEntity1(int id, Operation<T> original) {
        return id == 0 ? null : original.call(id);
    }
}