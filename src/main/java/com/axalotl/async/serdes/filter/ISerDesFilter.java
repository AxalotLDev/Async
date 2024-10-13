package com.axalotl.async.serdes.filter;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.axalotl.async.serdes.ISerDesHookType;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public interface ISerDesFilter {

    void serialise(Runnable task, Object obj, BlockPos bp, World w, ISerDesHookType hookType);

    @Nullable
    default Set<Class<?>> getTargets() {
        return null;
    }

    /**
     * Perform initialisation; this may include optimisation steps like looking up
     * pools pre-emptively, generating pook configs, etc.
     * <p>
     * As such it is invoked after pools are initialised
     */
    default void init() {

    }

    @Nullable
    default Set<Class<?>> getWhitelist() {
        return null;
    }

    enum ClassMode {
        BLACKLIST,
        WHITELIST,
        UNKNOWN
    }

    @Nonnull
    default ClassMode getModeOnline(Class<?> c) {
        return ClassMode.UNKNOWN;
    }
}
