package com.axalotl.async.common.parallelised.utils;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * SIMD batch kernels for entity hot paths.
 * <p>
 * <b>Do not reference this class directly.</b> Always gate calls through
 * {@link VectorOpsBootstrap#AVAILABLE}. Loading this class when the
 * {@code jdk.incubator.vector} module is not resolved triggers
 * {@link NoClassDefFoundError} at link time.
 */
public final class VectorOps {

    private static final VectorSpecies<Double> SP  = DoubleVector.SPECIES_PREFERRED;
    private static final int                   LEN = SP.length();

    private VectorOps() {}

    /**
     * For each lane {@code i}, compute {@code (ex[i]-tx)^2 + (ey[i]-ty)^2 + (ez[i]-tz)^2}
     * and store the raw IEEE-754 bit pattern of the double result in
     * {@code outBits[i]}. Ordering of the resulting longs matches ordering
     * of the squared distances for non-negative inputs, which is all that
     * {@link FastBitRadixSort} requires.
     * <p>
     * The tail past {@code SP.loopBound(n)} is processed with a scalar loop
     * that is bit-for-bit identical to the non-vector path, keeping sort
     * results deterministic regardless of which implementation runs.
     */
    public static void batchDistanceSquaredBits(
            double[] ex, double[] ey, double[] ez,
            double tx, double ty, double tz,
            long[] outBits, int n) {

        DoubleVector vtx = DoubleVector.broadcast(SP, tx);
        DoubleVector vty = DoubleVector.broadcast(SP, ty);
        DoubleVector vtz = DoubleVector.broadcast(SP, tz);

        int upper = SP.loopBound(n);
        int i = 0;
        for (; i < upper; i += LEN) {
            DoubleVector dx = DoubleVector.fromArray(SP, ex, i).sub(vtx);
            DoubleVector dy = DoubleVector.fromArray(SP, ey, i).sub(vty);
            DoubleVector dz = DoubleVector.fromArray(SP, ez, i).sub(vtz);
            DoubleVector distSq = dx.mul(dx).add(dy.mul(dy)).add(dz.mul(dz));
            distSq.reinterpretAsLongs().intoArray(outBits, i);
        }
        for (; i < n; i++) {
            double dx = ex[i] - tx;
            double dy = ey[i] - ty;
            double dz = ez[i] - tz;
            outBits[i] = Double.doubleToRawLongBits(dx * dx + dy * dy + dz * dz);
        }
    }
}
