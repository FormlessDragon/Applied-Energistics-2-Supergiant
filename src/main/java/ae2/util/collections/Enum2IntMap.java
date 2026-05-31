package ae2.util.collections;

import it.unimi.dsi.fastutil.bytes.Byte2IntMap;
import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@SuppressWarnings("unused")
public class Enum2IntMap<E extends Enum<E>> extends AbstractReference2IntMap<E> {
    private final Byte2IntOpenHashMap delegate = new Byte2IntOpenHashMap();
    private final Helper<E> helper;

    public Enum2IntMap(Class<E> enumClass) {
        this(enumClass, 0);
    }

    public Enum2IntMap(Class<E> enumClass, int expected) {
        this.helper = new Helper<>(enumClass);
        this.delegate.defaultReturnValue(this.defRetValue);
        if (expected > 0) {
            this.delegate.ensureCapacity(expected);
        }
    }

    public Enum2IntMap(Class<E> enumClass, Map<? extends E, ? extends Integer> map) {
        this(enumClass, map.size());
        this.putAll(map);
    }

    public Enum2IntMap(Enum2IntMap<E> map) {
        this(map.helper.enumClass, map.size());
        this.defaultReturnValue(map.defaultReturnValue());
        this.putAll(map);
    }

    @Override
    public void defaultReturnValue(int rv) {
        super.defaultReturnValue(rv);
        this.delegate.defaultReturnValue(rv);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public void clear() {
        this.delegate.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.helper.isCompatibleKey(key) && this.delegate.containsKey(this.helper.encode(this.helper.requireKey(key)));
    }

    @Override
    public boolean containsValue(int value) {
        return this.delegate.containsValue(value);
    }

    @Override
    public int getInt(Object key) {
        if (!this.helper.isCompatibleKey(key)) {
            return this.defRetValue;
        }
        return this.delegate.get(this.helper.encode(this.helper.requireKey(key)));
    }

    @Override
    public int put(E key, int value) {
        return this.delegate.put(this.helper.encode(key), value);
    }

    @Override
    public int removeInt(Object key) {
        if (!this.helper.isCompatibleKey(key)) {
            return this.defRetValue;
        }
        return this.delegate.remove(this.helper.encode(this.helper.requireKey(key)));
    }

    @Override
    public ObjectSet<Reference2IntMap.Entry<E>> reference2IntEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NonNull ObjectIterator<Reference2IntMap.Entry<E>> iterator() {
                ObjectIterator<Byte2IntMap.Entry> delegateIterator = delegate.byte2IntEntrySet().iterator();
                return new AbstractObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return delegateIterator.hasNext();
                    }

                    @Override
                    public Reference2IntMap.Entry<E> next() {
                        return new EntryView(delegateIterator.next());
                    }

                    @Override
                    public void remove() {
                        delegateIterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public void clear() {
                delegate.clear();
            }

            @Override
            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry<?, ?> entry) || !(entry.getValue() instanceof Integer intValue)) {
                    return false;
                }
                if (!helper.isCompatibleKey(entry.getKey())) {
                    return false;
                }
                E key = helper.requireKey(entry.getKey());
                byte encoded = helper.encode(key);
                return delegate.containsKey(encoded) && delegate.get(encoded) == intValue;
            }

            @Override
            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry<?, ?> entry) || !(entry.getValue() instanceof Integer intValue)) {
                    return false;
                }
                if (!helper.isCompatibleKey(entry.getKey())) {
                    return false;
                }
                E key = helper.requireKey(entry.getKey());
                byte encoded = helper.encode(key);
                if (!delegate.containsKey(encoded) || delegate.get(encoded) != intValue) {
                    return false;
                }
                delegate.remove(encoded);
                return true;
            }
        };
    }

    @Override
    public @NonNull IntCollection values() {
        return this.delegate.values();
    }

    private static final class Helper<E extends Enum<E>> extends AbstractEnumPrimitiveMap<E> {
        private Helper(Class<E> enumClass) {
            super(enumClass);
        }
    }

    private final class EntryView implements Reference2IntMap.Entry<E> {
        private final Byte2IntMap.Entry delegateEntry;

        private EntryView(Byte2IntMap.Entry delegateEntry) {
            this.delegateEntry = delegateEntry;
        }

        @Override
        public E getKey() {
            return helper.decode(this.delegateEntry.getByteKey());
        }

        @Override
        public int getIntValue() {
            return this.delegateEntry.getIntValue();
        }

        @Override
        public int setValue(int value) {
            return this.delegateEntry.setValue(value);
        }

        @Override
        public int hashCode() {
            return this.delegateEntry.getByteKey() ^ getIntValue();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Enum2IntMap<?>.EntryView other) {
                return this.delegateEntry.getByteKey() == other.delegateEntry.getByteKey()
                    && getIntValue() == other.getIntValue();
            }
            if (!(obj instanceof Map.Entry<?, ?> other) || !(other.getValue() instanceof Integer otherValue)) {
                return false;
            }
            if (!helper.isCompatibleKey(other.getKey())) {
                return false;
            }
            return this.delegateEntry.getByteKey() == helper.encode(helper.requireKey(other.getKey()))
                && getIntValue() == otherValue;
        }

        @Override
        public String toString() {
            return getKey() + "->" + getIntValue();
        }
    }
}
