package com.axalotl.async.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity method that consumes an {@code ItemEntity} (typically the override
 * of {@code pickUpItem(ServerLevel, ItemEntity)}) as needing automatic synchronization
 * when Async is processing entities on multiple threads.
 *
 * <p>At class-load time, AsyncAPI rewrites the annotated method to:</p>
 * <ol>
 *   <li>Acquire a per-declaring-class lock before executing the body.</li>
 *   <li>Skip the body entirely if the {@code ItemEntity} parameter has already been
 *       removed (i.e. {@code itemEntity.isRemoved()} returns {@code true}).</li>
 * </ol>
 *
 * <p>Without these two guards, two parallel ticks may race on the same item entity,
 * both pass their internal liveness check, and both consume it — producing the classic
 * item-duplication bug observed on Pandas, Foxes, Allays, Villagers, etc.</p>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>The annotated method <b>must</b> declare an {@code ItemEntity} (or subtype) parameter.</li>
 *   <li>Annotate only the entry point that consumes the item — not internal helpers.</li>
 *   <li>Do <b>not</b> add a manual {@code synchronized} keyword or your own
 *       {@code isRemoved()} check; both are injected for you.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @AsyncCompatible
 * public class TruffleHog extends Animal {
 *
 *     @Override
 *     @SyncItemPickup
 *     protected void pickUpItem(ServerLevel level, ItemEntity item) {
 *         super.pickUpItem(level, item);
 *         getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, item.getOwner());
 *     }
 * }
 * }</pre>
 *
 * @see com.axalotl.async.api.utils.AsyncCompatible
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SyncItemPickup {
}
