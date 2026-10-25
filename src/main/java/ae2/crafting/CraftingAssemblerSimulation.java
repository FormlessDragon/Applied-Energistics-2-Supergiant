package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.container.ContainerConstants;
import ae2.crafting.inv.ICraftingInventory;
import ae2.crafting.pattern.AECraftingPattern;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
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
    private final Map<List<List<GenericStack>>, Result> results = boundedCache();
    private final Map<Validity, Boolean> valid = boundedCache();
    private @Nullable Result defaultResult;

    public CraftingAssemblerSimulation(IAssemblerPattern pattern, World level) {
        this.pattern = pattern;
        this.level = level;
    }

    private static <K, V> Map<K, V> boundedCache() {
        return new Object2ObjectLinkedOpenHashMap<>() {
            @Override
            public V put(K key, V value) {
                V previous = putAndMoveToLast(key, value);
                if (size() > CACHE_SIZE) removeFirst();
                return previous;
            }
        };
    }

    public @Nullable Result select(ICraftingInventory inventory, boolean allowMissing) {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(() -> select(inventory, allowMissing));
        }
        var inputs = pattern.getInputs();
        var choices = new ObjectArrayList<List<GenericStack>>(inputs.length);
        for (var input : inputs) {
            var options = new ObjectArrayList<GenericStack>();
            var seen = new ObjectOpenHashSet<GenericStack>();
            for (var possible : input.possibleInputs()) {
                for (var key : inventory.findFuzzyTemplates(possible.what())) {
                    if (inventory.extract(key, possible.amount(), Actionable.SIMULATE) < possible.amount()) continue;
                    var option = new GenericStack(key, possible.amount());
                    if (seen.add(option) && accepts(input, key)) options.add(option);
                }
            }
            options.sort(Comparator.comparingInt(o -> o.what() instanceof AEItemKey item && item.getItem().isDamageable()
                ? item.toStack().getItemDamage() : 0));
            if (allowMissing && input.possibleInputs().length > 0) {
                var first = input.possibleInputs()[0];
                if (!options.contains(first)) options.add(first);
            }
            if (options.isEmpty()) return null;
            choices.add(options);
        }
        var order = new Integer[inputs.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> choices.get(i).size()));
        var search = new Search(inventory, inputs, choices, order, allowMissing);
        search.visit(0, 0);
        return search.best;
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

    public long defaultRemainderAmount(AEKey key) {
        var remainders = defaultRemainders();
        return remainders == null ? 0 : remainders.get(key);
    }

    public @Nullable KeyCounter defaultRemainders() {
        if (CraftingCalculation.isCalculationThread()) {
            return CraftingCalculation.runOnMainThread(this::defaultRemainders);
        }
        var result = defaultResult;
        if (result == null) {
            var inputs = pattern.getInputs();
            var selected = new ObjectArrayList<List<GenericStack>>(inputs.length);
            for (var input : inputs) {
                var possible = input.possibleInputs();
                var bag = new ObjectArrayList<GenericStack>(possible.length == 0 ? 0 : 1);
                if (possible.length > 0) bag.add(new GenericStack(possible[0].what(),
                    LongMath.saturatedMultiply(possible[0].amount(), input.getMultiplier())));
                selected.add(bag);
            }
            defaultResult = result = evaluate(selected);
        }
        return result == null ? null : result.returned;
    }

    private @Nullable Result evaluate(List<List<GenericStack>> inputs) {
        var table = new KeyCounter[inputs.size()];
        var consumed = new KeyCounter();
        for (int i = 0; i < table.length; i++) {
            table[i] = new KeyCounter();
            for (var stack : inputs.get(i)) {
                table[i].add(stack.what(), stack.amount());
                consumed.add(stack.what(), stack.amount());
            }
        }
        var grid = new InventoryCrafting(ContainerConstants.EMPTY_CONTAINER, 3, 3);
        pattern.fillCraftingGrid(table, grid::setInventorySlotContents);
        if (!pattern.getPrimaryOutput().equals(GenericStack.fromItemStack(pattern.assemble(grid, level)))) return null;
        var returns = new KeyCounter();
        for (var remaining : pattern.getRemainingItems(grid)) {
            var stack = GenericStack.fromItemStack(remaining);
            if (stack != null) returns.add(stack.what(), stack.amount());
        }
        var output = new KeyCounter();
        output.addAll(returns);
        for (var stack : pattern.getOutputs()) output.add(stack.what(), stack.amount());
        return new Result(inputs, consumed, returns, output);
    }

    /**
     * Required stock before this same complete grid can execute a sequential batch.
     */
    public long required(Result result, AEKey key, long crafts) {
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

    public record Result(List<List<GenericStack>> inputs, KeyCounter consumed, KeyCounter returned, KeyCounter output) {
        public KeyCounter[] inputTable() {
            var table = new KeyCounter[inputs.size()];
            for (int i = 0; i < table.length; i++) {
                table[i] = new KeyCounter();
                for (var stack : inputs.get(i)) table[i].add(stack.what(), stack.amount());
            }
            return table;
        }

        public long batchSize(ICraftingInventory inventory, long limit) {
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

    private final class Search {
        final ICraftingInventory inventory;
        final IPatternDetails.IInput[] inputs;
        final List<List<GenericStack>> options;
        final Integer[] order;
        final boolean allowMissing;
        final KeyCounter[] selected;
        final KeyCounter taken = new KeyCounter();
        long bestMissing = Long.MAX_VALUE;
        int completeChoices;
        Result best;

        Search(ICraftingInventory inventory, IPatternDetails.IInput[] inputs, List<List<GenericStack>> options,
               Integer[] order, boolean allowMissing) {
            this.inventory = inventory;
            this.inputs = inputs;
            this.options = options;
            this.order = order;
            this.allowMissing = allowMissing;
            selected = new KeyCounter[inputs.length];
            Arrays.setAll(selected, i -> new KeyCounter());
        }

        void visit(int depth, long missing) {
            if (missing >= bestMissing) return;
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
                Result result;
                if (results.containsKey(key)) result = results.get(key);
                else {
                    result = evaluate(key);
                    results.put(key, result);
                }
                if (result != null) {
                    best = result;
                    bestMissing = missing;
                }
                return;
            }
            int index = order[depth];
            visitUnits(depth, index, inputs[index].getMultiplier(), 0, missing);
        }

        private void visitUnits(int depth, int index, long units, int start, long missing) {
            if (missing >= bestMissing) return;
            if (units == 0) {
                visit(depth + 1, missing);
                return;
            }
            for (int optionIndex = start; optionIndex < options.get(index).size(); optionIndex++) {
                var option = options.get(index).get(optionIndex);
                long count = option.amount();
                long held = inventory.extract(option.what(), Long.MAX_VALUE, Actionable.SIMULATE);
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
                if (bestMissing == 0) return;
            }
        }
    }
}
