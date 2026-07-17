package com.axalotl.async.common.mixin.utils;

import java.util.List;

public class MixinCanceller implements com.bawnorton.mixinsquared.api.MixinCanceller {
    private boolean LITHIUM = false;
    private boolean VMP = false;

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.contains("lithium") && !mixinClassName.contains("async")) {
            LITHIUM = true;
        }
        if (mixinClassName.contains("vmp") && !mixinClassName.contains("async")) {
            VMP = true;
        }
        switch (mixinClassName) {
            case "com.ishland.c2me.fixes.general.threading_issues.mixin.asynccatchers.MixinThreadedAnvilChunkStorage":
            case "com.ishland.c2me.fixes.worldgen.threading_issues.mixin.threading_detections.random_instances.MixinWorld":
                return true;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumServerLevel") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumChunkSectionChangeCallbackMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumSectionedBlockChangeTrackerMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumInternerMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumGameEventDispatcherStorage") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.ReferenceMaskedListMixin")
        ) {
            return !LITHIUM;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.vmp.VMPChunkMapMixin")) {
            return !VMP;
        }
        return mixinClassName.endsWith("com.cupboard.mixin.ServerAddEntityMixin");
    }
}