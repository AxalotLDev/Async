package com.axalotl.async.common.mixin.utils;

import java.util.List;

public class MixinCanceller implements com.bawnorton.mixinsquared.api.MixinCanceller {
    private boolean LITHIUM = false;
    private boolean VMP = false;
    private boolean C2ME = false;

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.contains("lithium") && !mixinClassName.contains("async")) {
            LITHIUM = true;
        }
        if (mixinClassName.contains("c2me") && !mixinClassName.contains("async")) {
            C2ME = true;
        }
        if (mixinClassName.contains("ishland.vmp") && !mixinClassName.contains("async")) {
            VMP = true;
        }
        switch (mixinClassName) {
            case "com.ishland.c2me.base.mixin.instrumentation.MixinServerChunkManager":
            case "com.ishland.c2me.fixes.general.threading_issues.mixin.asynccatchers.MixinThreadedAnvilChunkStorage":
            case "com.ishland.c2me.fixes.worldgen.threading_issues.mixin.threading_detections.random_instances.MixinWorld":
            case "net.caffeinemc.mods.lithium.mixin.collections.attributes.AttributeMapMixin":
            case "net.caffeinemc.mods.lithium.mixin.util.entity_movement_tracking.EntitySectionMixin":
            case "net.caffeinemc.mods.lithium.mixin.collections.entity_filtering.ClassInstanceMultiMapMixin":
            case "com.ishland.vmp.mixins.general.collections.MixinTypeFilterableList":
                return true;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumServerLevel") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumGameEventDispatcherStorage") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.ReferenceMaskedListMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.AsyncLithiumEntitySectionMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.AsyncLithiumEntityMovementTrackerMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.AsyncLithiumClassGroupFilterableListMixin")) {
            return !LITHIUM;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.c2me.TheChunkSystemMixin")) {
            return !C2ME;
        }
        return mixinClassName.endsWith("com.cupboard.mixin.ServerAddEntityMixin");
    }
}
