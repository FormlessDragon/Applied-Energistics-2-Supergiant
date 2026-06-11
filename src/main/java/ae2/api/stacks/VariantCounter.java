package ae2.api.stacks;

import ae2.api.config.FuzzyMode;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongSortedMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * Tallies a negative or positive amount for sub-variants of a {@link AEKey}.
 */
abstract class VariantCounter implements Iterable<Object2LongMap.Entry<AEKey>> {
    private final boolean saturating;

    protected VariantCounter(boolean saturating) {
        this.saturating = saturating;
    }

    public long get(AEKey key) {
        return this.getRecords().getOrDefault(key, 0);
    }

    public void add(AEKey key, long amount) {
        var records = getRecords();
        if (saturating) {
            long newAmount;
            long left = records.getLong(key);
            long result = left + amount;
            if (((left ^ result) & (amount ^ result)) < 0) {
                newAmount = result < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
            } else {
                newAmount = result;
            }
            records.put(key, newAmount);
        } else {
            records.addTo(key, amount);
        }
    }

    public void set(AEKey key, long amount) {
        getRecords().put(key, amount);
    }

    public long remove(AEKey key) {
        return getRecords().removeLong(key);
    }

    public void addAll(VariantCounter other) {
        for (var entry : other.getRecords().object2LongEntrySet()) {
            add(entry.getKey(), entry.getLongValue());
        }
    }

    public void removeAll(VariantCounter other) {
        for (var entry : other.getRecords().object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount == Long.MIN_VALUE) {
                add(entry.getKey(), Long.MAX_VALUE);
            } else {
                add(entry.getKey(), -amount);
            }
        }
    }

    public abstract Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy);

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
        for (var entry : getRecords().object2LongEntrySet()) {
            entry.setValue(0L);
        }
    }

    public void clear() {
        getRecords().clear();
    }

    public abstract VariantCounter copy(boolean saturating);

    public void invert() {
        for (var entry : getRecords().object2LongEntrySet()) {
            long amount = entry.getLongValue();
            entry.setValue(amount == Long.MIN_VALUE ? Long.MAX_VALUE : -amount);
        }
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
        private final AEKey2LongMap records = new AEKey2LongMap.OpenHashMap();

        UnorderedVariantMap(boolean saturating) {
            super(saturating);
        }

        /**
         * For keys whose primary key does not support fuzzy range lookups, we simply return all records, which amounts
         * to ignoring NBT.
         */
        @Override
        public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return records.object2LongEntrySet();
        }

        @Override
        AEKey2LongMap getRecords() {
            return records;
        }

        @Override
        public VariantCounter copy(boolean saturating) {
            var result = new UnorderedVariantMap(saturating);
            result.records.putAll(records);
            return result;
        }
    }

    /**
     * This variant list is optimized for damageable items, and supports selecting durability ranges with
     * {@link #findFuzzy}.
     */
    static class FuzzyVariantMap extends VariantCounter {
        private final AEKey2LongMap.AVLTreeMap records = FuzzySearch.createMap2Long();

        FuzzyVariantMap(boolean saturating) {
            super(saturating);
        }

        @Override
        public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy) {
            // The cast is necessary because the subMap in the call is not an instance of AEKey2LongMap.AVLTreeMap!
            return FuzzySearch.findFuzzy((Object2LongSortedMap<AEKey>) records, key, fuzzy).object2LongEntrySet();
        }

        @Override
        AEKey2LongMap getRecords() {
            return this.records;
        }

        @Override
        public VariantCounter copy(boolean saturating) {
            var result = new FuzzyVariantMap(saturating);
            result.records.putAll(records);
            return result;
        }
    }

}
