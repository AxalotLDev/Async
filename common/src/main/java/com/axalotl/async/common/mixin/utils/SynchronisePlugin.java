package com.axalotl.async.common.mixin.utils;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_BRIDGE;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class SynchronisePlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronisePlugin.class);

    private static final String SYNC_ALL_MIXIN = "com.axalotl.async.common.mixin.utils.SyncAllMixin";
    private static final int NOT_SYNCABLE = ACC_STATIC | ACC_BRIDGE | ACC_NATIVE | ACC_ABSTRACT | ACC_SYNTHETIC;

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (!SYNC_ALL_MIXIN.equals(mixinClassName)) return;
        for (MethodNode method : targetClass.methods) {
            if ((method.access & NOT_SYNCABLE) == 0 && !method.name.equals("<init>")) {
                method.access |= ACC_SYNCHRONIZED;
                LOGGER.debug("Setting synchronize bit for {} in {}.", method.name, targetClassName);
            }
        }
    }
}