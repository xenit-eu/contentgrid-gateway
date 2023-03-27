package com.contentgrid.gateway.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A thread-safe higher level data structure that wraps a map and supports creating multiple lookup indexes. Requires an
 * identity function for the data structure to be stored.
 *
 * @param <K> the type of id
 * @param <V> the type of the stored values
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcurrentLookup<K, V> {

    @NonNull
    private final Function<V, K> identityFunction;

    @NonNull
    private final ReadWriteLock readWriteLock;

    public ConcurrentLookup(Function<V, K> identityFunction) {
        this(identityFunction, new ReentrantReadWriteLock());
    }

    private final Set<Index<?, V>> indices = new HashSet<>();

    private final Map<K, V> data = new HashMap<>();

    public final V add(@NonNull V item) {
//    public final V put(@NonNull V item) {
        var id = Objects.requireNonNull(this.identityFunction.apply(item), "identity(%s) is null".formatted(item));
        var writeLock = this.readWriteLock.writeLock();

        try {
            writeLock.lock();

            var old = this.data.put(id, item);

            // update all the indices
            for (var index : this.indices) {

                // remove the old item from the index
                if (old != null) {
                    index.remove(old);
                }

                // store the new item in the index
                index.store(item);
            }

            return old;
        } finally {
            writeLock.unlock();
        }
    }

    public final V get(@NonNull K id) {
        var readlock = this.readWriteLock.readLock();
        try {
            readlock.lock();
            return this.data.get(id);
        } finally {
            readlock.unlock();
        }
    }

    public final V remove(@NonNull K id) {
        var writeLock = this.readWriteLock.writeLock();

        try {
            writeLock.lock();
            var old = this.data.remove(id);

            // remove the old item from the index
            if (old != null) {
                for (var index : this.indices) {
                    index.remove(old);
                }
            }

            return old;
        } finally {
            writeLock.unlock();
        }
    }


    public void clear() {
        var writeLock = this.readWriteLock.writeLock();

        try {
            writeLock.lock();
            this.data.clear();

            // clear indexes
            for (var index : this.indices) {
                index.clear();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public int size() {
        return this.data.size();
    }

    public final <L> Lookup<L, V> createLookup(Function<V, L> indexFunction) {
        var index = new LookupIndex<>(indexFunction);

        var writeLock = this.readWriteLock.writeLock();

        try {
            writeLock.lock();
            this.indices.add(index);

            // rebuild the index for existing data
            for (var item : this.data.values()) {
                index.store(item);
            }

            return index::get;
        } finally {
            writeLock.unlock();
        }
    }

    public final <L> Lookup<L, V> createMultiLookup(Function<V, Stream<L>> indexFunction) {
        var index = new MultiIndex<>(indexFunction);

        var writeLock = this.readWriteLock.writeLock();

        try {
            writeLock.lock();
            this.indices.add(index);

            // rebuild the index
            for (var item : this.data.values()) {
                index.store(item);
            }

            return index::get;
        } finally {
            writeLock.unlock();
        }
    }

    public final <L> Lookup<L, V> createMultiLookup(Function<V, Stream<L>> indexFunction) {
        var index = new MultiIndex<>(indexFunction);
        this.indices.add(index);

        // rebuild the index
        for (var item : this.data.values()) {
            index.store(item);
        }

        return index::get;
    }

    public Stream<V> stream() {
        return this.data.values().stream();
    }

    @FunctionalInterface
    public interface Lookup<L, V> extends Function<L, Collection<V>> {

    }

    private interface Index<L, T> {

        List<T> get(L key);

        void store(T data);

        void remove(T data);

        void clear();
    }

    private static class LookupIndex<L, T> implements Index<L, T> {

        private final Map<L, Set<T>> data = new HashMap<>();
        private final Function<T, L> indexFunction;

        LookupIndex(@NonNull Function<T, L> indexFunction) {
            this.indexFunction = indexFunction;
        }

        @Override
        public List<T> get(L key) {
            return List.copyOf(this.data.getOrDefault(key, Set.of()));
        }

        @Override
        public void store(T data) {
            var key = this.indexFunction.apply(data);
            if (key != null) {
                this.data.computeIfAbsent(key, k -> new HashSet<>()).add(data);
            }
        }

        @Override
        public void remove(T data) {
            var key = this.indexFunction.apply(data);
            var list = this.data.get(key);
            list.remove(data);
        }

        @Override
        public void clear() {
            this.data.clear();
        }
    }

    private static class MultiIndex<L, T> implements Index<L, T> {
        private final Map<L, Set<T>> data = new HashMap<>();
        private final Function<T, Stream<L>> indexFunction;

        MultiIndex(@NonNull Function<T, Stream<L>> indexFunction) {
            this.indexFunction = indexFunction;
        }

        @Override
        public List<T> get(L key) {
            return List.copyOf(this.data.getOrDefault(key, Set.of()));
        }

        @Override
        public void store(T data) {
            this.indexFunction.apply(data).forEachOrdered(key -> {
                Objects.requireNonNull(key, "key cannot be null");
                this.data.computeIfAbsent(key, k -> new HashSet<>()).add(data);
            });
        }

        @Override
        public void remove(T data) {
            this.indexFunction.apply(data).forEach(key -> {
                var list = this.data.get(Objects.requireNonNull(key));
                list.remove(data);
            });
        }

        @Override
        public void clear() {
            this.data.clear();
        }
    }
}