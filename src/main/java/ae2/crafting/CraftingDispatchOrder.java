package ae2.crafting;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.crafting.inv.ICraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * A CPU replay certificate for order-sensitive stock conversions. A batch repeats a short sequence without
 * expanding it per craft. Only accepted pushes advance the certificate, so delayed outputs and restored pattern
 * instances cannot change its execution order. Unordered batches form barriers around ordinary sub-plans when
 * jobs are merged; their independently supplied stock remains available to the later batches.
 */
public final class CraftingDispatchOrder {
    public record Step(IPatternDetails pattern, long crafts) {
        public Step {
            if (pattern == null || crafts <= 0) throw new IllegalArgumentException("Invalid dispatch step");
        }
    }

    public record Batch(List<Step> steps, long repetitions, boolean ordered) {
        public Batch {
            steps = List.copyOf(steps);
            if (steps.isEmpty() || repetitions <= 0) throw new IllegalArgumentException("Invalid dispatch batch");
            if (!ordered && new HashSet<>(steps.stream().map(Step::pattern).toList()).size() != steps.size()) {
                throw new IllegalArgumentException("Duplicate unordered dispatch task");
            }
        }
    }

    private final ArrayDeque<Batch> batches = new ArrayDeque<>();
    private long[] remaining;
    private long repetitions;
    private int cursor;
    private CraftingSeedReservations seeds;
    private final Object2IntOpenHashMap<IPatternDetails> indices = new Object2IntOpenHashMap<>();

    public CraftingDispatchOrder(List<Batch> batches) {
        this.batches.addAll(batches);
        this.indices.defaultReturnValue(-1);
        startBatch();
    }

    public static List<Batch> forPlan(ICraftingPlan plan) {
        if (plan instanceof CraftingPlan concrete) return concrete.dispatchOrder();
        if (plan instanceof BatchCraftingPlan batch) return batch.dispatchOrder();
        return List.of();
    }

    public static List<Batch> unordered(Iterable<? extends IPatternDetails> patterns, ToLongFunction<IPatternDetails> count) {
        var steps = new ArrayList<Step>();
        for (var pattern : patterns) {
            long crafts = count.applyAsLong(pattern);
            if (crafts > 0) steps.add(new Step(pattern, crafts));
        }
        return steps.isEmpty() ? List.of() : List.of(new Batch(steps, 1, false));
    }

    public void append(ICraftingPlan plan) {
        var order = forPlan(plan);
        boolean wasEmpty = this.batches.isEmpty();
        this.batches.addAll(order.isEmpty() ? unordered(plan.patternTimes().keySet(), plan.patternTimes()::getLong) : order);
        if (wasEmpty) startBatch();
    }

    private void startBatch() {
        this.cursor = 0;
        this.seeds = null;
        this.indices.clear();
        var batch = this.batches.peekFirst();
        if (batch == null) {
            this.remaining = new long[0];
            this.repetitions = 0;
            return;
        }
        this.remaining = batch.steps.stream().mapToLong(Step::crafts).toArray();
        this.repetitions = batch.repetitions;
        if (!batch.ordered) for (int i = 0; i < batch.steps.size(); i++) this.indices.put(batch.steps.get(i).pattern, i);
    }

    private int index(IPatternDetails pattern) {
        var batch = this.batches.peekFirst();
        if (batch == null) return -1;
        if (batch.ordered) return batch.steps.get(this.cursor).pattern.equals(pattern) ? this.cursor : -1;
        return this.indices.getInt(pattern);
    }

    public int limit(IPatternDetails pattern, int maximum) {
        int index = index(pattern);
        return index < 0 ? 0 : (int) Math.min(maximum, this.remaining[index]);
    }

    public ICraftingInventory protect(IPatternDetails pattern, ICraftingInventory inventory) {
        var batch = this.batches.peekFirst();
        // The ordered certificate already accounts for every input. Rebuilding SCC reservations from only the
        // undispatched tail can split the original cycle and forbid a step proven safe by the replay.
        if (batch == null || batch.ordered) return inventory;
        if (this.seeds == null) {
            this.seeds = new CraftingSeedReservations(batch.steps.stream().map(Step::pattern).toList(), p -> {
                int index = index(p);
                return index < 0 ? 0 : this.remaining[index];
            });
        }
        return this.seeds.protect(pattern, inventory);
    }

    public void accepted(IPatternDetails pattern, int count) {
        int index = index(pattern);
        if (index < 0 || count <= 0 || count > this.remaining[index]) {
            throw new IllegalStateException("Push does not match dispatch certificate");
        }
        this.remaining[index] -= count;
        while (this.cursor < this.remaining.length && this.remaining[this.cursor] == 0) this.cursor++;
        if (this.cursor < this.remaining.length) return;
        if (--this.repetitions > 0) {
            this.remaining = this.batches.getFirst().steps.stream().mapToLong(Step::crafts).toArray();
            this.cursor = 0;
            this.seeds = null;
        } else {
            this.batches.removeFirst();
            startBatch();
        }
    }

    public NBTTagList writeToNBT(ToIntFunction<IPatternDetails> patternIndex) {
        var list = new NBTTagList();
        boolean first = true;
        for (var batch : this.batches) {
            if (first) {
                var tail = new ArrayList<Step>();
                for (int i = 0; i < this.remaining.length; i++) {
                    if (this.remaining[i] > 0) tail.add(new Step(batch.steps.get(i).pattern, this.remaining[i]));
                }
                writeBatch(list, new Batch(tail, 1, batch.ordered), patternIndex);
                if (this.repetitions > 1) writeBatch(list, new Batch(batch.steps, this.repetitions - 1, batch.ordered), patternIndex);
                first = false;
            } else writeBatch(list, batch, patternIndex);
        }
        return list;
    }

    private static void writeBatch(NBTTagList list, Batch batch, ToIntFunction<IPatternDetails> patternIndex) {
        var tag = new NBTTagCompound();
        tag.setLong("repetitions", batch.repetitions);
        tag.setBoolean("ordered", batch.ordered);
        var steps = new NBTTagList();
        for (var step : batch.steps) {
            var item = new NBTTagCompound();
            int index = patternIndex.applyAsInt(step.pattern);
            if (index < 0) throw new IllegalStateException("Dispatch certificate refers to an unknown task");
            item.setInteger("task", index);
            item.setLong("crafts", step.crafts);
            steps.appendTag(item);
        }
        tag.setTag("steps", steps);
        list.appendTag(tag);
    }

    public static CraftingDispatchOrder readFromNBT(NBTTagList list, List<IPatternDetails> patterns,
                                                    ToLongFunction<IPatternDetails> remaining) {
        var batches = new ArrayList<Batch>();
        var counts = new Object2LongOpenHashMap<IPatternDetails>();
        for (int i = 0; i < list.tagCount(); i++) {
            var tag = list.getCompoundTagAt(i);
            if (!tag.hasKey("ordered", Constants.NBT.TAG_BYTE) || !tag.hasKey("repetitions", Constants.NBT.TAG_LONG)
                || !tag.hasKey("steps", Constants.NBT.TAG_LIST)) throw new IllegalArgumentException("Incomplete dispatch batch");
            var entries = tag.getTagList("steps", Constants.NBT.TAG_COMPOUND);
            var steps = new ArrayList<Step>();
            for (int j = 0; j < entries.tagCount(); j++) {
                var item = entries.getCompoundTagAt(j);
                if (!item.hasKey("task", Constants.NBT.TAG_INT) || !item.hasKey("crafts", Constants.NBT.TAG_LONG)) {
                    throw new IllegalArgumentException("Incomplete dispatch step");
                }
                int index = item.getInteger("task");
                if (index < 0 || index >= patterns.size()) throw new IllegalArgumentException("Invalid dispatch task index");
                steps.add(new Step(patterns.get(index), item.getLong("crafts")));
            }
            var batch = new Batch(steps, tag.getLong("repetitions"), tag.getBoolean("ordered"));
            for (var step : steps) counts.put(step.pattern, Math.addExact(counts.getLong(step.pattern), Math.multiplyExact(step.crafts, batch.repetitions)));
            batches.add(batch);
        }
        for (var pattern : patterns) if (counts.getLong(pattern) != remaining.applyAsLong(pattern)) {
            throw new IllegalArgumentException("Dispatch certificate does not match remaining tasks");
        }
        return new CraftingDispatchOrder(batches);
    }
}
