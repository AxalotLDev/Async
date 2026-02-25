package com.axalotl.async.common.parallelised.utils;

import java.util.concurrent.atomic.AtomicLong;

public final class AsyncRandomState {

    private static final long MULTIPLIER = 25214903917L;
    private static final long MODULUS_MASK = 281474976710655L;

    private static final AtomicLong VERSION = new AtomicLong(1);

    // Per-thread: [0] = local seed, [1] = last seen version
    private static final ThreadLocal<long[]> THREAD_STATE = ThreadLocal.withInitial(() -> new long[2]);

    private AsyncRandomState() {}

    public static void bumpVersion() {
        VERSION.incrementAndGet();
    }

    public static int next(AtomicLong masterSeed, int bits) {
        long[] state = THREAD_STATE.get();
        long currentVersion = VERSION.get();

        if (state[1] != currentVersion) {
            long master = masterSeed.get();
            state[0] = (master ^ Thread.currentThread().threadId()) & MODULUS_MASK;
            state[1] = currentVersion;
        }

        long j = (state[0] * MULTIPLIER + 11L) & MODULUS_MASK;
        state[0] = j;
        return (int) (j >>> (48 - bits));
    }
}