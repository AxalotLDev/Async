package com.axalotl.async.neoforge.platform;

import com.axalotl.async.common.platform.ModPlatform;
import com.axalotl.async.neoforge.config.AsyncConfig;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

import java.util.HashSet;
import java.util.Set;

public class NeoForgeModPlatform implements ModPlatform {

    @Override
    public void saveConfig() {
        AsyncConfig.saveConfig();
    }

    @Override
    public void reloadConfig() {
        AsyncConfig.loadConfig();
    }

    @Override
    public boolean isModLoaded(String id) {
        return FMLLoader.getCurrent().getLoadingModList().getModFileById(id) != null;
    }

    @Override
    public Set<String> getModIds() {
        Set<String> modIds = new HashSet<>();
        ModList.get().getMods().forEach(modInfo -> modIds.add(modInfo.getModId()));
        return modIds;
    }
}