package com.axalotl.async.serdes.filter;

import com.axalotl.async.serdes.ISerDesHookType;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public class VanillaFilter implements ISerDesFilter {

    @Override
    public void serialise(Runnable task, Object obj, BlockPos bp, World w, ISerDesHookType hookType) {
        task.run();
    }

    @Override
    public @NotNull ClassMode getModeOnline(Class<?> c) {
        if (c.getName().startsWith("net.minecraft")) {
            return ClassMode.WHITELIST;
        }
        return ClassMode.UNKNOWN;
    }

}
