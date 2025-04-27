package com.axalotl.async.common.mixin.c2me;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class AsyncModMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return mixinClassName.equals("com.ishland.c2me.fixes.general.threading_issues.mixin.asynccatchers.MixinThreadedAnvilChunkStorage") ||
                mixinClassName.equals("com.ishland.c2me.fixes.worldgen.threading_issues.mixin.threading_detections.random_instances.MixinWorld");
    }
}
