package com.axalotl.async.common.mixin.utils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks every loaded mod's root paths looking for classes whose methods carry
 * {@code @com.axalotl.async.api.annotation.SyncItemPickup}.
 *
 * <p>Returns the internal class names ({@code com/example/MyMob}) of every
 * candidate target. The actual bytecode rewrite is handled later by
 * {@link SyncItemPickupTransformer}; this stage only locates targets so we can
 * generate stub mixins for them.</p>
 */
final class SyncAnnotationScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncAnnotationScanner.class);
    private static final String ANNOTATION_DESC = "Lcom/axalotl/async/api/annotation/SyncItemPickup;";

    /** Skip our own classes, vanilla, JDK, and common library namespaces — they're either handled or irrelevant. */
    private static final String[] SKIP_PREFIXES = {
            "com/axalotl/async/",
            "net/minecraft/",
            "java/", "javax/", "jdk/", "sun/",
            "org/spongepowered/", "com/llamalad7/", "com/bawnorton/",
            "org/objectweb/asm/", "org/slf4j/", "org/apache/",
            "it/unimi/dsi/fastutil/", "com/google/", "com/mojang/",
    };

    private SyncAnnotationScanner() {}

    static Set<String> scan() {
        Set<String> targets = new LinkedHashSet<>();
        for (Path root : discoverModRoots()) {
            scanRoot(root, targets);
        }
        return targets;
    }

    private static List<Path> discoverModRoots() {
        try {
            Class<?> loaderCls = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderCls.getMethod("getInstance").invoke(null);
            Collection<?> mods = (Collection<?>) loaderCls.getMethod("getAllMods").invoke(loader);
            List<Path> roots = new java.util.ArrayList<>();
            for (Object mod : mods) {
                Method getRootPaths = mod.getClass().getMethod("getRootPaths");
                @SuppressWarnings("unchecked")
                List<Path> modRoots = (List<Path>) getRootPaths.invoke(mod);
                roots.addAll(modRoots);
            }
            return roots;
        } catch (ClassNotFoundException ignore) {
            // Not Fabric — try NeoForge below.
        } catch (Throwable t) {
            LOGGER.warn("Async: Fabric mod enumeration failed", t);
        }
        try {
            Class<?> fmlCls = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Object current = fmlCls.getMethod("getCurrent").invoke(null);
            Object loadingList = current.getClass().getMethod("getLoadingModList").invoke(current);
            Collection<?> modFiles = (Collection<?>) loadingList.getClass().getMethod("getModFiles").invoke(loadingList);
            List<Path> roots = new java.util.ArrayList<>();
            for (Object mfi : modFiles) {
                Object modFile = mfi.getClass().getMethod("getFile").invoke(mfi);
                Object secureJar = modFile.getClass().getMethod("getSecureJar").invoke(modFile);
                Path root = (Path) secureJar.getClass().getMethod("getRootPath").invoke(secureJar);
                if (root != null) roots.add(root);
            }
            return roots;
        } catch (ClassNotFoundException ignore) {
            // Neither Fabric nor NeoForge detected.
        } catch (Throwable t) {
            LOGGER.warn("Async: NeoForge mod enumeration failed", t);
        }
        LOGGER.warn("Async: no supported mod loader detected — @SyncItemPickup auto-scan disabled");
        return List.of();
    }

    private static void scanRoot(Path root, Set<String> out) {
        if (!Files.isDirectory(root)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".class")) return FileVisitResult.CONTINUE;
                    if (name.equals("module-info.class") || name.equals("package-info.class")) return FileVisitResult.CONTINUE;
                    try (InputStream in = Files.newInputStream(file)) {
                        scanClass(in, out);
                    } catch (IOException e) {
                        LOGGER.debug("Async: cannot read {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Async: walk failed for {}", root, e);
        }
    }

    private static void scanClass(InputStream in, Set<String> out) throws IOException {
        ClassReader reader = new ClassReader(in);
        String internalName = reader.getClassName();
        for (String prefix : SKIP_PREFIXES) {
            if (internalName.startsWith(prefix)) return;
        }
        boolean[] hit = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String aDesc, boolean visible) {
                        if (ANNOTATION_DESC.equals(aDesc)) hit[0] = true;
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (hit[0]) {
            out.add(internalName);
            LOGGER.debug("Async: found @SyncItemPickup target {}", internalName);
        }
    }
}
