package com.axalotl.async.common.parallelised.utils;

import net.minecraft.world.entity.Entity;

public final class FastBitRadixSort {
    private static final int SMALL_ARRAY_THRESHOLD = 6;

    private static final ThreadLocal<long[]>   BITS_BUFFER = ThreadLocal.withInitial(() -> new long[0]);
    private static final ThreadLocal<double[]> X_BUFFER    = ThreadLocal.withInitial(() -> new double[0]);
    private static final ThreadLocal<double[]> Y_BUFFER    = ThreadLocal.withInitial(() -> new double[0]);
    private static final ThreadLocal<double[]> Z_BUFFER    = ThreadLocal.withInitial(() -> new double[0]);

    public void sort(Object[] entities, int size, net.minecraft.core.Position target) {
        if (size <= 1) {
            return;
        }

        long[]   bits = ensure(BITS_BUFFER, size);
        double[] ex   = ensureD(X_BUFFER,   size);
        double[] ey   = ensureD(Y_BUFFER,   size);
        double[] ez   = ensureD(Z_BUFFER,   size);

        for (int i = 0; i < size; i++) {
            Entity e = (Entity) entities[i];
            ex[i] = e.getX();
            ey[i] = e.getY();
            ez[i] = e.getZ();
        }

        double tx = target.x();
        double ty = target.y();
        double tz = target.z();

        if (VectorOpsBootstrap.AVAILABLE) {
            VectorOps.batchDistanceSquaredBits(ex, ey, ez, tx, ty, tz, bits, size);
        } else {
            for (int i = 0; i < size; i++) {
                double dx = ex[i] - tx;
                double dy = ey[i] - ty;
                double dz = ez[i] - tz;
                bits[i] = Double.doubleToRawLongBits(dx * dx + dy * dy + dz * dz);
            }
        }

        fastRadixSort(entities, bits, 0, size - 1, 62);
    }

    private static long[] ensure(ThreadLocal<long[]> tl, int size) {
        long[] b = tl.get();
        if (b.length < size) { b = new long[size]; tl.set(b); }
        return b;
    }

    private static double[] ensureD(ThreadLocal<double[]> tl, int size) {
        double[] b = tl.get();
        if (b.length < size) { b = new double[size]; tl.set(b); }
        return b;
    }

    private static void fastRadixSort(Object[] ents, long[] bits, int low, int high, int bit) {
        if (bit < 0 || low >= high) {
            return;
        }
        if (high - low <= SMALL_ARRAY_THRESHOLD) {
            insertionSort(ents, bits, low, high);
            return;
        }

        int i = low;
        int j = high;
        final long mask = 1L << bit;

        while (i <= j) {
            while (i <= j && (bits[i] & mask) == 0) i++;
            while (i <= j && (bits[j] & mask) != 0) j--;
            if (i < j) swap(ents, bits, i++, j--);
        }

        if (low < j) fastRadixSort(ents, bits, low, j, bit - 1);
        if (i < high) fastRadixSort(ents, bits, i, high, bit - 1);
    }

    private static void insertionSort(Object[] ents, long[] bits, int low, int high) {
        for (int i = low + 1; i <= high; i++) {
            int j = i;
            Object currentEntity = ents[j];
            long currentBits = bits[j];
            while (j > low && bits[j - 1] > currentBits) {
                ents[j] = ents[j - 1];
                bits[j] = bits[j - 1];
                j--;
            }
            ents[j] = currentEntity;
            bits[j] = currentBits;
        }
    }

    private static void swap(Object[] ents, long[] bits, int a, int b) {
        Object tempEntity = ents[a];
        ents[a] = ents[b];
        ents[b] = tempEntity;
        long tempBits = bits[a];
        bits[a] = bits[b];
        bits[b] = tempBits;
    }
}