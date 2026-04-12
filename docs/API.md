# AsyncAPI — Mod Developer Reference

This document describes the public API exposed by **Async** for third-party mod developers who want their custom entities, AI sensors, or data structures to remain correct when Async is processing entities on multiple threads.

> All API classes live under the package `com.axalotl.async.api.*` and are guaranteed-stable across patch releases. Anything outside this package is internal and may change without notice.

---

## Table of Contents

- [Annotations](#annotations)
  - [`@AsyncCompatible`](#asynccompatible)
  - [`@SyncItemPickup`](#syncitempickup)
- [Concurrent Collections](#concurrent-collections)
  - [`ConcurrentCollections`](#concurrentcollections)
  - [`IteratorSafeOrderedReferenceSet<E>`](#iteratorsafeorderedreferencesete)
- [AI / Sensor Helpers](#ai--sensor-helpers)
  - [`SensorUtils`](#sensorutils)
- [FastUtil Concurrent Wrappers](#fastutil-concurrent-wrappers)
  - [`Int2ObjectConcurrentHashMap<V>`](#int2objectconcurrenthashmapv)
  - [`Long2ObjectConcurrentHashMap<V>`](#long2objectconcurrenthashmapv)
  - [`Long2LongConcurrentHashMap`](#long2longconcurrenthashmap)
  - [`ConcurrentLongLinkedOpenHashSet`](#concurrentlonglinkedopenhashset)
  - [`ConcurrentLongSortedSet`](#concurrentlongsortedset)
  - [`FastUtilHackUtil`](#fastutilhackutil)

---

## Annotations

### `@AsyncCompatible`

**Package:** `com.axalotl.async.api.utils`
**Target:** `TYPE` · **Retention:** `RUNTIME`

A marker annotation you place on **your custom entity class** to signal to Async that the class has been audited for concurrent ticking and does not require the fallback synchronized-tick path.

Use it only after you have:
- Replaced any non-thread-safe collections in your entity with their concurrent equivalents (see below).
- Verified that any inter-entity interaction (item pickup, breeding, container access, etc.) is properly synchronized.

```java
import com.axalotl.async.api.utils.AsyncCompatible;
import net.minecraft.world.entity.animal.Animal;

@AsyncCompatible
public class MyCustomMob extends Animal {
    // ... entity code that is safe to tick in parallel
}
```

If your entity is **not** marked, Async will tick it on the main server thread, which is safe but slower.

---

### `@SyncItemPickup`

**Package:** `com.axalotl.async.api.annotation`
**Target:** `METHOD` · **Retention:** `RUNTIME`

Place this annotation on your entity's `pickUpItem(ServerLevel, ItemEntity)` method (or any equivalent method that consumes an `ItemEntity`) to make it safe to call from multiple threads.

**Where you can put it:**
- Directly on a method in your **own entity class** (the normal case — no Mixin needed).
- On a `@WrapMethod` method in your own Mixin (if you mix into a third-party entity you don't own).

Both are auto-detected at boot — Async scans every loaded mod's classes for the annotation and rewires them. No registration, no extra configuration, no Gradle plugin.

**What it does at class-load time:**
1. The annotated method's body is moved into a private helper.
2. The original method becomes a wrapper that synchronizes on the `ItemEntity` argument and skips the call if `entity.isRemoved()` (checked twice — once before locking for the fast path, once inside the lock).

**Why this is necessary:** without these two guards, two parallel ticks can race on the same `ItemEntity`, both pass their internal "is the item still alive?" check, and both call `pickUpItem`. Result — the item is consumed twice and duplicated in the inventories of two different mobs (a classic dupe bug seen on Pandas, Foxes, Allays, Villagers, etc.).

**Contract:**
- The annotated method **must** take an `ItemEntity` (or subtype) as one of its parameters.
- Mark exactly the entry point that touches the item — not every helper.
- Combine with `@AsyncCompatible` on the enclosing class.

```java
import com.axalotl.async.api.annotation.SyncItemPickup;
import com.axalotl.async.api.utils.AsyncCompatible;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Animal;

@AsyncCompatible
public class TruffleHog extends Animal {

    @Override
    @SyncItemPickup
    protected void pickUpItem(ServerLevel level, ItemEntity item) {
        // your normal pickup logic — Async injects sync + isRemoved() guard for you
        super.pickUpItem(level, item);
        getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, item.getOwner());
    }
}
```

> **Heads-up:** do not also add a manual `synchronized` keyword or your own `isRemoved()` check — the annotation already provides both, and double-locking adds latency for no benefit.

---

## Concurrent Collections

### `ConcurrentCollections`

**Package:** `com.axalotl.async.api.utils`

Tiny factory class for the three most-needed thread-safe JDK collection shapes. Use these wherever you would have written `new HashSet<>()`, `new HashMap<>()`, or `Collectors.toList()` in code that may run on an Async worker thread.

| Method | Returns | Backed by |
| --- | --- | --- |
| `static <T> Set<T> newHashSet()` | thread-safe `Set<T>` | `Collections.newSetFromMap(new ConcurrentHashMap<>())` |
| `static <T,U> Map<T,U> newHashMap()` | thread-safe `Map<T,U>` | `ConcurrentHashMap` |
| `static <T> Collector<T,?,List<T>> toList()` | stream collector | `CopyOnWriteArrayList` |

```java
import com.axalotl.async.api.utils.ConcurrentCollections;

public class HiveTracker {
    private final Set<UUID> knownBees = ConcurrentCollections.newHashSet();
    private final Map<BlockPos, Long> lastVisit = ConcurrentCollections.newHashMap();

    public List<UUID> snapshot() {
        return knownBees.stream().collect(ConcurrentCollections.toList());
    }
}
```

---

### `IteratorSafeOrderedReferenceSet<E>`

**Package:** `com.axalotl.async.api.utils`

An ordered, reference-equality set that supports **safe concurrent iteration with structural modification** during the iteration. Used internally by Async for entity-tick lists; exposed for mods that maintain similar order-sensitive entity collections.

Key properties:
- Reference equality (`==`), not `equals()`.
- Maintains insertion order.
- Iterators don't throw `ConcurrentModificationException` if elements are added/removed mid-iteration.
- Self-defragments when the live ratio drops below `maxFragFactor` (default `0.2`).

| Member | Purpose |
| --- | --- |
| `int ITERATOR_FLAG_SEE_ADDITIONS` | Pass to `iterator(int)` so the iterator yields elements added *after* it was created. |
| `boolean add(E e)` | Insert; auto-defrag triggers if needed. |
| `boolean remove(E e)` | Mark removed; lazy compaction. |
| `boolean contains(E e)` | Membership test. |
| `int size()` | Live element count. |
| `Iterator<E> iterator()` | Standard iterator (snapshot of current view). |
| `Iterator<E> iterator(int flags)` | Configurable iterator. |
| `void finishRawIterator()` | **Must** be called when you finish a raw iteration to allow defragmentation. |

```java
import com.axalotl.async.api.utils.IteratorSafeOrderedReferenceSet;

IteratorSafeOrderedReferenceSet<Entity> tracked = new IteratorSafeOrderedReferenceSet<>();
tracked.add(entityA);
tracked.add(entityB);

Iterator<Entity> it = tracked.iterator(IteratorSafeOrderedReferenceSet.ITERATOR_FLAG_SEE_ADDITIONS);
try {
    while (it.hasNext()) {
        Entity e = it.next();
        e.tick();
        // safe to add new entities here — the iterator will see them
    }
} finally {
    tracked.finishRawIterator();
}
```

---

## AI / Sensor Helpers

### `SensorUtils`

**Package:** `com.axalotl.async.api.utils`

Helpers for safely customizing vanilla AI sensors that Async parallelizes.

#### `wrapSensor(Sensor<T> original, TickWrapper<T> logic)`

Returns a new `Sensor<T>` that delegates `requires()` to the original sensor but routes `doTick(level, entity)` through your custom lambda. Use this when you want to inject thread-safe logic before/after vanilla sensor work — e.g. to fold your mod's memory-module updates into an existing sensor without subclassing.

#### `distanceComparator(Entity source)`

Returns a `Comparator<T>` that orders entities by squared distance to `source`, **caching** each computed distance for the duration of the comparator's lifetime. Use with `List.sort` or `stream().sorted(...)` when sorting hot lists during a tick — avoids the O(n log n) recomputation of `distanceToSqr` that a naïve comparator does.

```java
import com.axalotl.async.api.utils.SensorUtils;

Sensor<Villager> wrapped = SensorUtils.wrapSensor(originalNearestPlayerSensor, (level, villager) -> {
    // your pre-tick logic here, runs on whatever thread Async picks
    villager.getBrain().setMemory(MY_MEMORY, computeStuff(villager));
    originalNearestPlayerSensor.tick(level, villager); // delegate to vanilla
});

List<Mob> nearby = pool.stream()
        .sorted(SensorUtils.distanceComparator(self))
        .toList();
```

---

## FastUtil Concurrent Wrappers

These classes implement standard fastutil interfaces (`Long2ObjectMap`, `LongSortedSet`, etc.) but are **safe for concurrent use**. Use them as drop-in replacements wherever your mod stored entity ids, chunk positions, or block ids in fastutil maps that may now be touched from multiple threads.

All maps in this section are backed by `java.util.concurrent.ConcurrentHashMap`; the sets are backed by `ConcurrentSkipListSet`. Iteration is weakly consistent — it never throws `ConcurrentModificationException` and reflects state at *some* point during the traversal.

### `Int2ObjectConcurrentHashMap<V>`

**Package:** `com.axalotl.async.api.fastutil`
Drop-in for `Int2ObjectOpenHashMap<V>`.

| Method | Description |
| --- | --- |
| `V get(int key)`, `V put(int, V)`, `V remove(int)` | Standard primitive-keyed operations. |
| `V putIfAbsent(int, V)`, `V computeIfAbsent(int, IntFunction<V>)` | Atomic insert-or-fetch. |
| `boolean replace(int, V, V)` | Atomic CAS-style replace. |
| `ObjectSet<Int2ObjectMap.Entry<V>> int2ObjectEntrySet()` | Entry view (weakly consistent). |
| `IntSet keySet()`, `ObjectCollection<V> values()` | Live views. |

```java
Int2ObjectConcurrentHashMap<EntityState> byNetId = new Int2ObjectConcurrentHashMap<>();
EntityState state = byNetId.computeIfAbsent(entity.getId(), id -> new EntityState(id));
```

### `Long2ObjectConcurrentHashMap<V>`

**Package:** `com.axalotl.async.api.fastutil`
Drop-in for `Long2ObjectOpenHashMap<V>` — typical use is keying by chunk position (`ChunkPos.toLong()`) or block position (`BlockPos.asLong()`).

| Method | Description |
| --- | --- |
| `V get(long)`, `V put(long, V)`, `V remove(long)` | Primitive-keyed ops. |
| `V putIfAbsent(long, V)`, `V computeIfAbsent(long, LongFunction<V>)`, `V compute(long, ...)` | Atomic helpers. |
| `boolean replace(long, V, V)` | CAS replace. |
| `ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet()` | Entry view. |
| `LongSet keySet()`, `ObjectCollection<V> values()` | Live views. |

```java
Long2ObjectConcurrentHashMap<ChunkData> chunks = new Long2ObjectConcurrentHashMap<>();
chunks.computeIfAbsent(ChunkPos.asLong(x, z), p -> loadChunkData(p));
```

### `Long2LongConcurrentHashMap`

**Package:** `com.axalotl.async.api.fastutil`
For long→long mappings (cooldown timers keyed by block position, last-seen-tick-by-entity-id, etc.).

| Method | Description |
| --- | --- |
| `long get(long)`, `long put(long, long)`, `long remove(long)` | Primitive-only ops. |
| `boolean containsKey(long)`, `boolean containsValue(long)` | Membership. |
| Snapshot views of entry/key/value sets. |

### `ConcurrentLongLinkedOpenHashSet`

**Package:** `com.axalotl.async.api.fastutil`
Ordered set of longs with `firstLong()`/`lastLong()`/`removeFirstLong()`/`removeLastLong()`. Use for FIFO/LIFO queues of entity or block ids accessed from multiple threads.

### `ConcurrentLongSortedSet`

**Package:** `com.axalotl.async.api.fastutil`
Sorted set of longs implementing fastutil's `LongSortedSet`. Supports `subSet`, `headSet`, `tailSet`, and a `LongBidirectionalIterator iterator(long fromElement)` for ranged scans.

### `FastUtilHackUtil`

**Package:** `com.axalotl.async.api.fastutil`
Bridge utilities to wrap raw JDK collections so they implement fastutil interfaces. Useful when you need to hand a JDK `Set<Long>` to an API that requires `LongSet`.

| Method (selected) | Purpose |
| --- | --- |
| `LongSet wrapLongSet(Set<Long>)` | View a JDK long set as a fastutil `LongSet`. |
| `IntSet wrapIntSet(Set<Integer>)` | Same for ints. |
| `ObjectSet<Long2ObjectMap.Entry<T>> entrySetLongWrap(...)` | Adapt entry sets. |
| `Long2ObjectMap.FastEntrySet<T> entrySetLongWrapFast(...)` | Fast iteration variant. |
| Inner classes `WrappingLongIterator`, `WrappingIntIterator`, etc. | Iterator adapters. |

```java
Set<Long> jdkSet = ConcurrentHashMap.newKeySet();
LongSet asFastUtil = FastUtilHackUtil.wrapLongSet(jdkSet);
someVanillaApiThatWantsLongSet.accept(asFastUtil);
```

---

## Versioning & Stability

- Anything in `com.axalotl.async.api.*` follows semver — breaking changes only on a major bump.
- Internal packages (`com.axalotl.async.common.*`, `com.axalotl.async.fabric.*`, `com.axalotl.async.neoforge.*`) are not API and may change at any time.
- If you need a hook that doesn't exist, please open an issue: <https://github.com/AxalotLDev/Async/issues>.
