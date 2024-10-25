package com.axalotl.async.mixin.world;

import com.axalotl.async.parallelised.fastutil.Long2ByteConcurrentHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import net.minecraft.world.chunk.light.LevelPropagator;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelPropagator.class)
public abstract class LevelPropagatorMixin {

    @Final
    @Shadow
    @Mutable
    private Long2ByteMap pendingUpdates;


    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/light/LevelPropagator;pendingUpdates:Lit/unimi/dsi/fastutil/longs/Long2ByteMap;", opcode = Opcodes.PUTFIELD))
    private void overwritePendingUpdates(LevelPropagator instance, Long2ByteMap value, int levelCount, final int expectedLevelSize, final int expectedTotalSize) {
        pendingUpdates = new Long2ByteConcurrentHashMap(expectedTotalSize, 0.5f);
    }
}
