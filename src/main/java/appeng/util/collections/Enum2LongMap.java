package appeng.util.collections;

import it.unimi.dsi.fastutil.bytes.Byte2LongMap;
import it.unimi.dsi.fastutil.bytes.Byte2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@SuppressWarnings("unused")
public class Enum2LongMap<E extends Enum<E>> extends AbstractReference2LongMap<E> {
    private final Byte2LongOpenHashMap delegate = new Byte2LongOpenHashMap();
    private final Helper<E> helper;

    public Enum2LongMap(Class<E> enumClass) {
        this(enumClass, 0);
    }

    public Enum2LongMap(Class<E> enumClass, int expected) {
        this.helper = new Helper<>(enumClass);
        this.delegate.defaultReturnValue(this.defRetValue);
        if (expected > 0) {
            this.delegate.ensureCapacity(expected);
        }
    }

    public Enum2LongMap(Class<E> enumClass, Map<? extends E, ? extends Long> map) {
        this(enumClass, map.size());
        this.putAll(map);
    }

    public Enum2LongMap(Enum2LongMap<E> map) {
        this(map.helper.enumClass, map.size());
        this.defaultReturnValue(map.defaultReturnValue());
        this.putAll(map);
    }

    @Override
    public void defaultReturnValue(long rv) {
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
    public boolean containsValue(long value) {
        return this.delegate.containsValue(value);
    }

    @Override
    public long getLong(Object key) {
        if (!this.helper.isCompatibleKey(key)) {
            return this.defRetValue;
        }
        return this.delegate.get(this.helper.encode(this.helper.requireKey(key)));
    }

    @Override
    public long put(E key, long value) {
        return this.delegate.put(this.helper.encode(key), value);
    }

    @Override
    public long removeLong(Object key) {
        if (!this.helper.isCompatibleKey(key)) {
            return this.defRetValue;
        }
        return this.delegate.remove(this.helper.encode(this.helper.requireKey(key)));
    }

    @Override
    public ObjectSet<Reference2LongMap.Entry<E>> reference2LongEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NonNull ObjectIterator<Reference2LongMap.Entry<E>> iterator() {
                ObjectIterator<Byte2LongMap.Entry> delegateIterator = delegate.byte2LongEntrySet().iterator();
                return new AbstractObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return delegateIterator.hasNext();
                    }

                    @Override
                    public Reference2LongMap.Entry<E> next() {
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
                if (!(o instanceof Map.Entry<?, ?> entry) || !(entry.getValue() instanceof Long longValue)) {
                    return false;
                }
                if (!helper.isCompatibleKey(entry.getKey())) {
                    return false;
                }
                E key = helper.requireKey(entry.getKey());
                byte encoded = helper.encode(key);
                return delegate.containsKey(encoded) && delegate.get(encoded) == longValue;
            }

            @Override
            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry<?, ?> entry) || !(entry.getValue() instanceof Long longValue)) {
                    return false;
                }
                if (!helper.isCompatibleKey(entry.getKey())) {
                    return false;
                }
                E key = helper.requireKey(entry.getKey());
                byte encoded = helper.encode(key);
                if (!delegate.containsKey(encoded) || delegate.get(encoded) != longValue) {
                    return false;
                }
                delegate.remove(encoded);
                return true;
            }
        };
    }

    @Override
    public @NonNull LongCollection values() {
        return this.delegate.values();
    }

    private static final class Helper<E extends Enum<E>> extends AbstractEnumPrimitiveMap<E> {
        private Helper(Class<E> enumClass) {
            super(enumClass);
        }
    }

    private final class EntryView implements Reference2LongMap.Entry<E> {
        private final Byte2LongMap.Entry delegateEntry;

        private EntryView(Byte2LongMap.Entry delegateEntry) {
            this.delegateEntry = delegateEntry;
        }

        @Override
        public E getKey() {
            return helper.decode(this.delegateEntry.getByteKey());
        }

        @Override
        public long getLongValue() {
            return this.delegateEntry.getLongValue();
        }

        @Override
        public long setValue(long value) {
            return this.delegateEntry.setValue(value);
        }

        @SuppressWarnings("deprecation")
        @Deprecated
        @Override
        public Long getValue() {
            return this.delegateEntry.getLongValue();
        }

        @SuppressWarnings("deprecation")
        @Deprecated
        @Override
        public Long setValue(Long value) {
            return this.delegateEntry.setValue(value.longValue());
        }

        @Override
        public int hashCode() {
            return this.delegateEntry.getByteKey() ^ it.unimi.dsi.fastutil.HashCommon.long2int(getLongValue());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Enum2LongMap<?>.EntryView other) {
                return this.delegateEntry.getByteKey() == other.delegateEntry.getByteKey()
                    && getLongValue() == other.getLongValue();
            }
            if (!(obj instanceof Map.Entry<?, ?> other) || !(other.getValue() instanceof Long otherValue)) {
                return false;
            }
            if (!helper.isCompatibleKey(other.getKey())) {
                return false;
            }
            return this.delegateEntry.getByteKey() == helper.encode(helper.requireKey(other.getKey()))
                && getLongValue() == otherValue;
        }

        @Override
        public String toString() {
            return getKey() + "->" + getLongValue();
        }
    }
}
