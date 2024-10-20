package com.axalotl.async.serdes.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.axalotl.async.config.BlockEntityLists;
import com.axalotl.async.serdes.ISerDesHookType;
import com.axalotl.async.serdes.SerDesRegistry;

import com.axalotl.async.serdes.pools.ChunkLockPool;
import com.axalotl.async.serdes.pools.ISerDesPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class LegacyFilter implements ISerDesFilter {

    ISerDesPool clp;
    ISerDesPool.ISerDesOptions config;

    @Override
    public void init() {
        clp = SerDesRegistry.getOrCreatePool("LEGACY", ChunkLockPool::new);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("range", "1");
        config = clp.compileOptions(cfg);
    }

    @Override
    public void serialise(Runnable task, Object obj, BlockPos bp, World w, ISerDesHookType hookType) {
        clp.serialise(task, obj, bp, w, config);
    }

    @Override
    public Set<Class<?>> getTargets() {
        return BlockEntityLists.teBlackList;
    }

    @Override
    public Set<Class<?>> getWhitelist() {
        return BlockEntityLists.teWhiteList;
    }

}
