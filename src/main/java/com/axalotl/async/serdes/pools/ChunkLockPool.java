package com.axalotl.async.serdes.pools;

import javax.annotation.Nullable;

import com.axalotl.async.parallelised.ChunkLock;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ChunkLockPool implements ISerDesPool {

    public static class CLPOptions implements ISerDesOptions {
        int range;

        public int getRange() {
            return range;
        }
    }

    ChunkLock cl = new ChunkLock();

    public ChunkLockPool() {

    }

    @Override
    public void serialise(Runnable task, Object o, BlockPos bp, World w, @Nullable ISerDesOptions options) {
        int range = 1;
        if (options instanceof CLPOptions) {
            range = ((CLPOptions) options).getRange();
        }
        long[] locks = cl.lock(bp, range);
        try {
            task.run();
        } finally {
            cl.unlock(locks);
        }
    }
}