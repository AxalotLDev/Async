package com.axalotl.async.common.mixin.plugin;

import com.axalotl.async.common.config.AsyncConfig;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Arrays;
import java.util.HashSet;

public class AsyncMixinPlugin implements IMixinConfigPlugin {
    private static final Logger syncLogger = LogManager.getLogger();
    private final Multimap<String, String> mixin2MethodsMap = ArrayListMultimap.create();
    private final Multimap<String, String> mixin2MethodsExcludeMap = ArrayListMultimap.create();
    private final TreeSet<String> syncAllSet = new TreeSet<>();
    private final Set<String> serverMixins = new HashSet<>();
    private final Set<String> clientMixins = new HashSet<>();
    private final Set<String> sodiumMixins = new HashSet<>();

    private boolean isSodiumLoaded = false;
    private boolean isLithiumLoaded = false;
    private boolean isVmpLoaded = false;
    private boolean isClient = false;

    @Override
    public void onLoad(String mixinPackage) {
        mixin2MethodsExcludeMap.put("com.axalotl.async.common.mixin.utils.SyncAllMixin", "net.minecraft.world.level.chunk.ChunkStatus.isOrAfter");
        syncAllSet.add("com.axalotl.async.common.mixin.utils.FastUtilsMixin");
        syncAllSet.add("com.axalotl.async.common.mixin.utils.SyncAllMixin");

        String[] serverMixinClasses = {
            "com.axalotl.async.common.mixin.server.ServerWatchdogMixin",
            "com.axalotl.async.common.mixin.vmp.VMPChunkMapMixin"
        };
        serverMixins.addAll(Arrays.asList(serverMixinClasses));

        String[] clientMixinClasses = {
            "com.axalotl.async.fabric.mixin.client.LevelRendererMixin",
            "com.axalotl.async.neoforge.mixin.client.LevelRendererMixin"
        };
        clientMixins.addAll(Arrays.asList(clientMixinClasses));

        String[] sodiumMixinClasses = {
            "com.axalotl.async.fabric.mixin.sodium.SodiumWorldRendererMixin",
            "com.axalotl.async.fabric.mixin.sodium.AsyncSodiumWorldRendererMixin",
            "com.axalotl.async.neoforge.mixin.sodium.SodiumWorldRendererMixin",
            "com.axalotl.async.neoforge.mixin.sodium.AsyncSodiumWorldRendererMixin"
        };
        sodiumMixins.addAll(Arrays.asList(sodiumMixinClasses));

        try {
            Class.forName("net.minecraft.client.Minecraft");
            this.isClient = true;
        } catch (ClassNotFoundException e) {
            this.isClient = false;
        }

        try {
            Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
            this.isSodiumLoaded = true;
            syncLogger.info("Sodium detected. Applying compatibility mixins.");
        } catch (ClassNotFoundException e) {
            syncLogger.info("Sodium not detected.");
        }

        try {
            Class.forName("me.jellysquid.mods.lithium.common.LithiumMod");
            this.isLithiumLoaded = true;
            syncLogger.info("Lithium detected. Applying compatibility mixins.");
        } catch (ClassNotFoundException e) {
            syncLogger.info("Lithium not detected.");
        }

        try {
            Class.forName("com.ishland.vmp.common.VMPMod");
            this.isVmpLoaded = true;
            syncLogger.info("VMP detected. Applying compatibility mixins.");
        } catch (ClassNotFoundException e) {
            syncLogger.info("VMP not detected.");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (this.isClient) {
            if (serverMixins.contains(mixinClassName)) {
                return false;
            }
        } else { // on server
            if (clientMixins.contains(mixinClassName)) {
                return false;
            }
        }

        if (sodiumMixins.contains(mixinClassName)) {
            return this.isSodiumLoaded;
        }

        if (mixinClassName.startsWith("com.axalotl.async.common.mixin.lithium")) {
            return this.isLithiumLoaded;
        }
        if (mixinClassName.startsWith("com.axalotl.async.common.mixin.vmp")) {
            return this.isVmpLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        Collection<String> targetMethods = mixin2MethodsMap.get(mixinClassName);
        Collection<String> excludedMethods = mixin2MethodsExcludeMap.get(mixinClassName);

        if (!targetMethods.isEmpty()) {
            applySynchronizeBit(targetClass, targetMethods, targetClassName);
        } else if (syncAllSet.contains(mixinClassName)) {
            int negFilter = 5448;
            for (MethodNode method : targetClass.methods) {
                if ((method.access & negFilter) == 0 && !method.name.equals("<init>") && !excludedMethods.contains(method.name)) {
                    method.access |= 32;
                    logSynchronize(method.name, targetClassName, mixinClassName);
                }
            }
        }
    }

    private void applySynchronizeBit(ClassNode targetClass, Collection<String> targetMethods, String targetClassName) {
        for (MethodNode method : targetClass.methods) {
            for (String targetMethod : targetMethods) {
                if (method.name.equals(targetMethod)) {
                    method.access |= 32;
                    logSynchronize(method.name, targetClassName, null);
                }
            }
        }
    }

    private void logSynchronize(String methodName, String targetClassName, String mixinClassName) {
        if (mixinClassName == null || !mixinClassName.equals("com.axalotl.async.mixin.utils.FastUtilsMixin")) {
            String message = "Setting synchronize bit for " + methodName + " in " + targetClassName + ".";
            syncLogger.debug(message);
        }
    }
}
