package com.axalotl.async.mixin.utils;

import java.util.HashMap;
import java.util.Map;

public class ForgeMappingResolver {

    // Static map to resolve obfuscated to deobfuscated names with descriptors
    private static final Map<String, String> METHOD_MAPPINGS = new HashMap<>();

    static {
        // Key: className/methodName/descriptor
        METHOD_MAPPINGS.put("net.minecraft.src.C_2126_/m_62427_/()V", "net.minecraft.world.level.chunk.ChunkStatus.isOrAfter");
    }

    /**
     * Resolves the deobfuscated method name from obfuscated using class name, method name, and descriptor.
     *
     * @param className   The obfuscated class name.
     * @param methodName  The obfuscated method name.
     * @param descriptor  The method descriptor.
     * @return The deobfuscated method name if found; otherwise, returns the obfuscated method name.
     */
    public static String mapMethodName(String className, String methodName, String descriptor) {
        String key = className + "/" + methodName + "/" + descriptor;
        return METHOD_MAPPINGS.getOrDefault(key, methodName);
    }
}