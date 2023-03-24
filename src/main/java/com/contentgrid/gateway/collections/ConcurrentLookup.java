package com.contentgrid.gateway.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
public class ConcurrentLookup<ID, T> {

    @NonNull
    private final Function<T, ID> identityFunction;

    private final Set<IIndex<?, T>> indices = new HashSet<>();

    private final Map<ID, T> data = new ConcurrentHashMap<>();

    public final T add(@NonNull T item) {
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

    public final T get(@NonNull ID id) {
        return this.data.get(id);
    }

    public final <E extends Throwable> T remove(@NonNull ID id)
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

    public final <Key> Lookup<Key, T> createLookup(Function<T, Key> indexFunction) {
        var index = new LookupIndex<>(indexFunction);
        this.indices.add(index);

        // rebuild the index for existing data
        for (var item : this.data.values()) {
            index.store(item);
        }

        return index::get;
    }

    public Stream<T> stream() {
        return this.data.values().stream();
    }

    @FunctionalInterface
    public interface Lookup<Key, Value> extends Function<Key, Collection<Value>> {

    }
//
//    public final <Key> Function<Key, List<T>> addMultiIndex(Function<T, Stream<Key>> indexFunction) {
//        var index = new MultiIndex<Key, T>(indexFunction);
//        this.indices.add(index);
//
//        // rebuild the index
//        for (var item : this.data.values()) {
//            index.store(item);
//        }
//
//        return index::get;
//    }

//    /**
//     * Stores the data if no data with the same id exists yet. If data with the same id exists, throws the supplied
//     * {@code Throwable}.
//     *
//     * @param data              the data to store
//     * @param throwableSupplier provides the exception to be thrown, when data with the same id already exists
//     * @param <E>               the type of exception to be thrown
//     * @throws E the exception thrown when the data already exists
//     */
//    protected <E extends Throwable> void create(T data, Supplier<E> throwableSupplier) throws E {
//        if (this.get(identityFunction.apply(data)).isPresent()) {
//            throw throwableSupplier.get();
//        }
//
//        this.add(data);
//    }

//    protected <E extends Throwable> void update(T data, Supplier<E> throwableSupplier) throws E {
//        if (this.get(identityFunction.apply(data)) == null) {
//            throw throwableSupplier.get();
//        }
//
//        this.put(data);
//    }

    public interface IIndex<Key, T> {
        List<T> get(Key key);
        void store(T data);
        void remove(T data);
    }

    private static class LookupIndex<Key, T> implements IIndex<Key, T> {

        private final Map<Key, List<T>> data = new HashMap<>();
        private final Function<T, Key> indexFunction;

        LookupIndex(@NonNull Function<T, Key> indexFunction) {
            this.indexFunction = indexFunction;
        }

        @Override
        public List<T> get(Key key) {
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

    private static class MultiIndex<Key, T> implements IIndex<Key, T> {

        private final Map<Key, List<T>> data = new HashMap<>();
        private final Function<T, Stream<Key>> indexFunction;

        MultiIndex(@NonNull Function<T, Stream<Key>> indexFunction) {
            this.indexFunction = indexFunction;
        }

        @Override
        public List<T> get(Key key) {
            return this.data.getOrDefault(key, List.of());
        }

        @Override
        public void store(T data) {
            this.indexFunction.apply(data)
                    .forEachOrdered(key -> this.data.computeIfAbsent(key, k -> new ArrayList<>()).add(data));
        }

        @Override
        public void remove(T data) {
            this.indexFunction.apply(data).forEach(key -> {
                var list = this.data.get(key);
                list.remove(data);
            });
        }

    }

}
