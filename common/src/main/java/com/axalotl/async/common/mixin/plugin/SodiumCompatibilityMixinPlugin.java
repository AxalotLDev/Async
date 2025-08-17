package com.axalotl.async.common.mixin.plugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class SodiumCompatibilityMixinPlugin implements IMixinConfigPlugin {
    private boolean isSodiumLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
            this.isSodiumLoaded = true;
            System.out.println("Sodium detected. Applying compatibility mixins.");
        } catch (ClassNotFoundException e) {
            System.out.println("Sodium not detected.");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (this.isSodiumLoaded) {
            // Disable client-side mixins that conflict with Sodium
            if (mixinClassName.startsWith("com.axalotl.async.fabric.mixin.client") ||
                mixinClassName.startsWith("com.axalotl.async.neoforge.mixin.client")) {
                System.out.println("Disabling conflicting mixin for Sodium compatibility: " + mixinClassName);
                return false;
            }
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
    }
}
