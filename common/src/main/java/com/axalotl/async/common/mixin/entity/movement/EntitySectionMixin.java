package com.axalotl.async.common.mixin.entity.movement;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySection.class)
public class EntitySectionMixin<T extends EntityAccess> {

    @Shadow
    @Final
    private ClassInstanceMultiMap<T> storage;

    @Shadow
    @Mutable
    private Visibility chunkStatus;

    @Unique
    private final AtomicReference<Visibility> async$atomicStatus = new AtomicReference<>(Visibility.HIDDEN);

    @Unique
    private final Object async$storageLock = new Object();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void async$init(
        Class<?> clazz,
        Visibility status,
        CallbackInfo ci
    ) {
        async$atomicStatus.set(status != null ? status : Visibility.HIDDEN);
    }

    /**
     * @author FurryMileon
     * @reason Make add thread-safe with per-instance lock
     */
    @Overwrite
    public void add(T entity) {
        synchronized (async$storageLock) {
            this.storage.add(entity);
        }
    }

    /**
     * @author FurryMileon
     * @reason Make remove thread-safe with per-instance lock
     */
    @Overwrite
    public boolean remove(T entity) {
        synchronized (async$storageLock) {
            return this.storage.remove(entity);
        }
    }

    /**
     * @author FurryMileon
     * @reason Make getStatus thread-safe via atomic read
     */
    @Overwrite
    public Visibility getStatus() {
        return async$atomicStatus.get();
    }

    /**
     * @author FurryMileon
     * @reason Make updateChunkStatus thread-safe via atomic swap
     */
    @Overwrite
    public Visibility updateChunkStatus(Visibility status) {
        Visibility safeStatus = status != null ? status : Visibility.HIDDEN;
        Visibility old = async$atomicStatus.getAndSet(safeStatus);
        this.chunkStatus = safeStatus;
        return old != null ? old : Visibility.HIDDEN;
    }

    @WrapMethod(method = "getEntities()Ljava/util/stream/Stream;")
    private Stream<T> getEntities(Operation<Stream<T>> original) {
        return storage.stream().filter(Objects::nonNull);
    }
}
