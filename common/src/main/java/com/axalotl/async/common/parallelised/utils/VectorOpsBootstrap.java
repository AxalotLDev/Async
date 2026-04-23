package com.axalotl.async.common.parallelised.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-shot probe for the {@code jdk.incubator.vector} module.
 * <p>
 * {@link #AVAILABLE} is {@code true} when the Vector API classes can be
 * loaded and linked by the current classloader. This requires either
 * {@code --add-modules=jdk.incubator.vector} on the launch command line
 * or a distribution that resolves the module by default.
 * <p>
 * When {@code true}, callers may safely reference {@link VectorOps} and the
 * JIT will intrinsify operations into SIMD instructions. When {@code false},
 * callers must take the scalar fallback path — referencing {@code VectorOps}
 * would fail at link time because it imports {@code jdk.incubator.vector.*}
 * types.
 * <p>
 * No dynamic {@link ModuleLayer} resolution is attempted: classes loaded
 * outside the boot layer are not reliably intrinsified, so the expected
 * SIMD gain would be lost even if the module could be made visible.
 */
public final class VectorOpsBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger("Async-Vector");

    public static final boolean AVAILABLE = probe();

    private VectorOpsBootstrap() {}

    /**
     * Emit the status line once at startup so server operators can see
     * whether SIMD fast-paths are active.
     */
    public static void logStatus() {
        if (AVAILABLE) {
            LOGGER.info("Vector API (jdk.incubator.vector) is resolved — SIMD fast-paths enabled.");
        } else {
            LOGGER.info("Vector API is not resolved — using scalar SoA fallback. " + "For extra throughput pass --add-modules=jdk.incubator.vector to the JVM.");
        }
    }

    private static boolean probe() {
        try {
            Class.forName("jdk.incubator.vector.DoubleVector", false, VectorOpsBootstrap.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
