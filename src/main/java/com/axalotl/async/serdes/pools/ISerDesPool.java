package com.axalotl.async.serdes.pools;

import java.util.Map;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public interface ISerDesPool {

    interface ISerDesOptions {
    }

    void serialise(Runnable task, Object o, BlockPos bp, World w, @Nullable ISerDesOptions options);

    default ISerDesOptions compileOptions(Map<String, Object> config) {
        return null;
    }

    default void init(String name, Map<String, Object> config) {

    }

}