package ae2.util;

import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Thread-safe copy-on-write map wrapper. Does not accept null keys or values.
 */
public class CowReference2LongMap<K> {
    private final IntFunction<? extends Reference2LongMap<K>> mapSupplier;
    private volatile Reference2LongMap<K> map;

    private CowReference2LongMap(IntFunction<? extends Reference2LongMap<K>> mapSupplier) {
        this.mapSupplier = mapSupplier;
        this.map = Reference2LongMaps.unmodifiable(mapSupplier.apply(0));
    }

    public static <K> CowReference2LongMap<K> newMap() {
        return new CowReference2LongMap<>(Reference2LongOpenHashMap::new);
    }

    /**
     * Add the value to the map, or throw an IllegalArgumentException if it is already present.
     */
    public void putIfAbsent(K key, long value) throws IllegalArgumentException {
        Objects.requireNonNull(key, "Key may not be null");

        synchronized (this) {
            if (map.containsKey(key)) {
                throw new IllegalArgumentException("Map already contains a value for the following key: " + key);
            }
            var newMap = mapSupplier.apply(map.size() + 1);
            newMap.putAll(map);
            newMap.put(key, value);
            map = Reference2LongMaps.unmodifiable(newMap);
        }
    }

    public void modifyValue(K key, long value) throws IllegalArgumentException {
        Objects.requireNonNull(key, "Key may not be null");

        synchronized (this) {
            if (!map.containsKey(key)) {
                throw new IllegalArgumentException("Map already contains a value for the following key: " + key);
            }
            var newMap = mapSupplier.apply(map.size());
            newMap.putAll(map);
            newMap.put(key, value);
            map = Reference2LongMaps.unmodifiable(newMap);
        }
    }

    /**
     * Return the current unmodifiable map. Further additions will not be reflected in the returned object.
     */
    public Reference2LongMap<K> getMap() {
        return map;
    }
}
