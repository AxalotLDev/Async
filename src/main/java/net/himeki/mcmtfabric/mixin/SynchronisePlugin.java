package net.himeki.mcmtfabric.mixin;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class SynchronisePlugin implements IMixinConfigPlugin {
    private static final Logger syncLogger = LogManager.getLogger();
    private final Multimap<String, String> mixin2MethodsMap = ArrayListMultimap.create();
    private final Multimap<String, String> mixin2MethodsExcludeMap = ArrayListMultimap.create();
    private final TreeSet<String> syncAllSet = new TreeSet<>();

    @Override
    public void onLoad(String mixinPackage) {
        MappingResolver mappingResolver = FabricLoader.getInstance().getMappingResolver();
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ServerChunkManagerMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_3215", "method_14161", "()V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ServerTickSchedulerMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_1949", "method_8670", "()V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ServerTickSchedulerMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_1949", "method_20514", "(Lnet/minecraft/class_1954;)V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ServerTickSchedulerMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_1949", "method_8672", "(Lnet/minecraft/class_3341;ZZ)Ljava/util/List;"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ServerWorldMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_1937", "method_19282", "(Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_2680;)V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.LevelPropagatorMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_3554", "method_15492", "(I)I"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.LevelPropagatorMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_3554", "method_15478", "(JJIZ)V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ChainRestrictedNeighborUpdaterMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_7159", "method_41706", "(Lnet/minecraft/class_2338;Lnet/minecraft/class_7159$class_7162;)V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ServerChunkManagerMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_3215", "method_21738", "(JLnet/minecraft/class_2791;Lnet/minecraft/class_2806;)V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.PathNodeNavigatorMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_13", "method_52", "(Lnet/minecraft/class_1950;Lnet/minecraft/class_1308;Ljava/util/Set;FIF)Lnet/minecraft/class_11;"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.ChunkStatusMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_2806", "method_20612", "(Lnet/minecraft/class_3218;Lnet/minecraft/class_3485;Lnet/minecraft/class_3227;Ljava/util/function/Function;Lnet/minecraft/class_2791;)Ljava/util/concurrent/CompletableFuture;"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.NearestLivingEntitiesSensorMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_4148","method_19101", "(Lnet/minecraft/class_3218;Lnet/minecraft/class_1309;)V"));
//        mixin2MethodsMap.put("net.himeki.mcmtfabric.mixin.TntEntityMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_1541","method_6971", "(Lnet/minecraft/class_3218;Lnet/minecraft/class_1309;)V"));

        mixin2MethodsExcludeMap.put("net.himeki.mcmtfabric.mixin.SyncAllMixin", mappingResolver.mapMethodName("intermediary", "net.minecraft.class_2806", "method_12165", "()V"));
        syncAllSet.add("net.himeki.mcmtfabric.mixin.SyncAllMixin");
        syncAllSet.add("net.himeki.mcmtfabric.mixin.FastUtilsMixin");

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
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        Collection<String> targetMethods = mixin2MethodsMap.get(mixinClassName);
        Collection<String> excludedMethods = mixin2MethodsExcludeMap.get(mixinClassName);

        if (!targetMethods.isEmpty()) for (MethodNode method : targetClass.methods) {
            for (String targetMethod : targetMethods)
                if (method.name.equals(targetMethod)) {
                    method.access |= Opcodes.ACC_SYNCHRONIZED;
                    syncLogger.info("Setting synchronize bit for " + method.name + " in " + targetClassName + ".");
                }
        }
        else if (syncAllSet.contains(mixinClassName)) {
            int negFilter = Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE;

            for (MethodNode method : targetClass.methods) {
                if ((method.access & negFilter) == 0 && !method.name.equals("<init>") && !excludedMethods.contains(method.name)) {
                    method.access |= Opcodes.ACC_SYNCHRONIZED;
                    syncLogger.info("Setting synchronize bit for " + method.name + " in " + targetClassName + ".");
                }
            }

        }
//        syncLogger.info("Setting synchronize bit for mixins | methods");
    }
}
