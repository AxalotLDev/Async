package com.axalotl.async.api.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as explicitly compatible with asynchronous processing.
 *
 * <p>
 * By default, all modded entities are assumed to be
 * <b>not thread-safe</b> and will be executed synchronously on the main thread.
 * Entity classes annotated with {@code @AsyncCompatible} indicate that they are
 * designed to be safely used in asynchronous contexts.
 * </p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @AsyncCompatible
 * public class MyCustomEntity extends Entity {
 *     // safe async logic here
 * }
 * }</pre>
 *
 * @see "com.axalotl.async.common.ParallelProcessor#entitySupportsAsyncApi(Entity entity)"
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AsyncCompatible {
}