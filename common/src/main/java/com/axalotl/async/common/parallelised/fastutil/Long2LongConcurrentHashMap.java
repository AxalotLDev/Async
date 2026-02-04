package com.axalotl.async.common.parallelised.fastutil;

import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Long2LongMap implementation using ConcurrentHashMap.
 * Designed for safe concurrent iteration and modification.
 */
public final class Long2LongConcurrentHashMap implements Long2LongMap {

    private final ConcurrentHashMap<Long, Long> backing = new ConcurrentHashMap<>();
    private volatile long defaultReturnValue = 0L;

    @Override
    public long get(long key) {
        Long value = backing.get(key);
        return value != null ? value : defaultReturnValue;
    }

    @Override
    public long put(long key, long value) {
        Long previous = backing.put(key, value);
        return previous != null ? previous : defaultReturnValue;
    }

    @Override
    public long remove(long key) {
        Long previous = backing.remove(key);
        return previous != null ? previous : defaultReturnValue;
    }

    @Override
    public boolean containsKey(long key) {
        return backing.containsKey(key);
    }

    @Override
    public boolean containsValue(long value) {
        return backing.containsValue(value);
    }

    @Override
    public void defaultReturnValue(long rv) {
        this.defaultReturnValue = rv;
    }

    @Override
    public long defaultReturnValue() {
        return defaultReturnValue;
    }

    @Override
    public ObjectSet<Entry> long2LongEntrySet() {
        return new EntrySet();
    }

    @Override
    public @NotNull LongSet keySet() {
        return new KeySet();
    }

    @Override
    public @NotNull LongCollection values() {
        return new ValueCollection();
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
    public void putAll(@NotNull Map<? extends Long, ? extends Long> m) {
        backing.putAll(m);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    // Inner classes for views

    private final class EntrySet extends AbstractObjectSet<Entry> implements ObjectSet<Entry> {
        @Override
        public @NotNull ObjectIterator<Entry> iterator() {
            final Iterator<Map.Entry<Long, Long>> backingIt = backing.entrySet().iterator();
            return new ObjectIterator<>() {
                private Map.Entry<Long, Long> current;

                @Override
                public boolean hasNext() {
                    return backingIt.hasNext();
                }

                @Override
                public Entry next() {
                    current = backingIt.next();
                    // Return Entry that writes back to the map
                    return new Entry() {
                        private final long key = current.getKey();

                        @Override
                        public long getLongKey() {
                            return key;
                        }

                        @Override
                        public long getLongValue() {
                            Long val = backing.get(key);
                            return val != null ? val : defaultReturnValue;
                        }

                        @Override
                        public long setValue(long value) {
                            Long old = backing.put(key, value);
                            return old != null ? old : defaultReturnValue;
                        }
                    };
                }

                @Override
                public void remove() {
                    backingIt.remove();
                }
            };
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Entry e) {
                Long value = backing.get(e.getLongKey());
                return value != null && value == e.getLongValue();
            }
            return false;
        }

        @Override
        public void clear() {
            backing.clear();
        }
    }

    private final class KeySet extends AbstractLongSet {
        @Override
        public @NotNull LongIterator iterator() {
            // Snapshot for safe iteration
            long[] keys = backing.keySet().stream().mapToLong(Long::longValue).toArray();
            return new LongIterator() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < keys.length;
                }

                @Override
                public long nextLong() {
                    return keys[index++];
                }
            };
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean contains(long key) {
            return backing.containsKey(key);
        }

        @Override
        public boolean remove(long key) {
            return backing.remove(key) != null;
        }

        @Override
        public void clear() {
            backing.clear();
        }
    }

    private final class ValueCollection extends AbstractLongCollection {
        @Override
        public @NotNull LongIterator iterator() {
            // Snapshot for safe iteration
            long[] values = backing.values().stream().mapToLong(Long::longValue).toArray();
            return new LongIterator() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < values.length;
                }

                @Override
                public long nextLong() {
                    return values[index++];
                }
            };
        }

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean contains(long value) {
            return backing.containsValue(value);
        }

        @Override
        public void clear() {
            backing.clear();
        }
    }
}