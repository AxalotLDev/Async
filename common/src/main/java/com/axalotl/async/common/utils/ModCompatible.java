package com.axalotl.async.common.utils;

import com.axalotl.async.common.platform.PlatformUtils;

import java.util.ArrayList;
import java.util.List;

public class ModCompatible {

    public static List<String > addUnsupportedMods() {
        final List<String> mods = new ArrayList<>();
        if (PlatformUtils.isModLoaded("create")) {
            mods.add("create:*");
        } else if (PlatformUtils.isModLoaded("fowlplay")) {
            mods.add("fowlplay:*");
        }
        return mods;
    }
}