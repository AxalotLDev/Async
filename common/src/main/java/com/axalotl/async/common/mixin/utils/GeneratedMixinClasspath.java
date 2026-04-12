package com.axalotl.async.common.mixin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Materializes generated stub mixin class bytes onto the active mod-loader's
 * classpath so that Mixin's bytecode provider can resolve them.
 *
 * <p>Strategy: write all stubs to a temporary directory tree on disk, then ask
 * the platform classloader to add that directory as a code source via
 * reflection on internal APIs (Knot for Fabric, ModLauncher for NeoForge).
 * If injection fails, we log a clear, actionable error rather than crashing —
 * the rest of the mod still works, the auto-magic {@code @SyncItemPickup} just
 * won't apply to plain classes (it still works for in-tree mixins via
 * {@link SyncItemPickupTransformer}).</p>
 */
final class GeneratedMixinClasspath {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedMixinClasspath.class);

    private GeneratedMixinClasspath() {}

    /**
     * Write every generated class to a temp dir and add that dir to the active classloader.
     *
     * @param classes map of binary class name (dot-separated) to bytes
     * @return {@code true} on success.
     */
    static boolean publish(Map<String, byte[]> classes) {
        if (classes.isEmpty()) return true;
        Path root;
        try {
            root = Files.createTempDirectory("async-generated-mixins-");
            root.toFile().deleteOnExit();
        } catch (IOException e) {
            LOGGER.error("Async: cannot create temp dir for generated mixins", e);
            return false;
        }

        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Path target = root.resolve(e.getKey().replace('.', '/') + ".class");
            try {
                Files.createDirectories(target.getParent());
                Files.write(target, e.getValue());
                target.toFile().deleteOnExit();
            } catch (IOException ex) {
                LOGGER.error("Async: cannot write {}", target, ex);
                return false;
            }
        }

        if (tryInjectKnot(root)) return true;
        if (tryInjectModLauncher(root)) return true;

        LOGGER.error("Async: failed to inject generated mixin classpath into the active classloader. " +
                "@SyncItemPickup on plain classes will not take effect. " +
                "This usually means the mod loader version is incompatible — please open an issue at " +
                "https://github.com/AxalotLDev/Async/issues including your loader version.");
        return false;
    }

    /** Fabric Knot: reflection on KnotClassDelegate#addCodeSource(Path). */
    private static boolean tryInjectKnot(Path root) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            // Walk up parent chain looking for the Knot loader.
            ClassLoader cur = cl;
            while (cur != null) {
                String name = cur.getClass().getName();
                if (name.contains("KnotClassLoader") || name.contains("knot.Knot")) {
                    Object delegate = readField(cur, "delegate");
                    if (delegate == null) {
                        cur = cur.getParent();
                        continue;
                    }
                    // KnotClassDelegate.addCodeSource(Path) — present in Loader 0.14+
                    for (Method m : delegate.getClass().getMethods()) {
                        if (m.getName().equals("addCodeSource")
                                && m.getParameterCount() == 1
                                && m.getParameterTypes()[0] == Path.class) {
                            m.invoke(delegate, root);
                            LOGGER.info("Async: injected generated mixin classpath via Knot ({} entries)", root);
                            return true;
                        }
                    }
                }
                cur = cur.getParent();
            }
        } catch (Throwable t) {
            LOGGER.debug("Async: Knot injection path failed", t);
        }
        return false;
    }

    /** NeoForge ModLauncher: reflection on TransformingClassLoader's resource finder. */
    private static boolean tryInjectModLauncher(Path root) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            ClassLoader cur = cl;
            while (cur != null) {
                String name = cur.getClass().getName();
                if (name.contains("TransformingClassLoader") || name.contains("modlauncher")) {
                    // ModLauncher exposes a 'resourceFinder' field of type Function<String, Enumeration<URL>>
                    // we wrap it to also serve our temp dir.
                    if (wrapResourceFinder(cur, root)) {
                        LOGGER.info("Async: injected generated mixin classpath via ModLauncher ({})", root);
                        return true;
                    }
                }
                cur = cur.getParent();
            }
        } catch (Throwable t) {
            LOGGER.debug("Async: ModLauncher injection path failed", t);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean wrapResourceFinder(ClassLoader cl, Path root) throws Exception {
        for (Field f : cl.getClass().getDeclaredFields()) {
            if (f.getName().toLowerCase().contains("resourcefinder")
                    || f.getName().toLowerCase().contains("classbytesfinder")) {
                f.setAccessible(true);
                Object original = f.get(cl);
                if (original instanceof java.util.function.Function<?, ?> fn) {
                    java.util.function.Function<String, Object> wrapped = path -> {
                        Path local = root.resolve(path);
                        if (Files.isRegularFile(local)) {
                            try {
                                return java.util.Collections.enumeration(java.util.List.of(local.toUri().toURL()));
                            } catch (Exception ignored) {}
                        }
                        return ((java.util.function.Function<String, Object>) fn).apply(path);
                    };
                    f.set(cl, wrapped);
                    return true;
                }
            }
        }
        return false;
    }

    private static Object readField(Object owner, String name) throws ReflectiveOperationException {
        Class<?> c = owner.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(owner);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
