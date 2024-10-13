package com.axalotl.async.serdes.filter;

import com.axalotl.async.serdes.ISerDesHookType;
import com.axalotl.async.serdes.SerDesRegistry;
import com.axalotl.async.serdes.pools.ChunkLockPool;
import com.axalotl.async.serdes.pools.ISerDesPool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Hunter Hancock (meta1203)
 * This, and any other code I submit to jediminer543's JMT-MCMT project, is licensed under the 2-Clause BSD License.
 * (<a href="https://opensource.org/licenses/BSD-2-Clause">...</a>)
 */
public class AutoFilter implements ISerDesFilter {
    private static AutoFilter SINGLETON;

    private ISerDesPool pool;
    private final Set<Class<?>> blacklist = ConcurrentHashMap.newKeySet();

    public static AutoFilter singleton() {
        if (SINGLETON == null) SINGLETON = new AutoFilter();
        return SINGLETON;
    }

    @Override
    public void init() {
        pool = SerDesRegistry.getOrCreatePool("AUTO", ChunkLockPool::new);
    }

    @Override
    public void serialise(Runnable task, Object obj, BlockPos bp, World w, ISerDesHookType hookType) {
        pool.serialise(task, obj, bp, w, null);
    }

    @Override
    public Set<Class<?>> getTargets() {
        return blacklist;
    }

    @Override
    public @NotNull ClassMode getModeOnline(Class<?> c) {
        return ClassMode.UNKNOWN;
    }

    public void addClassToBlacklist(Class<?> c) {
        blacklist.add(c);
    }
}
