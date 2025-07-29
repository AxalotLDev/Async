package com.axalotl.async.common.mixin.utils;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class AsyncModMixinCanceller implements MixinCanceller {
    private boolean LITHIUM = false;

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.contains("lithium") && !mixinClassName.contains("async")) {
            LITHIUM = true;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumServerChunkCacheMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumServerLevel")) {
            return !LITHIUM;
        }
        return mixinClassName.equals("com.ishland.c2me.fixes.general.threading_issues.mixin.asynccatchers.MixinThreadedAnvilChunkStorage") ||
                mixinClassName.equals("com.ishland.c2me.fixes.worldgen.threading_issues.mixin.threading_detections.random_instances.MixinWorld");
    }
}