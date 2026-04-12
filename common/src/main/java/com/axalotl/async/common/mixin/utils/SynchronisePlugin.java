package com.axalotl.async.common.mixin.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SynchronisePlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronisePlugin.class);

    private static final int FINAL_STATIC_PRIVATE_ABSTRACT = 0x1548; // final, static, private, abstract
    private static final int SYNCHRONIZED = 0x20; // synchronized
    private final Multimap<String, String> mixin2MethodsMap = ArrayListMultimap.create();
    private final Multimap<String, String> mixin2MethodsExcludeMap = ArrayListMultimap.create();
    private final TreeSet<String> syncAllSet = new TreeSet<>();

    /** FQNs of dynamically-generated stub mixins for 3rd-party {@code @SyncItemPickup} targets. */
    private final List<String> generatedStubMixins = new ArrayList<>();

    @Override
    public void onLoad(String mixinPackage) {
        mixin2MethodsExcludeMap.put("com.axalotl.async.common.mixin.utils.SyncAllMixin", "net.minecraft.world.level.chunk.ChunkStatus.isOrAfter");
        syncAllSet.add("com.axalotl.async.common.mixin.utils.FastUtilSynchronizeMixin");
        syncAllSet.add("com.axalotl.async.common.mixin.utils.SyncAllMixin");

        bootstrapGeneratedStubs();
    }

    /**
     * Discover 3rd-party classes carrying {@code @SyncItemPickup}, generate one stub mixin per
     * target class, and publish them on the active classloader so Mixin can resolve them when
     * {@link #getMixins()} returns their names.
     */
    private void bootstrapGeneratedStubs() {
        try {
            Set<String> targets = SyncAnnotationScanner.scan();
            if (targets.isEmpty()) return;

            Map<String, byte[]> stubs = new LinkedHashMap<>();
            for (String internalName : targets) {
                String fqn = SyncStubMixinGenerator.stubClassName(internalName);
                stubs.put(fqn, SyncStubMixinGenerator.generate(internalName));
                generatedStubMixins.add(fqn);
            }

            if (!GeneratedMixinClasspath.publish(stubs)) {
                generatedStubMixins.clear();
                return;
            }
            LOGGER.info("Async: registered {} @SyncItemPickup target(s) via auto-generated mixins", generatedStubMixins.size());
        } catch (Throwable t) {
            LOGGER.error("Async: @SyncItemPickup auto-discovery failed; in-tree mixins still work", t);
            generatedStubMixins.clear();
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return generatedStubMixins.isEmpty() ? null : generatedStubMixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        Collection<String> targetMethods = mixin2MethodsMap.get(mixinClassName);
        Collection<String> excludedMethods = mixin2MethodsExcludeMap.get(mixinClassName);

        // Always scan for @SyncItemPickup — the annotation is preserved by Mixin on woven methods.
        SyncItemPickupTransformer.apply(targetClass);

        if (!targetMethods.isEmpty()) {
            applySynchronizeBit(targetClass, targetMethods, targetClassName);
        } else if (syncAllSet.contains(mixinClassName)) {
            for (MethodNode method : targetClass.methods) {
                if ((method.access & FINAL_STATIC_PRIVATE_ABSTRACT) == 0 && !method.name.equals("<init>") && !excludedMethods.contains(method.name)) {
                    method.access |= SYNCHRONIZED;
                    logSynchronize(method.name, targetClassName, mixinClassName);
                }
            }
        }
    }

    private void applySynchronizeBit(ClassNode targetClass, Collection<String> targetMethods, String targetClassName) {
        for (MethodNode method : targetClass.methods) {
            for (String targetMethod : targetMethods) {
                if (method.name.equals(targetMethod)) {
                    method.access |= SYNCHRONIZED;
                    logSynchronize(method.name, targetClassName, null);
                }
            }
        }
    }

    private void logSynchronize(String methodName, String targetClassName, String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.equals("com.axalotl.async.mixin.utils.FastUtilSynchronizeMixin")) {
            String message = "Setting synchronize bit for " + methodName + " in " + targetClassName + ".";
            LOGGER.debug(message);
        }
    }
}