package com.axalotl.async.common.mixin.lithium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.caffeinemc.mods.lithium.common.world.GameEventDispatcherStorage;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = GameEventDispatcherStorage.class, remap = false)
public class LithiumGameEventDispatcherStorage {

    @Unique
    private final Object lock = new Object();

    @WrapMethod(method = "addChunk")
    private void addChunk(long pos, Int2ObjectMap<GameEventListenerRegistry> dispatchers, Operation<Void> original) {
        synchronized (lock) {
            original.call(pos, dispatchers);
        }
    }

    @WrapMethod(method = "removeChunk")
    private void removeChunk(long pos, Operation<Void> original) {
        synchronized (lock) {
            original.call(pos);
        }
    }

    @WrapMethod(method = "replace")
    private void replace(long pos, Int2ObjectMap<GameEventListenerRegistry> dispatchers, Operation<Void> original) {
        synchronized (lock) {
            original.call(pos, dispatchers);
        }
    }

    @WrapMethod(method = "get")
    private Int2ObjectMap<GameEventListenerRegistry> get(long pos, Operation<Int2ObjectMap<GameEventListenerRegistry>> original) {
        synchronized (lock) {
            return original.call(pos);
        }
    }
}