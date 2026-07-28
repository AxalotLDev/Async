package com.axalotl.async.api.fastutil;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import org.apache.commons.lang3.ArrayUtils;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;


/**
 * Utility bridge between standard Java Collections and FastUtil primitive collections.
 * <p>
 * This class provides adapters (“wrappers”) that allow:
 * <ul>
 *     <li>Using {@link Collection} as FastUtil primitive collections</li>
 *     <li>Converting Map entries between boxed and primitive key/value pairs</li>
 *     <li>Wrapping primitive FastUtil iterators around Java iterators</li>
 * </ul>
 *
 * <p><b>Purpose:</b> Reduce boilerplate when interoperating between
 * FastUtil and standard Java collections while avoiding full data copies
 * where possible.</p>
 *
 * <p>This class is not intended to be instantiated.</p>
 */
public final class FastUtilHackUtil {

    private FastUtilHackUtil() {
        throw new AssertionError("No instances");
    }

    /**
     * Wraps a {@link Collection} of {@link Byte} into a FastUtil {@link ByteCollection}.
     *
     * @param c the backing collection
     * @return a {@link ByteCollection} view backed by {@code c}
     */
    public static ByteCollection wrapBytes(Collection<Byte> c) {
        return new WrappingByteCollection(c);
    }

    /**
     * Adapter that exposes a {@link Collection} as a FastUtil {@link ByteCollection}.
     * <p>
     * Backed directly by the original collection.
     */
    public static class WrappingByteCollection implements ByteCollection {

        Collection<Byte> backing;

        /**
         * Creates a view backed by the given collection.
         *
         * @param backing the collection to wrap
         */
        public WrappingByteCollection(Collection<Byte> backing) {
            this.backing = backing;
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(byte o) {
            return backing.contains(o);
        }

        @Override
        public Object @NotNull [] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T @NotNull [] toArray(T @NotNull [] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean add(byte e) {
            return backing.add(e);
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Byte> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public @NotNull ByteIterator iterator() {
            return FastUtilHackUtil.itrByteWrap(backing);
        }

        @Override
        public boolean rem(byte key) {
            return backing.remove(key);
        }

        @Override
        public byte[] toByteArray() {
            return null;
        }

        @Override
        public byte[] toArray(byte[] a) {
            return ArrayUtils.toPrimitive(backing.toArray(new Byte[0]));
        }

        @Override
        public boolean addAll(ByteCollection c) {
            return addAll((Collection<Byte>) c);
        }

        @Override
        public boolean containsAll(ByteCollection c) {
            return containsAll((Collection<Byte>) c);
        }

        @Override
        public boolean removeAll(ByteCollection c) {
            return removeAll((Collection<Byte>) c);
        }

        @Override
        public boolean retainAll(ByteCollection c) {
            return retainAll((Collection<Byte>) c);
        }
    }

    /**
     * Wraps the entry set of a {@link Map} with boxed {@link Long} keys and {@link Byte} values
     * into a FastUtil entry set.
     *
     * @param map the backing map
     * @return an entry set view backed by {@code map}
     */
    public static ObjectSet<Long2ByteMap.Entry> entrySetLongByteWrap(Map<Long, Byte> map) {
        return new ConvertingObjectSet<>(map.entrySet(), FastUtilHackUtil::longByteEntryForwards, FastUtilHackUtil::longByteEntryBackwards);
    }

    private static Long2ByteMap.Entry longByteEntryForwards(Map.Entry<Long, Byte> entry) {
        return new Long2ByteMap.Entry() {

            @Override
            public byte setValue(byte value) {
                return entry.setValue(value);
            }

            @Override
            public byte getByteValue() {
                return entry.getValue();
            }

            @Override
            public long getLongKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }

        };
    }

    private static Map.Entry<Long, Byte> longByteEntryBackwards(Long2ByteMap.Entry entry) {
        return entry;
    }

    /**
     * Wraps the entry set of a {@link Map} with boxed {@link Long} keys into a FastUtil
     * {@link Long2ObjectMap.FastEntrySet}, which supports reusing a single entry instance
     * while iterating.
     *
     * @param map the backing map
     * @param <T> the value type
     * @return a fast entry set view backed by {@code map}
     */
    public static <T> Long2ObjectMap.FastEntrySet<T> entrySetLongWrapFast(Map<Long, T> map) {
        return new ConvertingObjectSetFast<>(map.entrySet(), FastUtilHackUtil::longEntryForwards, FastUtilHackUtil::longEntryBackwards);
    }

    /**
     * Adapter that exposes a {@link Set} as a FastUtil {@link Long2ObjectMap.FastEntrySet},
     * converting elements with the supplied functions.
     *
     * @param <E> the element type of the backing set
     * @param <T> the value type of the exposed entries
     */
    public static class ConvertingObjectSetFast<E, T> implements Long2ObjectMap.FastEntrySet<T> {

        Set<E> backing;
        Function<E, Long2ObjectMap.Entry<T>> forward;
        Function<Long2ObjectMap.Entry<T>, E> back;

        /**
         * Creates a view backed by the given set.
         *
         * @param backing the set to wrap
         * @param forward converts a backing element into an entry
         * @param back    converts an entry back into a backing element
         */
        public ConvertingObjectSetFast(Set<E> backing,
                                       Function<E, Long2ObjectMap.Entry<T>> forward,
                                       Function<Long2ObjectMap.Entry<T>, E> back) {
            this.backing = backing;
            this.forward = forward;
            this.back = back;
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean contains(Object o) {
            try {
                return backing.contains(back.apply((Long2ObjectMap.Entry<T>) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public Object @NotNull [] toArray() {
            return backing.stream().map(forward).toArray();
        }

        @Override
        public <R> R @NotNull [] toArray(R @NotNull [] a) {
            return backing.stream().map(forward).collect(Collectors.toSet()).toArray(a);
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean remove(Object o) {
            try {
                return backing.remove(back.apply((Long2ObjectMap.Entry<T>) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean containsAll(Collection<?> c) {
            try {
                return backing.containsAll(c.stream()
                        .map(i -> back.apply((Long2ObjectMap.Entry<T>) i))
                        .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }

        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean removeAll(Collection<?> c) {
            try {
                return backing.removeAll(c.stream().map(i -> back
                                .apply((Long2ObjectMap.Entry<T>) i))
                        .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean retainAll(Collection<?> c) {
            try {
                return backing.retainAll(c.stream()
                        .map(i -> back.apply((Long2ObjectMap.Entry<T>) i))
                        .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public void clear() {
            backing.clear();

        }

        @Override
        public @NotNull ObjectIterator<Long2ObjectMap.Entry<T>> iterator() {
            final Iterator<E> backg = backing.iterator();
            return new ObjectIterator<>() {

                @Override
                public boolean hasNext() {
                    return backg.hasNext();
                }

                @Override
                public Long2ObjectMap.Entry<T> next() {
                    return forward.apply(backg.next());
                }

                @Override
                public void remove() {
                    backg.remove();
                }
            };
        }

        @Override
        public boolean add(Long2ObjectMap.Entry<T> e) {
            return backing.add(back.apply(e));
        }

        @Override
        public boolean addAll(Collection<? extends Long2ObjectMap.Entry<T>> c) {
            return backing.addAll(c.stream().map(back).toList());
        }

        @Override
        public ObjectIterator<Long2ObjectMap.Entry<T>> fastIterator() {
            return iterator();
        }


    }

    /**
     * Wraps a Java {@link Iterator} of {@link Byte} into a FastUtil {@link ByteIterator}.
     */
    public static class WrappingByteIterator implements ByteIterator {

        Iterator<Byte> parent;

        /**
         * Creates a view backed by the given iterator.
         *
         * @param parent the iterator to wrap
         */
        public WrappingByteIterator(Iterator<Byte> parent) {
            this.parent = parent;
        }

        @Override
        public boolean hasNext() {
            return parent.hasNext();
        }

        @Override
        public void remove() {
            parent.remove();
        }

        @Override
        public byte nextByte() {
            return parent.next();
        }
    }

    /**
     * Returns a FastUtil {@link ByteIterator} from an {@link Iterable} of boxed bytes.
     *
     * @param backing the backing iterable
     * @return a {@link ByteIterator} over {@code backing}
     */
    public static ByteIterator itrByteWrap(Iterable<Byte> backing) {
        return new WrappingByteIterator(backing.iterator());
    }

    /**
     * Wraps a {@link Set} as a FastUtil {@link ObjectSet} with conversion functions.
     *
     * @param <E> the element type of the backing set
     * @param <T> the element type exposed by this set
     */
    public static class ConvertingObjectSet<E, T> implements ObjectSet<T> {
        private final Set<E> backing;
        private final Function<E, T> forward;
        private final Function<T, E> back;

        /**
         * Creates a view backed by the given set.
         *
         * @param backing the set to wrap
         * @param forward converts a backing element into an exposed element
         * @param back    converts an exposed element back into a backing element
         */
        public ConvertingObjectSet(Set<E> backing, Function<E, T> forward, Function<T, E> back) {
            this.backing = Objects.requireNonNull(backing, "Backing set cannot be null");
            this.forward = Objects.requireNonNull(forward, "Forward function cannot be null");
            this.back = Objects.requireNonNull(back, "Backward function cannot be null");
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean contains(Object o) {
            try {
                return backing.contains(back.apply((T) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public Object @NotNull [] toArray() {
            return backing.stream().map(forward).toArray();
        }

        @Override
        public <R> R @NotNull [] toArray(R @NotNull [] a) {
            return backing.stream().map(forward).collect(Collectors.toSet()).toArray(a);
        }

        @Override
        public boolean add(T e) {
            return backing.add(back.apply(e));
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean remove(Object o) {
            try {
                return backing.remove(back.apply((T) o));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean containsAll(Collection<?> c) {
            try {
                return backing.containsAll(c.stream()
                        .map(i -> back.apply((T) i))
                        .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            return backing.addAll(c.stream().map(back).collect(Collectors.toSet()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean removeAll(Collection<?> c) {
            try {
                return backing.removeAll(c.stream()
                        .map(i -> back.apply((T) i))
                        .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean retainAll(Collection<?> c) {
            try {
                return backing.retainAll(c.stream()
                        .map(i -> back.apply((T) i))
                        .collect(Collectors.toSet()));
            } catch (ClassCastException cce) {
                return false;
            }
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public @NotNull ObjectIterator<T> iterator() {
            return new ObjectIterator<>() {
                private final Iterator<E> backg = backing.iterator();

                @Override
                public boolean hasNext() {
                    return backg.hasNext();
                }

                @Override
                public T next() {
                    return forward.apply(backg.next());
                }

                @Override
                public void remove() {
                    backg.remove();
                }
            };
        }
    }

    private static <T> Int2ObjectMap.Entry<T> intEntryForwards(Map.Entry<Integer, T> entry) {
        return new Int2ObjectMap.Entry<>() {
            @Override
            public T getValue() {
                return entry.getValue();
            }

            @Override
            public T setValue(T value) {
                return entry.setValue(value);
            }

            @Override
            public int getIntKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }
        };
    }

    private static <T> Map.Entry<Integer, T> intEntryBackwards(Int2ObjectMap.Entry<T> entry) {
        return entry;
    }

    private static <T> Long2ObjectMap.Entry<T> longEntryForwards(Map.Entry<Long, T> entry) {
        return new Long2ObjectMap.Entry<>() {
            @Override
            public T getValue() {
                return entry.getValue();
            }

            @Override
            public T setValue(T value) {
                return entry.setValue(value);
            }

            @Override
            public long getLongKey() {
                return entry.getKey();
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == entry) {
                    return true;
                }
                return super.equals(obj);
            }

            @Override
            public int hashCode() {
                return entry.hashCode();
            }
        };
    }

    private static <T> Map.Entry<Long, T> longEntryBackwards(Long2ObjectMap.Entry<T> entry) {
        return entry;
    }

    /**
     * Iterator adapting boxed {@link Integer} to FastUtil {@link IntIterator}.
     */
    static class WrappingIntIterator implements IntIterator {
        private final Iterator<Integer> backing;

        WrappingIntIterator(Iterator<Integer> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public int nextInt() {
            return backing.next();
        }

        @Override
        public void remove() {
            backing.remove();
        }
    }

    static class WrappingLongIterator implements LongIterator {
        private final Iterator<Long> backing;

        WrappingLongIterator(Iterator<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public long nextLong() {
            return backing.next();
        }

        @Override
        public void remove() {
            backing.remove();
        }
    }

    static class WrappingShortIterator implements ShortIterator {
        private final Iterator<Short> backing;

        WrappingShortIterator(Iterator<Short> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public short nextShort() {
            return backing.next();
        }

        @Override
        public void remove() {
            backing.remove();
        }
    }

    /**
     * Wraps a Java {@link Set} of boxed integers into a FastUtil {@link IntSet}.
     */
    public static class WrappingIntSet implements IntSet {
        private final Set<Integer> backing;

        /**
         * Creates a view backed by the given set.
         *
         * @param backing the set to wrap
         */
        public WrappingIntSet(Set<Integer> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean add(int key) {
            return backing.add(key);
        }

        @Override
        public boolean contains(int key) {
            return backing.contains(key);
        }

        @Override
        public int[] toIntArray() {
            return backing.stream().mapToInt(Integer::intValue).toArray();
        }

        @Override
        public int[] toArray(int[] a) {
            return ArrayUtils.toPrimitive(backing.toArray(new Integer[0]));
        }

        @Override
        public boolean addAll(IntCollection c) {
            return backing.addAll(c);
        }

        @Override
        public boolean containsAll(IntCollection c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean removeAll(IntCollection c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(IntCollection c) {
            return backing.retainAll(c);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public Object @NotNull [] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T @NotNull [] toArray(T @NotNull [] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Integer> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public @NotNull IntIterator iterator() {
            return new WrappingIntIterator(backing.iterator());
        }

        @Override
        public boolean remove(int k) {
            return backing.remove(k);
        }
    }

    /**
     * Adapter that exposes a {@link Set} of boxed longs as a FastUtil {@link LongSet}.
     * <p>
     * Backed directly by the original set.
     */
    public static class WrappingLongSet implements LongSet {
        private final Set<Long> backing;

        /**
         * Creates a view backed by the given set.
         *
         * @param backing the set to wrap
         */
        public WrappingLongSet(Set<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public boolean add(long key) {
            return backing.add(key);
        }

        @Override
        public boolean contains(long key) {
            return backing.contains(key);
        }

        @Override
        public long[] toLongArray() {
            return backing.stream().mapToLong(Long::longValue).toArray();
        }

        @Override
        public long[] toArray(long[] a) {
            if (a.length >= size()) {
                return null;
            } else {
                return toLongArray();
            }
        }

        @Override
        public boolean addAll(LongCollection c) {
            return backing.addAll(c);
        }

        @Override
        public boolean containsAll(LongCollection c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean removeAll(LongCollection c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(LongCollection c) {
            return backing.retainAll(c);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public Object @NotNull [] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T @NotNull [] toArray(T @NotNull [] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends Long> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public @NotNull LongIterator iterator() {
            return new WrappingLongIterator(backing.iterator());
        }

        @Override
        public boolean remove(long k) {
            return backing.remove(k);
        }
    }

    /**
     * Wraps the entry set of a {@link Map} with boxed {@link Integer} keys into a FastUtil entry set.
     *
     * @param map the backing map
     * @param <T> the value type
     * @return an entry set view backed by {@code map}
     */
    public static <T> ObjectSet<Int2ObjectMap.Entry<T>> entrySetIntWrap(Map<Integer, T> map) {
        return new ConvertingObjectSet<>(
                map.entrySet(),
                FastUtilHackUtil::intEntryForwards,
                FastUtilHackUtil::intEntryBackwards
        );
    }

    /**
     * Wraps the entry set of a {@link Map} with boxed {@link Long} keys into a FastUtil entry set.
     *
     * @param map the backing map
     * @param <T> the value type
     * @return an entry set view backed by {@code map}
     */
    public static <T> ObjectSet<Long2ObjectMap.Entry<T>> entrySetLongWrap(Map<Long, T> map) {
        return new ConvertingObjectSet<>(
                map.entrySet(),
                FastUtilHackUtil::longEntryForwards,
                FastUtilHackUtil::longEntryBackwards
        );
    }

    /**
     * Wraps a {@link Set} of boxed longs into a FastUtil {@link LongSet}.
     *
     * @param longset the backing set
     * @return a {@link LongSet} view backed by {@code longset}
     */
    public static LongSet wrapLongSet(Set<Long> longset) {
        return new WrappingLongSet(longset);
    }

    /**
     * Wraps a {@link Set} of boxed integers into a FastUtil {@link IntSet}.
     *
     * @param intset the backing set
     * @return an {@link IntSet} view backed by {@code intset}
     */
    public static IntSet wrapIntSet(Set<Integer> intset) {
        return new WrappingIntSet(intset);
    }

    /**
     * Wraps an {@link Iterator} of boxed shorts into a FastUtil {@link ShortIterator}.
     *
     * @param backing the backing iterator
     * @return a {@link ShortIterator} view backed by {@code backing}
     */
    public static ShortIterator itrShortWrap(Iterator<Short> backing) {
        return new WrappingShortIterator(backing);
    }

    /**
     * Returns a FastUtil {@link ShortIterator} over an {@link Iterable} of boxed shorts.
     *
     * @param backing the backing iterable
     * @return a {@link ShortIterator} over {@code backing}
     */
    public static ShortIterator itrShortWrap(Iterable<Short> backing) {
        return itrShortWrap(backing.iterator());
    }

    /**
     * Wraps an {@link Iterator} of boxed longs into a FastUtil {@link LongListIterator}.
     * <p>
     * The returned iterator is forward-only; the backward and index operations of
     * {@link LongListIterator} are not supported.
     *
     * @param c the backing iterator
     * @return a {@link LongListIterator} view backed by {@code c}
     */
    public static LongListIterator wrap(Iterator<Long> c) {
        return new SlimWrappingLongListIterator(c);
    }


    /**
     * Forward-only adapter that exposes an {@link Iterator} of boxed longs as a
     * FastUtil {@link LongListIterator}.
     * <p>
     * Backward and index operations are not supported.
     */
    public static class SlimWrappingLongListIterator implements LongListIterator {
        private final Iterator<Long> backing;

        SlimWrappingLongListIterator(Iterator<Long> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public long previousLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextLong() {
            return backing.next();
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public boolean hasPrevious() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int previousIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove() {
            backing.remove();
        }

    }

    /**
     * Adapter that exposes a {@link Collection} as a FastUtil {@link ObjectCollection}.
     * <p>
     * Backed directly by the original collection.
     *
     * @param <V> the element type
     */
    public static class WrappingObjectCollection<V> implements ObjectCollection<V> {
        private final Collection<V> backing;

        /**
         * Creates a view backed by the given collection.
         *
         * @param backing the collection to wrap
         */
        public WrappingObjectCollection(Collection<V> backing) {
            this.backing = Objects.requireNonNull(backing);
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return backing.contains(o);
        }

        @Override
        public Object @NotNull [] toArray() {
            return backing.toArray();
        }

        @Override
        public <T> T @NotNull [] toArray(T @NotNull [] a) {
            return backing.toArray(a);
        }

        @Override
        public boolean add(V e) {
            return backing.add(e);
        }

        @Override
        public boolean remove(Object o) {
            return backing.remove(o);
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return backing.containsAll(c);
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends V> c) {
            return backing.addAll(c);
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return backing.removeAll(c);
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return backing.retainAll(c);
        }

        @Override
        public void clear() {
            backing.clear();
        }

        @Override
        public @NotNull ObjectIterator<V> iterator() {
            return itrWrap(backing);
        }
    }

    /**
     * Wraps a {@link Collection} into a FastUtil {@link ObjectCollection}.
     *
     * @param c   the backing collection
     * @param <K> the element type
     * @return an {@link ObjectCollection} view backed by {@code c}
     */
    public static <K> ObjectCollection<K> wrap(Collection<K> c) {
        return new WrappingObjectCollection<>(c);
    }

    /**
     * Wraps an {@link Iterable} into a FastUtil {@link ObjectIterator}.
     */
    private record WrapperObjectIterator<T>(Iterator<T> parent) implements ObjectIterator<T> {
        private WrapperObjectIterator(Iterator<T> parent) {
            this.parent = Objects.requireNonNull(parent);
        }

        @Override
        public boolean hasNext() {
            return parent.hasNext();
        }

        @Override
        public T next() {
            return parent.next();
        }

        @Override
        public void remove() {
            parent.remove();
        }
    }

    /**
     * Returns a FastUtil {@link ObjectIterator} from an {@link Iterable}.
     *
     * @param in  the backing iterable
     * @param <T> the element type
     * @return an {@link ObjectIterator} over {@code in}
     */
    public static <T> ObjectIterator<T> itrWrap(Iterable<T> in) {
        return new WrapperObjectIterator<>(in.iterator());
    }
}