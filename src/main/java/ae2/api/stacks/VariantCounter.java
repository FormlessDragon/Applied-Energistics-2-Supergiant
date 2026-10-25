package ae2.api.stacks;

import ae2.api.config.FuzzyMode;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

/**
 * Tallies a negative or positive amount for sub-variants of a {@link AEKey}.
 */
abstract class VariantCounter implements Iterable<Object2LongMap.Entry<AEKey>> {
    private final KeyCounter.OverflowPolicy overflowPolicy;

    protected VariantCounter(KeyCounter.OverflowPolicy overflowPolicy) {
        this.overflowPolicy = overflowPolicy;
    }

    protected final long sum(AEKey key, long left, long right) {
        if (overflowPolicy == KeyCounter.OverflowPolicy.REJECT) return KeyAmounts.add(key, left, right);
        long result = left + right;
        if (overflowPolicy == KeyCounter.OverflowPolicy.SATURATE && ((left ^ result) & (right ^ result)) < 0) {
            return right < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return result;
    }

    public long get(AEKey key) {
        return this.getRecords().getOrDefault(key, 0);
    }

    public void add(AEKey key, long amount) {
        var records = getRecords();
        if (overflowPolicy == KeyCounter.OverflowPolicy.WRAP) {
            records.addTo(key, amount);
        } else {
            records.put(key, sum(key, records.getLong(key), amount));
        }
    }

    public void set(AEKey key, long amount) {
        getRecords().put(key, amount);
    }

    public long remove(AEKey key) {
        return getRecords().removeLong(key);
    }

    public void addAll(VariantCounter other) {
        other.forEachEntry(this::add);
    }

    public void removeAll(VariantCounter other) {
        other.forEachEntry((key, amount) -> {
            if (amount == Long.MIN_VALUE) {
                if (overflowPolicy == KeyCounter.OverflowPolicy.REJECT) KeyAmounts.subtract(key, 0, amount);
                add(key, Long.MAX_VALUE);
            } else {
                add(key, -amount);
            }
        });
    }

    public abstract Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy);

    @Override
    public void forEach(Consumer<? super Object2LongMap.Entry<AEKey>> action) {
        Objects.requireNonNull(action, "action");
        var records = getRecords();
        if (!records.isEmpty()) Object2LongMaps.fastForEach(records, action);
    }

    public void forEachEntry(ObjLongConsumer<? super AEKey> action) {
        Objects.requireNonNull(action, "action");
        var records = getRecords();
        if (!records.isEmpty()) records.forEachEntry(action);
    }

    public abstract void forEachFuzzy(AEKey filter, FuzzyMode fuzzy, ObjLongConsumer<? super AEKey> action);

    public int size() {
        return getRecords().size();
    }

    public boolean isEmpty() {
        return getRecords().isEmpty();
    }

    @Override
    public @NotNull Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        return Object2LongMaps.fastIterator(getRecords());
    }

    abstract AEKey2LongMap getRecords();

    /**
     * Sets all amounts to zero.
     */
    public void reset() {
        forEachEntry((key, amount) -> set(key, 0L));
    }

    public void clear() {
        getRecords().clear();
    }

    public abstract VariantCounter copy(KeyCounter.OverflowPolicy overflowPolicy);

    public void invert() {
        forEachEntry((key, amount) -> set(key, overflowPolicy == KeyCounter.OverflowPolicy.REJECT
            ? KeyAmounts.subtract(key, 0, amount) : amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount));
    }

    public void removeZeros() {
        var it = getRecords().values().iterator();
        while (it.hasNext()) {
            var entry = it.nextLong();
            if (entry == 0) {
                it.remove();
            }
        }
    }

    /**
     * This variant list is optimized for items that cannot be damaged and thus do not support querying durability
     * ranges via {@link #findFuzzy}.
     */
    static class UnorderedVariantMap extends VariantCounter {
        // Most temporary crafting counters have one variant per primary key. Keep that pair inline until
        // another variant or a mutable entry view needs the map. Once exposed, retain the map for live views.
        private @Nullable AEKey2LongMap records;
        private @Nullable AEKey singleKey;
        private long singleAmount;

        UnorderedVariantMap(KeyCounter.OverflowPolicy overflowPolicy) {
            this(overflowPolicy, 1);
        }

        private UnorderedVariantMap(KeyCounter.OverflowPolicy overflowPolicy, int expected) {
            super(overflowPolicy);
            if (expected > 1) this.records = new AEKey2LongMap.OpenHashMap(expected);
        }

        @Override
        public long get(AEKey key) {
            return records != null ? records.getLong(key) : key.equals(singleKey) ? singleAmount : 0;
        }

        @Override
        public void add(AEKey key, long amount) {
            if (records != null) {
                super.add(key, amount);
            } else if (singleKey == null) {
                singleKey = key;
                singleAmount = amount;
            } else if (singleKey.equals(key)) {
                singleAmount = sum(key, singleAmount, amount);
            } else {
                getRecords();
                super.add(key, amount);
            }
        }

        @Override
        public void set(AEKey key, long amount) {
            if (records != null) records.put(key, amount);
            else if (singleKey == null || singleKey.equals(key)) {
                singleKey = key;
                singleAmount = amount;
            } else getRecords().put(key, amount);
        }

        @Override
        public long remove(AEKey key) {
            if (records != null) return records.removeLong(key);
            if (!key.equals(singleKey)) return 0;
            long previous = singleAmount;
            singleKey = null;
            singleAmount = 0;
            return previous;
        }

        @Override
        public void forEachEntry(ObjLongConsumer<? super AEKey> action) {
            Objects.requireNonNull(action, "action");
            if (records != null) records.forEachEntry(action);
            else if (singleKey != null) action.accept(singleKey, singleAmount);
        }

        @Override
        public int size() {
            return records != null ? records.size() : singleKey == null ? 0 : 1;
        }

        @Override
        public boolean isEmpty() {
            return records != null ? records.isEmpty() : singleKey == null;
        }

        @Override
        public void clear() {
            if (records != null) records.clear();
            singleKey = null;
            singleAmount = 0;
        }

        @Override
        public void removeZeros() {
            if (records != null) super.removeZeros();
            else if (singleAmount == 0) singleKey = null;
        }

        /**
         * For keys whose primary key does not support fuzzy range lookups, we simply return all records, which amounts
         * to ignoring NBT.
         */
        @Override
        public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return getRecords().object2LongEntrySet();
        }

        @Override
        public void forEachFuzzy(AEKey filter, FuzzyMode fuzzy, ObjLongConsumer<? super AEKey> action) {
            forEachEntry(action);
        }

        @Override
        AEKey2LongMap getRecords() {
            if (records == null) {
                records = new AEKey2LongMap.OpenHashMap(1);
                if (singleKey != null) records.put(singleKey, singleAmount);
                singleKey = null;
                singleAmount = 0;
            }
            return records;
        }

        @Override
        public VariantCounter copy(KeyCounter.OverflowPolicy overflowPolicy) {
            var result = new UnorderedVariantMap(overflowPolicy, size());
            forEachEntry(result::set);
            return result;
        }
    }

    /**
     * This variant list is optimized for damageable items, and supports selecting durability ranges with
     * {@link #findFuzzy}.
     */
    static class FuzzyVariantMap extends VariantCounter {
        private final AEKey2LongMap.AVLTreeMap records = FuzzySearch.createMap2Long();

        FuzzyVariantMap(KeyCounter.OverflowPolicy overflowPolicy) {
            super(overflowPolicy);
        }

        @Override
        public void forEachFuzzy(AEKey filter, FuzzyMode fuzzy, ObjLongConsumer<? super AEKey> action) {
            records.forEachEntry((key, amount) -> {
                if (key.fuzzyEquals(filter, fuzzy)) action.accept(key, amount);
            });
        }

        @Override
        public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy) {
            // The comparator's range bounds only order by damage. Filter the selected entries with the public fuzzy
            // contract as a final step so equal damage and hash-collision keys cannot be lost at a range boundary.
            return new AbstractCollection<>() {
                @Override
                public int size() {
                    int result = 0;
                    for (var entry : records.object2LongEntrySet()) {
                        if (entry.getKey().fuzzyEquals(key, fuzzy)) {
                            result++;
                        }
                    }
                    return result;
                }

                @Override
                public @NotNull Iterator<Object2LongMap.Entry<AEKey>> iterator() {
                    return new Iterator<>() {
                        private Iterator<Object2LongMap.Entry<AEKey>> source = records.object2LongEntrySet().iterator();
                        private Object2LongMap.Entry<AEKey> next;
                        private Object2LongMap.Entry<AEKey> current;
                        private boolean advanced;

                        @Override
                        public boolean hasNext() {
                            if (next != null) {
                                return true;
                            }
                            while (source.hasNext()) {
                                var candidate = source.next();
                                advanced = true;
                                if (candidate.getKey().fuzzyEquals(key, fuzzy)) {
                                    next = candidate;
                                    return true;
                                }
                            }
                            return false;
                        }

                        @Override
                        public Object2LongMap.Entry<AEKey> next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            current = next;
                            next = null;
                            advanced = false;
                            return current;
                        }

                        @Override
                        public void remove() {
                            if (current == null) {
                                throw new IllegalStateException();
                            }
                            if (advanced) {
                                // hasNext may have moved past current, including past rejected entries. Remove the
                                // returned key and seek strictly after it so the AVL iterator cannot retain a
                                // deleted predecessor, and a prefetched match is neither skipped nor removed.
                                records.removeLong(current.getKey());
                                source = records.object2LongEntrySet().iterator(current);
                                next = null;
                            } else {
                                source.remove();
                            }
                            current = null;
                        }
                    };
                }
            };
        }

        @Override
        AEKey2LongMap getRecords() {
            return this.records;
        }

        @Override
        public VariantCounter copy(KeyCounter.OverflowPolicy overflowPolicy) {
            var result = new FuzzyVariantMap(overflowPolicy);
            result.records.putAll(records);
            return result;
        }
    }

}
