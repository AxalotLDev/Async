package com.axalotl.async.common.mixin.entity.sensor;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Sensor.class)
public abstract class SensorMixin<E extends LivingEntity> {

    @Shadow
    protected abstract void doTick(ServerLevel level, E body);

    @Unique
    private final Object lock = new Object();

    @WrapMethod(method = "tick")
    private void tick(ServerLevel level, E body, Operation<Void> original) {
        synchronized (lock) {
            this.doTick(level, body);
        }
    }
}