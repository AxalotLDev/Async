package com.axalotl.async.api.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as explicitly compatible with asynchronous processing.
 *
 * <p>
 * When {@code synchronizeUnannotatedModEntities} is enabled, modded entities are
 * assumed to be <b>not thread-safe</b> and execute synchronously unless their class
 * has {@code @AsyncCompatible}. The option is disabled by default, so this annotation
 * only affects servers that explicitly enable the compatibility gate.
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
