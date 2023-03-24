package com.contentgrid.gateway.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConcurrentLookup<K, V> {

    @NonNull
    private final Function<V, K> identityFunction;

    private final Set<Index<?, V>> indices = new HashSet<>();

    private final Map<K, V> data = new ConcurrentHashMap<>();

    public final V add(@NonNull V item) {
        var id = Objects.requireNonNull(this.identityFunction.apply(item), "identity(%s) is null".formatted(item));
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
    }

    public final V get(@NonNull K id) {
        return this.data.get(id);
    }

    public final <E extends Throwable> V remove(@NonNull K id)
            throws E {
        var old = this.data.remove(id);

        // remove the old item from the index
        if (old != null) {
            for (var index : this.indices) {
                index.remove(old);
            }
        }

        return old;
    }

    public final <L> Lookup<L, V> createLookup(Function<V, L> indexFunction) {
        var index = new LookupIndex<>(indexFunction);
        this.indices.add(index);

        // rebuild the index for existing data
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

    public interface Index<L, T> {
        List<T> get(L key);
        void store(T data);
        void remove(T data);
    }

    private static class LookupIndex<L, T> implements Index<L, T> {

        private final Map<L, List<T>> data = new HashMap<>();
        private final Function<T, L> indexFunction;

        LookupIndex(@NonNull Function<T, L> indexFunction) {
            this.indexFunction = indexFunction;
        }

        @Override
        public List<T> get(L key) {
            return List.copyOf(this.data.getOrDefault(key, List.of()));
        }

        @Override
        public void store(T data) {
            var key = this.indexFunction.apply(data);
            if (key != null) {
                this.data.computeIfAbsent(key, k -> new ArrayList<>()).add(data);
            }
        }

        @Override
        public void remove(T data) {
            var key = this.indexFunction.apply(data);
            var list = this.data.get(key);
            list.remove(data);
        }
    }
}
