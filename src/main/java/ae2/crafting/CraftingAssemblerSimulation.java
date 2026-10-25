package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.execution.CraftingCpuHelper;
import ae2.crafting.inv.ICraftingInventory;
import ae2.crafting.pattern.AECraftingPattern;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Complete-grid allocation and deterministic state transitions. Caches belong to one calculation, never a world.
 */
public final class CraftingAssemblerSimulation {
    private static final int CACHE_SIZE = 256;
    private static final int MAX_GRID_CHOICES = 65_536;
    private final IAssemblerPattern pattern;
    private final World level;
    private final Map<List<List<GenericStack>>, Evaluation> results = boundedCache();
    private final Map<Validity, Boolean> valid = boundedCache();
    private final Map<Selection, Result> selections = boundedCache();
    private @Nullable Grid scratch;
    private boolean defaultEvaluated;
    private @Nullable Evaluation defaultEvaluation;

    CraftingAssemblerSimulation(IAssemblerPattern pattern, World level) {
        this.pattern = pattern;
        this.level = level;
    }

    /**
     * CPU execution bridge; mutable simulation state and selected results stay inside the crafting package.
     */
    public static KeyCounter @Nullable [] extractInputs(IAssemblerPattern assembler, ICraftingInventory source,
                                                        World level, KeyCounter outputs, KeyCounter containers,
                                                        int multiplier) {
        if (multiplier <= 0) return null;
        var simulation = new CraftingAssemblerSimulation(assembler, level);
        // A provider receives per-slot bags, not a sequence of grids. Keep a merged push homogeneous so
        // independently draining those bags cannot recombine two valid grids into an invalid one.
        var scaled = new ICraftingInventory() {
            public void insert(AEKey key, long amount, Actionable mode) {
                throw new UnsupportedOperationException();
            }

            public long extract(AEKey key, long amount, Actionable mode) {
                if (mode != Actionable.SIMULATE) throw new UnsupportedOperationException();
                return Math.min(amount, source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) / multiplier);
            }

            public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                return source.findFuzzyTemplates(key);
            }
        };
        var choice = simulation.select(scaled, false);
        if (choice == null) return null;
        var table = new KeyCounter[assembler.getInputs().length];
        for (int i = 0; i < table.length; i++) {
            table[i] = new KeyCounter();
            for (var stack : choice.inputs().get(i)) {
                long amount = LongMath.saturatedMultiply(stack.amount(), multiplier);
                long extracted = source.extract(stack.what(), amount, Actionable.MODULATE);
                table[i].add(stack.what(), extracted);
                if (extracted != amount) {
                    CraftingCpuHelper.reinjectPatternInputs(source, table);
                    return null;
                }
            }
        }
        choice.returned().forEach((key, amount) -> containers.add(key, LongMath.saturatedMultiply(amount, multiplier)));
        for (var output : assembler.getOutputs())
            outputs.add(output.what(),
                LongMath.saturatedMultiply(output.amount(), multiplier));
        return table;
    }

    private static <K, V> Map<K, V> boundedCache() {
        return new Object2ObjectLinkedOpenHashMap<>(0) {
            @Override
            public V put(K key, V value) {
                V previous = putAndMoveToLast(key, value);
                if (size() > CACHE_SIZE) removeFirst();
                return previous;
            }
        };
    }

    @Nullable Result select(ICraftingInventory inventory, boolean allowMissing) {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(() -> select(inventory, allowMissing));
        }
        var inputs = pattern.getInputs();
        long capacity = 0;
        for (var input : inputs) {
            long largest = 0;
            for (var possible : input.possibleInputs()) largest = Math.max(largest, possible.amount());
            capacity = LongMath.saturatedAdd(capacity, LongMath.saturatedMultiply(largest, input.getMultiplier()));
        }
        var available = new Object2LongOpenHashMap<AEKey>();
        var choices = new ObjectArrayList<List<GenericStack>>(inputs.length);
        for (var input : inputs) {
            var options = new ObjectArrayList<GenericStack>();
            var seen = new ObjectOpenHashSet<GenericStack>();
            for (var possible : input.possibleInputs()) {
                for (var key : inventory.findFuzzyTemplates(possible.what())) {
                    if (!available.containsKey(key)) {
                        available.put(key, inventory.extract(key, capacity, Actionable.SIMULATE));
                    }
                    if (available.getLong(key) < possible.amount()) continue;
                    var option = new GenericStack(key, possible.amount());
                    if (seen.add(option) && accepts(input, key)) options.add(option);
                }
            }
            options.sort(Comparator.comparingInt(o -> o.what() instanceof AEItemKey item && item.getItem().isDamageable()
                ? item.toStack().getItemDamage() : 0));
            if (allowMissing && input.possibleInputs().length > 0) {
                var first = input.possibleInputs()[0];
                if (!available.containsKey(first.what())) {
                    available.put(first.what(), inventory.extract(first.what(), capacity, Actionable.SIMULATE));
                }
                if (!options.contains(first)) options.add(first);
            }
            if (options.isEmpty()) return null;
            choices.add(options);
        }
        // Only simultaneous input capacity matters to a grid choice. Larger stock counts may change between
        // crafts without changing this search. New variants, depleted inputs and missing-item mode remain in the key.
        var selection = new Selection(choices, available, allowMissing);
        if (selections.containsKey(selection)) return selections.get(selection);
        var order = new Integer[inputs.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> choices.get(i).size()));
        var search = new Search(available, inputs, choices, order, allowMissing);
        search.visit(0, 0);
        var result = search.best == null ? null : result(search.bestInputs, search.best);
        // A large search may have evicted its winning grid. Keep that evaluation for narrower replay inventories.
        if (search.best != null) results.put(search.bestInputs, search.best);
        selections.put(selection, result);
        // An exhaustive missing-mode search that found a fully stocked grid also solved real-stock mode,
        // provided that mode enumerates exactly the same choices. A truncated search cannot prove this.
        if (allowMissing && search.bestMissing == 0 && search.completeChoices < MAX_GRID_CHOICES) {
            selections.put(new Selection(choices, available, false), result);
        }
        return result;
    }

    private boolean accepts(IPatternDetails.IInput input, AEKey key) {
        var probe = new Validity(input, key);
        var cached = valid.get(probe);
        if (cached != null) return cached;
        boolean accepted = pattern instanceof AECraftingPattern nativePattern
            ? nativePattern.isInputCandidate(input, key) : input.isValid(key, level);
        valid.put(probe, accepted);
        return accepted;
    }

    boolean acceptsReplacement(AEKey seed, AEKey returned) {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(() -> acceptsReplacement(seed, returned));
        }
        for (var input : pattern.getInputs()) {
            if (Arrays.stream(input.possibleInputs()).anyMatch(p -> p.what().equals(seed)) && accepts(input, returned))
                return true;
        }
        return false;
    }

    long defaultRemainderAmount(AEKey key) {
        var remainders = defaultRemainders();
        return remainders == null ? 0 : remainders.get(key);
    }

    @Nullable KeyCounter defaultRemainders() {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(this::defaultRemainders);
        }
        var result = defaultEvaluation;
        if (!defaultEvaluated) {
            var inputs = pattern.getInputs();
            var selected = new ObjectArrayList<List<GenericStack>>(inputs.length);
            for (var input : inputs) {
                var possible = input.possibleInputs();
                var bag = new ObjectArrayList<GenericStack>(possible.length == 0 ? 0 : 1);
                if (possible.length > 0) bag.add(new GenericStack(possible[0].what(),
                    LongMath.saturatedMultiply(possible[0].amount(), input.getMultiplier())));
                selected.add(bag);
            }
            defaultEvaluation = result = evaluate(selected);
            defaultEvaluated = true;
        }
        return result == null ? null : result.returned;
    }

    boolean hasFixedResultWithoutRemainders() {
        for (var input : pattern.getInputs()) {
            if (input.possibleInputs().length > 1) return false;
        }
        var returned = defaultRemainders();
        return returned != null && returned.isEmpty();
    }

    private @Nullable Evaluation evaluate(List<List<GenericStack>> inputs) {
        if (results.containsKey(inputs)) return results.get(inputs);
        if (scratch == null) scratch = new Grid(inputs.size());
        var evaluated = evaluateUncached(inputs, scratch);
        results.put(inputs, evaluated);
        return evaluated;
    }

    private @Nullable Evaluation evaluateUncached(List<List<GenericStack>> inputs, Grid scratch) {
        // The adapter may consume its input table or write only occupied grid slots. Reset both before reuse.
        // No scratch counter or ItemStack is retained in an evaluation or a selected result.
        scratch.fill(inputs);
        var grid = scratch.inventory;
        pattern.fillCraftingGrid(scratch.table, grid::setInventorySlotContents);
        if (!pattern.getPrimaryOutput().equals(GenericStack.fromItemStack(pattern.assemble(grid, level)))) return null;
        var returns = new KeyCounter();
        for (var remaining : pattern.getRemainingItems(grid)) {
            var stack = GenericStack.fromItemStack(remaining);
            if (stack != null) returns.add(stack.what(), stack.amount());
        }
        long reusable = 0;
        for (var returned : returns) {
            for (var input : pattern.getInputs()) {
                if (accepts(input, returned.getKey())) {
                    reusable = LongMath.saturatedAdd(reusable, returned.getLongValue());
                    break;
                }
            }
        }
        return new Evaluation(returns, reusable);
    }

    private Result result(List<List<GenericStack>> inputs, Evaluation evaluated) {
        // Only the selected grid needs full material accounting. Candidate evaluations retain their returns alone.
        var consumed = new KeyCounter();
        for (var bag : inputs) {
            for (var stack : bag) consumed.add(stack.what(), stack.amount());
        }
        var output = new KeyCounter();
        output.addAll(evaluated.returned);
        for (var stack : pattern.getOutputs()) output.add(stack.what(), stack.amount());
        return new Result(inputs, consumed, evaluated.returned, output);
    }

    /**
     * Required stock before this same complete grid can execute a sequential batch.
     */
    long required(Result result, AEKey key, long crafts) {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(() -> required(result, key, crafts));
        }
        long in = result.consumed.get(key);
        for (var input : pattern.getInputs()) {
            if (Arrays.stream(input.possibleInputs()).noneMatch(p -> p.what().getPrimaryKey() == key.getPrimaryKey()))
                continue;
            for (var returned : result.returned) {
                if (!returned.getKey().equals(key) && (accepts(input, returned.getKey())
                    || returned.getKey().getPrimaryKey() == key.getPrimaryKey())) return in;
            }
        }
        return LongMath.saturatedAdd(in, LongMath.saturatedMultiply(Math.max(0, in - result.output.get(key)), crafts - 1));
    }

    // Borrowed, read-only material accounts. The simulator never changes a published result.
    record Result(List<List<GenericStack>> inputs, KeyCounter consumed, KeyCounter returned, KeyCounter output) {
        long batchSize(ICraftingInventory inventory, long limit) {
            for (var entry : consumed) {
                long held = inventory.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
                if (held < entry.getLongValue()) return 0;
                long loss = entry.getLongValue() - output.get(entry.getKey());
                if (loss > 0) limit = Math.min(limit, (held - entry.getLongValue()) / loss + 1);
            }
            return limit;
        }
    }

    private record Validity(IPatternDetails.IInput input, AEKey key) {
    }

    private record Evaluation(KeyCounter returned, long reusable) {
    }

    private static final class Grid {
        private final KeyCounter[] table;
        private final InventoryCrafting inventory = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);

        private Grid(int inputs) {
            table = new KeyCounter[inputs];
            Arrays.setAll(table, i -> new KeyCounter());
        }

        private void fill(List<List<GenericStack>> inputs) {
            inventory.clear();
            for (int i = 0; i < table.length; i++) {
                table[i].clear();
                for (var stack : inputs.get(i)) table[i].add(stack.what(), stack.amount());
            }
        }
    }

    private record Selection(List<List<GenericStack>> choices, Object2LongMap<AEKey> available, boolean allowMissing) {
    }

    private final class Search {
        final Object2LongMap<AEKey> available;
        final IPatternDetails.IInput[] inputs;
        final List<List<GenericStack>> options;
        final Integer[] order;
        final boolean allowMissing;
        final KeyCounter[] selected;
        final KeyCounter taken = new KeyCounter();
        long bestMissing = Long.MAX_VALUE;
        int completeChoices;
        Evaluation best;
        List<List<GenericStack>> bestInputs;

        Search(Object2LongMap<AEKey> available, IPatternDetails.IInput[] inputs, List<List<GenericStack>> options,
               Integer[] order, boolean allowMissing) {
            this.available = available;
            this.inputs = inputs;
            this.options = options;
            this.order = order;
            this.allowMissing = allowMissing;
            selected = new KeyCounter[inputs.length];
            Arrays.setAll(selected, i -> new KeyCounter());
        }

        void visit(int depth, long missing) {
            if (missing > bestMissing || completeChoices >= MAX_GRID_CHOICES) return;
            if (depth == order.length) {
                if (++completeChoices > MAX_GRID_CHOICES) return;
                var key = new ObjectArrayList<List<GenericStack>>(selected.length);
                for (var bag : selected) {
                    var stacks = new ObjectArrayList<GenericStack>();
                    for (var entry : bag) {
                        if (entry.getLongValue() > 0) stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
                    }
                    key.add(stacks);
                }
                var result = evaluate(key);
                // Damage values and NBT are opaque recipe state. Prefer grids that return usable inputs,
                // so a consumable alternative cannot crowd a circulating tool out of every slot.
                if (result != null && (missing < bestMissing || best == null || result.reusable() > best.reusable())) {
                    best = result;
                    bestInputs = key;
                    bestMissing = missing;
                }
                return;
            }
            int index = order[depth];
            visitUnits(depth, index, inputs[index].getMultiplier(), 0, missing);
        }

        private void visitUnits(int depth, int index, long units, int start, long missing) {
            if (missing > bestMissing || completeChoices >= MAX_GRID_CHOICES) return;
            if (units == 0) {
                visit(depth + 1, missing);
                return;
            }
            for (int optionIndex = start; optionIndex < options.get(index).size(); optionIndex++) {
                var option = options.get(index).get(optionIndex);
                long count = option.amount();
                long held = this.available.getLong(option.what());
                long already = taken.get(option.what());
                long available = Math.max(0, held - already);
                long addedMissing = Math.max(0, count - available);
                if (addedMissing > 0 && !allowMissing) continue;
                if (addedMissing > 0 && !option.equals(inputs[index].possibleInputs()[0])) continue;
                taken.add(option.what(), count);
                selected[index].add(option.what(), count);
                visitUnits(depth, index, units - 1, optionIndex, LongMath.saturatedAdd(missing, addedMissing));
                selected[index].remove(option.what(), count);
                taken.remove(option.what(), count);
            }
        }
    }
}
