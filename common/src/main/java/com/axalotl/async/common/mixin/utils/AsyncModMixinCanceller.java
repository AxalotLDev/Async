package com.axalotl.async.common.mixin.utils;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class AsyncModMixinCanceller implements MixinCanceller {
    private boolean LITHIUM = false;
    private boolean VMP = false;
    private boolean C2ME = false;

    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (mixinClassName.contains("lithium") && !mixinClassName.contains("async")) {
            LITHIUM = true;
        }
        if (mixinClassName.contains("vmp") && !mixinClassName.contains("async")) {
            VMP = true;
        }
        if (mixinClassName.contains("c2me") && !mixinClassName.contains("async")) {
            C2ME = true;
        }
        switch (mixinClassName) {
            case "com.ishland.c2me.fixes.general.threading_issues.mixin.asynccatchers.MixinThreadedAnvilChunkStorage":
            case "com.ishland.c2me.fixes.worldgen.threading_issues.mixin.threading_detections.random_instances.MixinWorld":
            case "net.caffeinemc.mods.lithium.mixin.collections.attributes.AttributeMapMixin":
            case "net.caffeinemc.mods.lithium.mixin.util.entity_movement_tracking.EntitySectionMixin":
            case "net.caffeinemc.mods.lithium.mixin.entity.projectile_projectile_collisions.ProjectileUtilMixin":
                return true;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumServerLevel") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.LithiumGameEventDispatcherStorage") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.ReferenceMaskedListMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.AsyncLithiumEntitySectionMixin") ||
                mixinClassName.endsWith("com.axalotl.async.common.mixin.lithium.AsyncLithiumEntityMovementTrackerMixin")) {
            return !LITHIUM;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.vmp.VMPChunkMapMixin")) {
            return !VMP;
        }
        if (mixinClassName.endsWith("com.axalotl.async.common.mixin.c2me.TheChunkSystemMixin")) {
            return !C2ME;
        }
        return mixinClassName.endsWith("com.cupboard.mixin.ServerAddEntityMixin");
    }
}