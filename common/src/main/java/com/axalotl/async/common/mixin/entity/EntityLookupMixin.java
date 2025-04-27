package com.axalotl.async.common.mixin.entity;

import com.axalotl.async.common.parallelised.ConcurrentCollections;
import com.axalotl.async.common.parallelised.fastutil.Int2ObjectConcurrentHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
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
    private Int2ObjectMap<T> byId;

    @Shadow
    @Final
    @Mutable
    private Map<UUID, T> byUuid;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void replaceConVars(CallbackInfo ci) {
        byId = new Int2ObjectConcurrentHashMap<>();
        byUuid = ConcurrentCollections.newHashMap();
    }
}