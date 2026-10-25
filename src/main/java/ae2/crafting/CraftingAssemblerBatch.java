package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.stacks.KeyAmounts;
import ae2.crafting.inv.ICraftingInventory;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

/**
 * A bounded sequence of validated grids, including arithmetically repeated state cycles.
 */
final class CraftingAssemblerBatch {
    private static final int MAX_KEYS = 4096;
    final KeyCounter consumed = KeyCounter.checked();
    final KeyCounter returned = KeyCounter.checked();
    final KeyCounter output = KeyCounter.checked();
    final KeyCounter peak = KeyCounter.checked();
    final KeyCounter delta = KeyCounter.checked();
    long crafts;

    static CraftingAssemblerBatch plan(CraftingAssemblerSimulation simulation,
                                       CraftingAssemblerSimulation.Result first, ICraftingInventory inventory, long limit) {
        var batch = new CraftingAssemblerBatch();
        var view = batch.view(inventory);
        // Brent-style checkpoints retain aggregate flows, not a per-craft path. Cache eviction therefore does
        // not turn a long but repeating durability/NBT cycle back into one iteration per requested craft.
        var checkpoint = first.inputs();
        var cycle = new CraftingAssemblerBatch();
        long window = 1, span = 0;
        var result = first;
        while (result != null && batch.crafts < limit && batch.consumed.size() < MAX_KEYS) {
            if (span > 0 && checkpoint.equals(result.inputs())) {
                long repeats = cycle.repeats(view, (limit - batch.crafts) / cycle.crafts);
                if (repeats > 0) batch.append(cycle, repeats);
                break;
            }
            if (span == window) {
                checkpoint = result.inputs();
                cycle = new CraftingAssemblerBatch();
                window = LongMath.saturatedMultiply(window, 2);
                span = 0;
            }
            long times = result.batchSize(view, limit - batch.crafts);
            if (times == 0) break;
            batch.append(result, times);
            cycle.append(result, times);
            span++;
            if (batch.crafts < limit) result = simulation.select(view, false);
        }
        return batch;
    }

    private void append(CraftingAssemblerSimulation.Result next, long times) {
        next.consumed().forEach((key, amount) -> {
            long needed = KeyAmounts.add(key, amount,
                KeyAmounts.multiply(key, Math.max(0, amount - next.output().get(key)), times - 1));
            peak.set(key, Math.max(peak.get(key), KeyAmounts.subtract(key, needed, delta.get(key))));
        });
        next.consumed().forEach((key, amount) -> {
            consumed.add(key, KeyAmounts.multiply(key, amount, times));
            delta.add(key, KeyAmounts.multiply(key, next.output().get(key) - amount, times));
        });
        next.returned().forEach((key, amount) -> returned.add(key, KeyAmounts.multiply(key, amount, times)));
        next.output().forEach((key, amount) -> {
            output.add(key, KeyAmounts.multiply(key, amount, times));
            if (next.consumed().get(key) == 0) delta.add(key, KeyAmounts.multiply(key, amount, times));
        });
        crafts = Math.addExact(crafts, times);
    }

    private void append(CraftingAssemblerBatch next, long times) {
        next.peak.forEach((key, amount) -> {
            long needed = KeyAmounts.add(key, amount,
                KeyAmounts.multiply(key, Math.max(0, -next.delta.get(key)), times - 1));
            peak.set(key, Math.max(peak.get(key), KeyAmounts.subtract(key, needed, delta.get(key))));
        });
        next.consumed.forEach((key, amount) -> consumed.add(key, KeyAmounts.multiply(key, amount, times)));
        next.returned.forEach((key, amount) -> returned.add(key, KeyAmounts.multiply(key, amount, times)));
        next.output.forEach((key, amount) -> output.add(key, KeyAmounts.multiply(key, amount, times)));
        next.delta.forEach((key, amount) -> delta.add(key, KeyAmounts.multiply(key, amount, times)));
        crafts = Math.addExact(crafts, Math.multiplyExact(next.crafts, times));
    }

    long repeats(ICraftingInventory inventory, long limit) {
        for (var entry : peak) {
            long held = inventory.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            if (held < entry.getLongValue()) return 0;
            long loss = -delta.get(entry.getKey());
            if (loss > 0) limit = Math.min(limit, (held - entry.getLongValue()) / loss + 1);
        }
        return limit;
    }

    long required(AEKey key, long times) {
        return KeyAmounts.add(key, peak.get(key),
            KeyAmounts.multiply(key, Math.max(0, -delta.get(key)), times - 1));
    }

    CraftingAssemblerBatch repeated(long times) {
        var result = new CraftingAssemblerBatch();
        result.append(this, times);
        return result;
    }

    void apply(ICraftingInventory inventory) {
        // The peak records every intermediate grid's simultaneous requirement, including unchanged seeds.
        peak.forEach((key, amount) -> inventory.extract(key, amount, Actionable.MODULATE));
        peak.forEach((key, amount) -> {
            if (amount > 0) inventory.insert(key, KeyAmounts.add(key, amount, delta.get(key)), Actionable.MODULATE);
        });
        delta.forEach((key, amount) -> {
            if (peak.get(key) == 0) inventory.insert(key, amount, Actionable.MODULATE);
        });
    }

    private ICraftingInventory view(ICraftingInventory inventory) {
        return new ICraftingInventory() {
            public void insert(AEKey key, long amount, Actionable mode) {
                throw new UnsupportedOperationException();
            }

            public long extract(AEKey key, long amount, Actionable mode) {
                if (mode != Actionable.SIMULATE) throw new UnsupportedOperationException();
                return Math.clamp(LongMath.saturatedAdd(
                    inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE), delta.get(key)), 0, amount);
            }

            public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                var result = new ObjectLinkedOpenHashSet<AEKey>();
                for (var variant : inventory.findFuzzyTemplates(key)) {
                    if (extract(variant, Long.MAX_VALUE, Actionable.SIMULATE) > 0) result.add(variant);
                }
                output.forEachFuzzy(key, FuzzyMode.IGNORE_ALL, (variant, amount) -> {
                    if (delta.get(variant) > 0) result.add(variant);
                });
                return result;
            }
        };
    }
}
