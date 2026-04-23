package com.axalotl.async.common.parallelised.utils;

import net.minecraft.world.level.NaturalSpawner;

/**
 * Per-thread spawn-pipeline context.
 *
 * <p>A single {@link ThreadLocal} holding a mutable {@link Frame} replaces
 * three separate {@code ThreadLocal&lt;Boolean&gt;}/{@code ThreadLocal&lt;SpawnState&gt;}
 * entries. The separate-ThreadLocals version profiled at ~19.5% of Server
 * self-time in JDK bookkeeping ({@code setInitialValue},
 * {@code expungeStaleEntry}, {@code cleanSomeSlots}, {@code ThreadLocalMap#set/remove})
 * because every spawn attempt invoked {@code set}/{@code remove} on up to
 * three entries — thousands of attempts per tick × three map mutations each
 * stressed the hash probe cleanup path.
 *
 * <p>Single-entry design:
 * <ul>
 *   <li>One {@link ThreadLocal} with {@link #withInitial} — the {@link Frame}
 *       is allocated once per thread and <b>never replaced</b>. Subsequent
 *       {@code get()} calls hit the warm {@code ThreadLocalMap} slot with no
 *       setInitialValue / expunge traffic.</li>
 *   <li>Mutation is direct field writes on the returned {@link Frame},
 *       which compile to ordinary putfield bytecode — no ThreadLocal write
 *       path at all.</li>
 *   <li>Cleanup at {@code spawnForChunk} exit resets the fields; we never
 *       call {@code ThreadLocal.remove()}, so {@code expungeStaleEntry} and
 *       friends never fire on this path.</li>
 * </ul>
 *
 * <p>The three logical flags are now:
 * <ul>
 *   <li>{@link Frame#state} — active {@link NaturalSpawner.SpawnState},
 *       non-null when the thread is inside {@code spawnForChunk}.</li>
 *   <li>{@link Frame#skipAfterSpawn} — CAS reservation failed; the follow-up
 *       {@code spawnCallback.run(mob, chunk)} must be cancelled
 *       (no entity was actually added).</li>
 *   <li>{@link Frame#suppressCountIncrement} — CAS reservation succeeded;
 *       vanilla {@code mobCategoryCounts.addTo} inside {@code afterSpawn}
 *       would double-count and must be skipped.</li>
 * </ul>
 */
public final class AsyncSpawnContext {

    public static final class Frame {
        public NaturalSpawner.SpawnState state;
        public boolean skipAfterSpawn;
        public boolean suppressCountIncrement;

        /** Resets the flags without touching {@code state}. */
        public void clearFlags() {
            this.skipAfterSpawn = false;
            this.suppressCountIncrement = false;
        }
    }

    public static final ThreadLocal<Frame> FRAME = ThreadLocal.withInitial(Frame::new);

    private AsyncSpawnContext() {}
}
