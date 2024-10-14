package com.axalotl.async.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.conversion.*;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.common.collect.Lists;
import net.fabricmc.loader.api.FabricLoader;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SerDesConfig {

    /**
     * Standard, run of the mill filter config
     * <p>
     * TODO: add more filter config parameters; like backup pools etc.
     *
     */

    private static final ObjectConverter OBJECT_CONVERTER = new ObjectConverter();

    public static class FilterConfig {
        public FilterConfig() {
        }

        public FilterConfig(int priority, String name, List<String> whitelist, List<String> blacklist, String pool,
                            Config poolParams) {
            this.priority = priority;
            this.name = name;
            this.whitelist = whitelist;
            this.blacklist = blacklist;
            this.pool = pool;
            this.poolParams = poolParams;
        }

        @Path("priority")
        @SpecIntInRange(min = 0, max = Integer.MAX_VALUE)
        int priority;

        @Path("name")
        String name;

        @Path("targets.whitelist")
        @SpecValidator(ClassListValidator.class)
        List<String> whitelist;

        @Path("targets.blacklist")
        @SpecValidator(ClassListValidator.class)
        List<String> blacklist;

        @Path("pools.primary.name")
        @SpecNotNull
        String pool;

        @Path("pools.primary.params")
        Config poolParams;

        public int getPriority() {
            return priority;
        }

        public List<String> getWhitelist() {
            return whitelist;
        }

        public List<String> getBlacklist() {
            return blacklist;
        }

        public String getPool() {
            return pool;
        }

        public Map<String, Object> getPoolParams() {
            try {
                return poolParams.valueMap();
            } catch (java.lang.NullPointerException npe) {
                return new HashMap<>();
            }
        }
    }

    public static class PoolConfig {
        @Path("class")
        @SpecValidator(ClassValidator.class)
        String clazz;

        @Path("name")
        @SpecNotNull
        String name;

        @Path("params")
        @SpecNotNull
        Config initParams;

        @Path("priority")
        @SpecIntInRange(min = Integer.MIN_VALUE, max = Integer.MAX_VALUE)
        int priority;

        public String getClazz() {
            return clazz;
        }

        public String getName() {
            return name;
        }

        public Map<String, Object> getInitParams() {
            return initParams.valueMap();
        }

        public int getPriority() {
            return priority;
        }

    }

    private static final class ClassListValidator implements Predicate<Object> {
        String validatorRegex = "^[a-z_]+(\\.[a-z0-9_]+)*((\\.[A-Z][A-Za-z0-9_]+($[A-Za-z0-9_]+)*)?|\\.\\*|\\.\\*\\*)\\$?([A-Z][A-Za-z0-9_]+($[A-Za-z0-9_]+)*)+$";

        @Override
        public boolean test(Object t) {
            if (t == null) {
                return true;
            }

            if (t instanceof List<?> list) {
                for (Object s : list) {
                    if (!(s instanceof String && ((String) s).matches(validatorRegex))) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

    private static final class ClassValidator implements Predicate<Object> {
        String validatorRegex = "^[a-z_]+(\\.[a-z0-9_]+)*((\\.[A-Za-z0-9_]+(\\$[A-Za-z0-9_]+)*))?$";

        @Override
        public boolean test(Object s) {
            return s instanceof String && ((String) s).matches(validatorRegex);
        }
    }

    static Map<String, FileConfig> configs = new HashMap<>();
    static Map<String, List<FilterConfig>> filters = new HashMap<>();
    static Map<String, List<PoolConfig>> pools = new HashMap<>();

    public static void loadConfigs() {
        filters = new HashMap<>();
        pools = new HashMap<>();
    }

    private static void loadConfig(FileConfig fc) {
        fc.load();
        String file = fc.getNioPath().getFileName().toString();
        List<Config> pool = fc.get("pools");
        List<Config> filter = fc.get("filters");
        if (pool == null) pool = new ArrayList<>();
        if (filter == null) filter = new ArrayList<>();
        filters.put(file, filter.stream()
                .map(c -> OBJECT_CONVERTER.toObject(c, FilterConfig::new))
                .collect(Collectors.toList()));
        pools.put(file, pool.stream()
                .map(c -> OBJECT_CONVERTER.toObject(c, PoolConfig::new))
                .collect(Collectors.toList()));
        configs.put(file, fc);
    }

    public static List<PoolConfig> getPools() {
        return pools.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingInt(PoolConfig::getPriority))
                .collect(Collectors.toList());
    }

    public static List<FilterConfig> getFilters() {
        return filters.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingInt(FilterConfig::getPriority))
                .collect(Collectors.toList());
    }

    public static void createFilterConfig(String name, Integer priority, List<String> whitelist, List<String> blacklist, @Nullable String pool) {
        java.nio.file.Path saveTo = FabricLoader.getInstance().getConfigDir().resolve("mcmt-serdes").resolve(name + ".toml");
        FilterConfig fc = new FilterConfig(priority, name, whitelist, blacklist, pool == null ? "LEGACY" : pool, Config.inMemory());
        FileConfig config = FileConfig.builder(saveTo).build();
        config.set("filters", Lists.newArrayList(OBJECT_CONVERTER.toConfig(fc, Config::inMemoryUniversal)));
        System.out.println("Saving config to " + saveTo + " ...");
        config.save();
        config.close();
    }
}
