package ae2.util.collections;

import it.unimi.dsi.fastutil.bytes.Byte2ByteMap;
import it.unimi.dsi.fastutil.bytes.Byte2ByteOpenHashMap;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractReference2ByteMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ByteMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings("unused")
public class Enum2ByteMap<E extends Enum<E>> extends AbstractReference2ByteMap<E> {
    private final Byte2ByteOpenHashMap delegate = new Byte2ByteOpenHashMap();
    private final Helper<E> helper;

    public Enum2ByteMap(Class<E> enumClass) {
        this(enumClass, 0);
    }

    public Enum2ByteMap(Class<E> enumClass, int expected) {
        this.helper = new Helper<>(enumClass);
        this.delegate.defaultReturnValue(this.defRetValue);
        if (expected > 0) {
            this.delegate.ensureCapacity(expected);
        }
    }

    public Enum2ByteMap(Class<E> enumClass, Map<? extends E, ? extends Byte> map) {
        this(enumClass, map.size());
        this.putAll(map);
    }

    public Enum2ByteMap(Enum2ByteMap<E> map) {
        this(map.helper.enumClass, map.size());
        this.defaultReturnValue(map.defaultReturnValue());
        this.putAll(map);
    }

    @Override
    public void defaultReturnValue(byte rv) {
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
    public boolean containsValue(byte value) {
        return this.delegate.containsValue(value);
    }

    @Override
    public byte getByte(Object key) {
        if (!this.helper.isCompatibleKey(key)) {
            return this.defRetValue;
        }
        return this.delegate.get(this.helper.encode(this.helper.requireKey(key)));
    }

    @Override
    public byte put(E key, byte value) {
        return this.delegate.put(this.helper.encode(key), value);
    }

    @Override
    public byte removeByte(Object key) {
        if (!this.helper.isCompatibleKey(key)) {
            return this.defRetValue;
        }
        return this.delegate.remove(this.helper.encode(this.helper.requireKey(key)));
    }

    @Override
    public ObjectSet<Reference2ByteMap.Entry<E>> reference2ByteEntrySet() {
        return new AbstractObjectSet<>() {
            @Override
            public @NotNull ObjectIterator<Reference2ByteMap.Entry<E>> iterator() {
                ObjectIterator<Byte2ByteMap.Entry> delegateIterator = delegate.byte2ByteEntrySet().iterator();
                return new AbstractObjectIterator<>() {
                    @Override
                    public boolean hasNext() {
                        return delegateIterator.hasNext();
                    }

                    @Override
                    public Reference2ByteMap.Entry<E> next() {
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
                if (!(o instanceof Map.Entry<?, ?> entry) || !(entry.getValue() instanceof Byte byteValue)) {
                    return false;
                }
                if (!helper.isCompatibleKey(entry.getKey())) {
                    return false;
                }
                E key = helper.requireKey(entry.getKey());
                byte encoded = helper.encode(key);
                return delegate.containsKey(encoded) && delegate.get(encoded) == byteValue;
            }

            @Override
            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry<?, ?> entry) || !(entry.getValue() instanceof Byte byteValue)) {
                    return false;
                }
                if (!helper.isCompatibleKey(entry.getKey())) {
                    return false;
                }
                E key = helper.requireKey(entry.getKey());
                byte encoded = helper.encode(key);
                if (!delegate.containsKey(encoded) || delegate.get(encoded) != byteValue) {
                    return false;
                }
                delegate.remove(encoded);
                return true;
            }
        };
    }

    @Override
    public @NotNull ByteCollection values() {
        return this.delegate.values();
    }

    private static final class Helper<E extends Enum<E>> extends AbstractEnumPrimitiveMap<E> {
        private Helper(Class<E> enumClass) {
            super(enumClass);
        }
    }

    private final class EntryView implements Reference2ByteMap.Entry<E> {
        private final Byte2ByteMap.Entry delegateEntry;

        private EntryView(Byte2ByteMap.Entry delegateEntry) {
            this.delegateEntry = delegateEntry;
        }

        @Override
        public E getKey() {
            return helper.decode(this.delegateEntry.getByteKey());
        }

        @Override
        public byte getByteValue() {
            return this.delegateEntry.getByteValue();
        }

        @Override
        public byte setValue(byte value) {
            return this.delegateEntry.setValue(value);
        }

        @Override
        public int hashCode() {
            return getKey().hashCode() ^ getByteValue();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Enum2ByteMap<?>.EntryView other) {
                return getKey() == other.getKey()
                    && getByteValue() == other.getByteValue();
            }
            if (!(obj instanceof Map.Entry<?, ?> other) || !(other.getValue() instanceof Byte otherValue)) {
                return false;
            }
            if (!helper.isCompatibleKey(other.getKey())) {
                return false;
            }
            return this.delegateEntry.getByteKey() == helper.encode(helper.requireKey(other.getKey()))
                && getByteValue() == otherValue;
        }

        @Override
        public String toString() {
            return getKey() + "->" + getByteValue();
        }
    }
}