package com.axalotl.async.api.spawn;

import net.minecraft.world.entity.MobCategory;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Interface mixed into {@code NaturalSpawner.SpawnState} by
 * {@code SpawnStateMixin}. Exposes:
 * <ul>
 *   <li>{@link #async$countsBacking()} — the {@link AtomicIntegerArray}
 *       that backs the overridden {@code mobCategoryCounts}, used for
 *       atomic compare-and-set reservations at the commit point.</li>
 *   <li>{@link #async$canSpawnForCategoryGlobal(MobCategory)} — delegates
 *       to vanilla's private {@code SpawnState#canSpawnForCategoryGlobal}
 *       via {@code @Shadow}. Routing through the vanilla method means the
 *       cap decision respects <em>any</em> mixin chain touching the cap
 *       formula — e.g. ServerCore's mobcap multiplier that overwrites
 *       {@code canSpawnForCategoryGlobal} to multiply the budget.
 *       Precomputing {@code maxPerChunk × spawnableChunkCount / 289} on
 *       our side would bake in the vanilla formula and ignore their
 *       overwrite, under-spawning when their multiplier is &gt;1.</li>
 * </ul>
 */
public interface AsyncSpawnStateView {
    AtomicIntegerArray async$countsBacking();
    boolean async$canSpawnForCategoryGlobal(MobCategory category);
}
