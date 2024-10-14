package com.axalotl.async.serdes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import com.axalotl.async.config.BlockEntityLists;
import com.axalotl.async.config.SerDesConfig;
import com.axalotl.async.serdes.filter.*;
import com.axalotl.async.serdes.pools.ChunkLockPool;
import com.axalotl.async.serdes.pools.ISerDesPool;
import com.axalotl.async.serdes.pools.ISerDesPool.ISerDesOptions;

import com.axalotl.async.serdes.pools.PostExecutePool;
import com.axalotl.async.serdes.pools.SingleExecutionPool;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fully modular filtering
 *
 * @author jediminer543
 */
public class SerDesRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<Class<?>, ISerDesFilter> EMPTYMAP = new ConcurrentHashMap<>();
    private static final Set<Class<?>> EMPTYSET = ConcurrentHashMap.newKeySet();

    static Map<ISerDesHookType, Map<Class<?>, ISerDesFilter>> optimisedLookup;
    static Map<ISerDesHookType, Set<Class<?>>> whitelist;
    static Set<Class<?>> unknown;

    static ArrayList<ISerDesFilter> filters;

    static Set<ISerDesHookType> hookTypes;

    static {
        filters = new ArrayList<>();
        optimisedLookup = new ConcurrentHashMap<>();
        whitelist = new ConcurrentHashMap<>();
        unknown = ConcurrentHashMap.newKeySet();
        hookTypes = new HashSet<>();
        //TODO do an event loop so that this is a thing
        Collections.addAll(hookTypes, SerDesHookTypes.values());
    }

    private static final ISerDesFilter DEFAULT_FILTER = new DefaultFilter();

    public static void init() {
        SerDesConfig.loadConfigs();
        initPools();
        initFilters();
        initLookup();
    }

    public static void initFilters() {
        filters.clear();
        // High Priority (I.e. non overridable)
        filters.add(new VanillaFilter());
        filters.add(new LegacyFilter()); //TODO kill me
        // Config loaded
        for (SerDesConfig.FilterConfig fpc : SerDesConfig.getFilters()) {
            ISerDesFilter filter = new GenericConfigFilter(fpc);
            filters.add(filter);
        }
        // Low priority
        filters.add(AutoFilter.singleton());
        filters.add(DEFAULT_FILTER);
        for (ISerDesFilter sdf : filters) {
            sdf.init();
        }
    }

    public static void initLookup() {
        optimisedLookup.clear();
        for (ISerDesFilter f : filters) {
            Set<Class<?>> rawTgt = f.getTargets();
            Set<Class<?>> rawWl = f.getWhitelist();
            if (rawTgt == null) rawTgt = ConcurrentHashMap.newKeySet();
            if (rawWl == null) rawWl = ConcurrentHashMap.newKeySet();
            Map<ISerDesHookType, Set<Class<?>>> whitelist = group(rawWl);
            for (ISerDesHookType sh : hookTypes) {
                for (Class<?> i : rawTgt) {
                    if (sh.isTargetable(i)) {
                        optimisedLookup.computeIfAbsent(sh,
                                k -> new ConcurrentHashMap<>()).put(i, f);
                        whitelist.computeIfAbsent(sh,
                                k -> ConcurrentHashMap.newKeySet()).remove(i);
                    }
                }
                whitelist.computeIfAbsent(sh,
                        k -> ConcurrentHashMap.newKeySet()).addAll(rawWl);
            }
        }
    }

    public static Map<ISerDesHookType, Set<Class<?>>> group(Set<Class<?>> set) {
        Map<ISerDesHookType, Set<Class<?>>> out = new ConcurrentHashMap<>();
        for (Class<?> i : set) {
            for (ISerDesHookType sh : hookTypes) {
                if (sh.isTargetable(i)) {
                    out.computeIfAbsent(sh, k -> ConcurrentHashMap.newKeySet()).add(i);
                }
            }
        }
        return out;
    }

    public static ISerDesFilter getFilter(ISerDesHookType isdh, Class<?> clazz) {
        if (whitelist.getOrDefault(isdh, EMPTYSET).contains(clazz)) {
            return null;
        }
        return optimisedLookup.getOrDefault(isdh, EMPTYMAP).getOrDefault(clazz, DEFAULT_FILTER);
    }

    static Map<String, ISerDesPool> registry = new ConcurrentHashMap<>();

    public static ISerDesPool getPool(String name) {
        return registry.get(name);
    }

    public static ISerDesPool getOrCreatePool(String name, Function<String, ISerDesPool> source) {
        return registry.computeIfAbsent(name, source);
    }

    public static ISerDesPool getOrCreatePool(String name, Supplier<ISerDesPool> source) {
        return getOrCreatePool(name, i -> {
            ISerDesPool out = source.get();
            out.init(i, new HashMap<>());
            return out;
        });
    }

//    public static boolean removeFromWhitelist(ISerDesHookType isdh, Class<?> c) {
//        return whitelist.getOrDefault(isdh, EMPTYSET).remove(c);
//    }

    public static void initPools() {
        registry.clear();
        // HARDCODED DEFAULTS
        getOrCreatePool("LEGACY", ChunkLockPool::new);
        getOrCreatePool("SINGLE", SingleExecutionPool::new);
        getOrCreatePool("POST", () -> PostExecutePool.POOL);
        // LOADED FROM CONFIG
        List<SerDesConfig.PoolConfig> pcl = SerDesConfig.getPools();
        if (pcl != null) for (SerDesConfig.PoolConfig pc : pcl) {
            if (!registry.containsKey(pc.getName())) {
                try {
                    Class<?> c = Class.forName(pc.getClazz());
                    Constructor<?> init = c.getConstructor();
                    Object o = init.newInstance();
                    if (o instanceof ISerDesPool) {
                        registry.put(pc.getName(), (ISerDesPool) o);
                        ((ISerDesPool) o).init(pc.getName(), pc.getInitParams());
                    }
                } catch (ClassNotFoundException | InvocationTargetException | IllegalArgumentException |
                         IllegalAccessException | InstantiationException | SecurityException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static class DefaultFilter implements ISerDesFilter {

        //TODO make not shit
        public static boolean filterTE(Object tte) {
            boolean isLocking = BlockEntityLists.teBlackList.contains(tte.getClass());
            // Apparently a string starts with check is faster than Class.getPackage; who knew (I didn't)
            if (!isLocking && !tte.getClass().getName().startsWith("net.minecraft.block.entity.")) {
                isLocking = true;
            }
            if (isLocking && BlockEntityLists.teWhiteList.contains(tte.getClass())) {
                isLocking = false;
            }
            if (tte instanceof PistonBlockEntity) {
                isLocking = true;
            }
            return isLocking;
        }

        ISerDesPool clp;
        ISerDesOptions config;

        @Override
        public void init() {
            clp = SerDesRegistry.getOrCreatePool("LEGACY", ChunkLockPool::new);
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("range", "1");
            config = clp.compileOptions(cfg);
        }

        @Override
        public void serialise(Runnable task, Object obj, BlockPos bp, World w, ISerDesHookType hookType) {
            if (!unknown.contains(obj.getClass())) {
                ClassMode mode = ClassMode.UNKNOWN;
                for (ISerDesFilter isdf : filters) {
                    ClassMode cm = isdf.getModeOnline(obj.getClass());
                    if (cm.compareTo(mode) < 0) {
                        mode = cm;
                    }
                    if (mode == ClassMode.BLACKLIST) {
                        optimisedLookup.computeIfAbsent(hookType,
                                        i -> new ConcurrentHashMap<>())
                                .put(obj.getClass(), isdf);
                        isdf.serialise(task, obj, bp, w, hookType);
                        return;
                    }
                }
                if (mode == ClassMode.WHITELIST) {
                    whitelist.computeIfAbsent(hookType,
                                    k -> ConcurrentHashMap.newKeySet())
                            .add(obj.getClass());
                    task.run(); // Whitelist = run on thread
                    return;
                }
                unknown.add(obj.getClass());
            }
            // TODO legacy behaviour please fix
            if (hookType.equals(SerDesHookTypes.TETick) && filterTE(obj)) {
                clp.serialise(task, obj, bp, w, config);
            } else {
                try {
                    task.run();
                } catch (Exception e) {
                    LOGGER.error("Exception running " + obj.getClass().getName() + " asynchronusly", e);
                    LOGGER.error("Adding " + obj.getClass().getName() + " to blacklist.");
                    AutoFilter.singleton().addClassToBlacklist(obj.getClass());
                    // TODO: this could leave a tick in an incomplete state. should the full exception be thrown?
                    if (e instanceof RuntimeException) throw e;
                }
            }
        }


    }
}

