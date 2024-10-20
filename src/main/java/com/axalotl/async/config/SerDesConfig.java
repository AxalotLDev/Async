package com.axalotl.async.config;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.conversion.*;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.common.collect.Lists;
import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SerDesConfig {

    /**
     * Standard, run of the mill filter config
     * <p>
     * TODO: add more filter config parameters; like backup pools etc.
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

        @Getter
        @Path("priority")
        @SpecIntInRange(min = 0, max = Integer.MAX_VALUE)
        int priority;

        @Path("name")
        String name;

        @Getter
        @Path("targets.whitelist")
        @SpecValidator(ClassListValidator.class)
        List<String> whitelist;

        @Getter
        @Path("targets.blacklist")
        @SpecValidator(ClassListValidator.class)
        List<String> blacklist;

        @Getter
        @Path("pools.primary.name")
        @SpecNotNull
        String pool;

        @Path("pools.primary.params")
        //@SpecNotNull
        Config poolParams; // nightconfig does not support Maps, use Configs instead

        public Map<String, Object> getPoolParams() {
            try {
                return poolParams.valueMap();
            } catch (NullPointerException npe) {
                return new HashMap<>();
            }
        }
    }

    public static class PoolConfig {
        @Getter
        @Path("class")
        @SpecValidator(ClassValidator.class)
        String clazz;

        @Getter
        @Path("name")
        @SpecNotNull
        String name;

        @Path("params")
        @SpecNotNull
        Config initParams;

        @Getter
        @Path("priority")
        @SpecIntInRange(min = Integer.MIN_VALUE, max = Integer.MAX_VALUE)
        int priority;

        public Map<String, Object> getInitParams() {
            return initParams.valueMap();
        }

    }

    private static final class ClassListValidator implements Predicate<Object> {
        String validatorRegex = "^[a-z_]+(\\.[a-z0-9_]+)*((\\.[A-Z][A-Za-z0-9_]+($[A-Za-z0-9_]+)*)?|\\.\\*|\\.\\*\\*)\\$?([A-Z][A-Za-z0-9_]+($[A-Za-z0-9_]+)*)+$";

        @Override
        public boolean test(Object t) {
            if (t == null) {
                return true;
            }

            if (t instanceof List<?>) {
                List<?> list = (List<?>) t;
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

//    public static void loadConfigs() {
//        filters = new HashMap<>();
//        pools = new HashMap<>();
//        java.nio.file.Path cfgDir = FabricLoader.getInstance().getConfigDir();
//        java.nio.file.Path serdesDir = cfgDir.resolve("async-serdes");
//        if (Files.isDirectory(serdesDir)) {
//
//        } else {
////            try {
////                Files.createDirectory(serdesDir);
////            } catch (IOException e) {
////                e.printStackTrace();
////            }
//        }
//        try {
//            Files.walk(serdesDir).map(p -> {
//                try {
//                    return FileConfig.of(p);
//                } catch (NoFormatFoundException nffe) {
//                    return null;
//                }
//            }).filter(Predicates.notNull()).filter(Objects::nonNull).forEach(SerDesConfig::loadConfig);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

//    private static void loadConfig(FileConfig fc) {
//        fc.load();
//        String file = fc.getNioPath().getFileName().toString();
//        List<Config> pool = fc.get("pools");
//        List<Config> filter = fc.get("filters");
//        if (pool == null) pool = new ArrayList<>();
//        if (filter == null) filter = new ArrayList<>();
//        filters.put(file, filter.stream()
//                .map(c -> OBJECT_CONVERTER.toObject(c, FilterConfig::new))
//                .collect(Collectors.toList()));
//        pools.put(file, pool.stream()
//                .map(c -> OBJECT_CONVERTER.toObject(c, PoolConfig::new))
//                .collect(Collectors.toList()));
//        configs.put(file, fc);
//    }

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
        java.nio.file.Path saveTo = FabricLoader.getInstance().getConfigDir().resolve("async-serdes").resolve(name + ".toml");
        FilterConfig fc = new FilterConfig(priority, name, whitelist, blacklist, pool == null ? "LEGACY" : pool, Config.inMemory());
        FileConfig config = FileConfig.builder(saveTo).build();
        config.set("filters", Lists.newArrayList(OBJECT_CONVERTER.toConfig(fc, Config::inMemoryUniversal)));
        System.out.println("Saving config to " + saveTo + " ...");
        config.save();
        config.close();
    }
}
