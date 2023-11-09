package com.contentgrid.gateway.collections;

import static reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import lombok.experimental.Delegate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;


public class ObservableMap<K, V> implements Map<K, V>, AutoCloseable {

    @Delegate(types = MapReadOperations.class)
    private final Map<K, V> map = new ConcurrentHashMap<>();

    private final Sinks.Many<MapUpdate<K, V>> updates = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public V get(Object key) {
        return this.map.get(key);
    }

    @Override
    public V put(K key, V value) {
        V previousValue = map.put(key, value);
        updates.emitNext(MapUpdate.put(key, value), FAIL_FAST);
        return previousValue;
    }

    @Override
    public Set<K> keySet() {
        return this.map.keySet();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        map.forEach(this::put);
    }

    @Override
    public void clear() {
        this.map.clear();
        updates.emitNext(MapUpdate.clear(), FAIL_FAST);
    }

    @Override
    public V remove(Object key) {
        V removedValue = map.remove(key);
        if (removedValue != null) {
            updates.emitNext(MapUpdate.remove(key, removedValue), FAIL_FAST);
        }
        return removedValue;
    }

    public Flux<MapUpdate<K, V>> observe() {
        return Flux.concat(
                Flux.fromIterable(map.entrySet()).map(entry -> MapUpdate.put(entry.getKey(), entry.getValue())),
                updates.asFlux()
        );
    }

    @Override
    public void close() {
        this.updates.emitComplete(FAIL_FAST);
    }

    public enum UpdateType {
        PUT, REMOVE, CLEAR
    }

    @Value
    public static class MapUpdate<K, V> {

        UpdateType type;
        Object key;
        V value;

        @Override
        public String toString() {
            return switch (type) {
                case PUT -> "put(%s,%s)".formatted(key, value);
                case REMOVE -> "remove(%s) (old:%s)".formatted(key, value);
                case CLEAR -> "clear()";
            };
        }

        public static <K,V> MapUpdate<K,V> put(K key, V value) {
            return new MapUpdate<>(UpdateType.PUT, key, value);
        }

        public static <K,V> MapUpdate<K,V> remove(Object key, V oldValue) {
            return new MapUpdate<>(UpdateType.REMOVE, key, oldValue);
        }

        public static <K,V> MapUpdate<K,V> clear() {
            return new MapUpdate<>(UpdateType.CLEAR, null, null);
        }

        @SuppressWarnings("unchecked")
        public K getKey() {
            return (K) this.key;
        }

        public Object getRawKey() {
            return this.key;
        }
    }

    @SuppressWarnings("unused")
    private interface MapReadOperations<K, V> {

        int size();

        boolean isEmpty();

        boolean containsKey(Object key);

        boolean containsValue(Object value);

        Collection<V> values();

        Set<Entry<K, V>> entrySet();
    }
}
