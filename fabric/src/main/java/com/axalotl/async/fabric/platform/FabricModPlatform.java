package com.axalotl.async.fabric.platform;

import com.axalotl.async.common.platform.ModPlatform;
import com.axalotl.async.fabric.config.AsyncConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.util.HashSet;
import java.util.Set;

public class FabricModPlatform implements ModPlatform {

    @Override
    public void saveConfig() {
        AsyncConfig.saveConfig();
    }

    @Override
    public void reloadConfig() {
        AsyncConfig.init();
    }

    @Override
    public boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public Set<String> getModIds() {
        Set<String> modIds = new HashSet<>();
        FabricLoader.getInstance().getAllMods().forEach(mod -> modIds.add(mod.getMetadata().getId()));
        return modIds;
    }
}