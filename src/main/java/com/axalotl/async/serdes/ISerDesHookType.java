package com.axalotl.async.serdes;

public interface ISerDesHookType {

    String getName();

    Class<?> getSuperclass();

    default boolean isTargetable(Class<?> clazz) {
        return getSuperclass().isAssignableFrom(clazz);
    }

    default boolean isTargetable(Object obj) {
        return isTargetable(obj.getClass());
    }

}
