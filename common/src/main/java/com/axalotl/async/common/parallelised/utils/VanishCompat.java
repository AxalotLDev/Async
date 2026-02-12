package com.axalotl.async.common.parallelised.utils;

import lombok.SneakyThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VanishCompat {

    @SneakyThrows
    @SuppressWarnings("deprecation")
    public static void apply() {
        Class<?> vanishManager;
        try {
            vanishManager = Class.forName("me.drex.vanish.util.VanishManager");
        } catch (ClassNotFoundException e) {
            return;
        }

        Field cacheField = null;
        for (Field f : vanishManager.getDeclaredFields()) {
            if (f.getType() == Map.class && Modifier.isStatic(f.getModifiers())) {
                cacheField = f;
                break;
            }
        }

        if (cacheField == null) {
            return;
        }

        cacheField.setAccessible(true);

        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        long offset = unsafe.staticFieldOffset(cacheField);
        unsafe.putObject(vanishManager, offset, new ConcurrentHashMap<>());
    }
}