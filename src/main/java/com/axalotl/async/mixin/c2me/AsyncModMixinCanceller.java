package com.axalotl.async.mixin.c2me;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

public class AsyncModMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        if (FabricLoader.getInstance().isModLoaded("c2me")) {
            return mixinClassName.equals("com.ishland.c2me.fixes.general.threading_issues.mixin.asynccatchers.MixinThreadedAnvilChunkStorage");
        } else return false;
    }
}

