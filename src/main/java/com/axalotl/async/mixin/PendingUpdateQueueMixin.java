package com.axalotl.async.mixin;

import com.axalotl.async.parallelised.fastutil.ConcurrentLongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.world.chunk.light.PendingUpdateQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PendingUpdateQueue.class)
public class PendingUpdateQueueMixin {
    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/light/PendingUpdateQueue;pendingIdUpdatesByLevel:[Lit/unimi/dsi/fastutil/longs/LongLinkedOpenHashSet;", args = "array=set"))
    private void overwritePendingIdUpdatesByLevel(LongLinkedOpenHashSet[] hashSets, int index, LongLinkedOpenHashSet hashSet, int levelCount, final int expectedLevelSize) {
        hashSets[index] = new ConcurrentLongLinkedOpenHashSet(expectedLevelSize, 0.5f) {
            @Override
            protected void rehash(int newN) {
                if (newN > expectedLevelSize) {
                    super.rehash(newN);
                }
            }
        };
    }
}
