package ae2.crafting;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.stacks.KeyAmounts;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * A bounded fallback for exact, non-growing stock conversions. Reserving a downstream input too early can prevent
 * the tree from discovering a cycle which temporarily consumes that input and then returns enough for the request.
 * Search whole inventories instead, and publish a replacement only after proving its task counts run on the CPU.
 * Unsupported recipes are omitted from the search. Limits or a missing witness leave the original plan intact;
 * search exhaustion is never a shortage proof.
 */
final class CraftingStockConversion {
    private static final int MAX_KEYS = 64;
    private static final int MAX_PATTERNS = 64;
    private static final int MAX_PATTERN_CANDIDATES = 4096;
    private static final int MAX_STATES = 10_000;
    private static final int MAX_TRANSITIONS = 100_000;

    private final CraftingPlan original;
    private final CraftingCalculation calculation;
    private final Map<AEKey, Integer> indices = new LinkedHashMap<>();
    private final List<Recipe> recipes = new ArrayList<>();
    private List<AEKey> keys;
    private long[] initial;
    private long[] reserves;
    private KeyCounter supplied;

    private CraftingStockConversion(CraftingPlan original, CraftingCalculation calculation) {
        this.original = original;
        this.calculation = calculation;
    }

    static @Nullable CraftingPlan tryPlan(CraftingPlan plan, CraftingCalculation calculation) throws InterruptedException {
        if (!plan.emittedItems().isEmpty()) return null;
        var search = new CraftingStockConversion(plan, calculation);
        try {
            if (!search.prepare()) return null;
        } catch (ArithmeticException overflow) {
            // An unused alternative may not fit this exact-input model. Keep the already validated original plan.
            return null;
        }
        return search.search();
    }

    private boolean prepare() throws InterruptedException {
        var pending = new ArrayDeque<AEKey>();
        var visited = new HashSet<AEKey>();
        var patterns = new HashSet<IPatternDetails>();
        var target = this.original.finalOutput().what();
        index(target);
        pending.add(target);
        while (!pending.isEmpty()) {
            this.calculation.handlePausing();
            var key = pending.removeFirst();
            if (!visited.add(key)) continue;
            for (var pattern : this.calculation.getCraftingFor(key)) {
                if (!patterns.add(pattern)) continue;
                if (patterns.size() > MAX_PATTERN_CANDIDATES) return false;
                Recipe recipe;
                try {
                    recipe = readRecipe(pattern, target);
                } catch (ArithmeticException overflow) {
                    // An unused alternative cannot invalidate a witness made entirely of supported recipes.
                    continue;
                }
                if (recipe == null) continue;
                if (this.recipes.size() == MAX_PATTERNS) return false;
                for (var input : recipe.inputs.keySet()) {
                    pending.add(input);
                    index(input);
                }
                for (var output : recipe.outputs.keySet()) index(output);
                if (this.indices.size() > MAX_KEYS) return false;
                this.recipes.add(recipe);
            }
        }
        if (this.recipes.isEmpty()) return false;
        this.keys = List.copyOf(this.indices.keySet());
        this.initial = new long[this.keys.size()];
        this.reserves = new long[this.keys.size()];
        this.supplied = KeyCounter.checked();
        var targets = this.original.recursiveReserveTargets();
        for (int i = 1; i < this.keys.size(); i++) {
            var key = this.keys.get(i);
            this.initial[i] = this.calculation.getNetworkAmount(key);
            this.reserves[i] = targets.get(key);
            if (this.initial[i] > 0) this.supplied.add(key, this.initial[i]);
        }
        for (var recipe : this.recipes) recipe.compile(this.indices);
        return true;
    }

    private static @Nullable Recipe readRecipe(IPatternDetails pattern, AEKey target) {
        if (pattern instanceof IAssemblerPattern || PseudoPatternDetails.isPseudo(pattern)) return null;
        var inputs = KeyCounter.checked();
        var outputs = KeyCounter.checked();
        for (var input : pattern.getInputs()) {
            var variants = input.possibleInputs();
            if (variants.length != 1 || variants[0].amount() <= 0 || input.getMultiplier() <= 0
                || input.getRemainingKey(variants[0].what()) != null) return null;
            var stack = variants[0];
            // The final request is new production, never an extraction of stored output.
            // Cycles consuming the requested output keep using the general recursive planner.
            if (stack.what().equals(target)) return null;
            inputs.add(stack.what(), KeyAmounts.multiply(stack.what(), stack.amount(), input.getMultiplier()));
        }
        for (var output : pattern.getOutputs()) {
            if (output.amount() <= 0) return null;
            outputs.add(output.what(), output.amount());
        }
        if (inputs.isEmpty() || outputs.isEmpty() || total(outputs).compareTo(total(inputs)) > 0) return null;
        return new Recipe(pattern, inputs, outputs);
    }

    private int index(AEKey key) {
        return this.indices.computeIfAbsent(key, ignored -> this.indices.size());
    }

    private static BigInteger total(KeyCounter counter) {
        var total = BigInteger.ZERO;
        for (var entry : counter) total = total.add(BigInteger.valueOf(entry.getLongValue()));
        return total;
    }

    private @Nullable CraftingPlan search() throws InterruptedException {
        var pending = new ArrayDeque<State>();
        var visited = new HashSet<State>();
        var start = new State(this.initial, new long[this.recipes.size()], null);
        pending.add(start);
        visited.add(start);
        int transitions = 0;
        while (!pending.isEmpty()) {
            this.calculation.handlePausing();
            var current = pending.removeFirst();
            for (int i = 0; i < this.recipes.size(); i++) {
                if (++transitions > MAX_TRANSITIONS) return null;
                var recipe = this.recipes.get(i);
                long maximum = recipe.runnable(current.inventory);
                if (recipe.outputs.get(this.keys.getFirst()) > 0) {
                    long remaining = this.original.finalOutput().amount() - current.inventory[0];
                    if (remaining <= 0) continue;
                    maximum = Math.min(maximum, 1 + (remaining - 1) / recipe.outputs.get(this.keys.getFirst()));
                }
                if (maximum == 0) continue;
                // Unit steps preserve all possible interleavings; a full batch shortcuts large conversions.
                for (long times : maximum == 1 ? new long[]{1} : new long[]{1, maximum}) {
                    long[] inventory = recipe.run(current.inventory, times);
                    if (inventory == null) continue;
                    // Do not explore a sequence that returns to an ancestor's identical inventory.
                    // It contributes no production and can repeat forever even in a finite inventory space.
                    boolean repeated = false;
                    for (var ancestor = current; ancestor != null; ancestor = ancestor.parent) {
                        if (Arrays.equals(inventory, ancestor.inventory)) { repeated = true; break; }
                    }
                    if (repeated || times > Long.MAX_VALUE - current.crafts[i]) continue;
                    var crafts = current.crafts.clone();
                    crafts[i] += times;
                    var next = new State(inventory, crafts, current);
                    if (!visited.add(next)) continue;
                    if (visited.size() > MAX_STATES) return null;
                    if (coversRequest(inventory)) {
                        var result = buildPlan(next);
                        if (result != null) return result;
                    }
                    pending.addLast(next);
                }
            }
        }
        return null;
    }

    private boolean coversRequest(long[] inventory) {
        if (inventory[0] < this.original.finalOutput().amount()) return false;
        for (int i = 1; i < inventory.length; i++) if (inventory[i] < this.reserves[i]) return false;
        return true;
    }

    private @Nullable CraftingPlan buildPlan(State end) throws InterruptedException {
        var counts = end.crafts;
        try {
            var times = new Object2LongLinkedOpenHashMap<IPatternDetails>();
            for (int i = 0; i < counts.length; i++) if (counts[i] > 0) times.put(this.recipes.get(i).pattern, counts[i]);
            var candidate = this.original.withContents(this.supplied, KeyCounter.checked(), KeyCounter.checked(),
                times, this.calculation);
            CraftingCalculation.validateAmounts(candidate);
            var required = CraftingCpuReplay.requiredStock(candidate, this.calculation);
            if (required == null) {
                // The search found an executable path. A different greedy hash order need not execute the same
                // count vector, so retain and independently validate the actual witness instead of rejecting it.
                var steps = new ArrayList<CraftingDispatchOrder.Step>();
                for (var current = end; current.parent != null; current = current.parent) {
                    for (int i = 0; i < counts.length; i++) {
                        long crafts = current.crafts[i] - current.parent.crafts[i];
                        if (crafts > 0) steps.add(new CraftingDispatchOrder.Step(this.recipes.get(i).pattern, crafts));
                    }
                }
                Collections.reverse(steps);
                candidate.recordDispatchOrder(List.of(new CraftingDispatchOrder.Batch(steps, 1, true)));
                required = CraftingCpuReplay.requiredStock(candidate, this.calculation);
                if (required == null) return null;
            }
            for (int i = 1; i < this.initial.length; i++) {
                long keep = Math.max(0, this.initial[i] - (end.inventory[i] - this.reserves[i]));
                if (keep > required.get(this.keys.get(i))) required.set(this.keys.get(i), keep);
            }
            var trimmed = candidate.withContents(required, KeyCounter.checked(), KeyCounter.checked(), times, this.calculation);
            trimmed.recordDispatchOrder(candidate.dispatchOrder());
            return CraftingCpuReplay.runs(trimmed, this.calculation) ? trimmed : candidate;
        } catch (ArithmeticException overflow) {
            // A speculative path may overflow aggregate crafted totals even though individual inventories fit.
            return null;
        }
    }

    private static final class State {
        final long[] inventory;
        final long[] crafts;
        final @Nullable State parent;
        private final int hash;

        State(long[] inventory, long[] crafts, @Nullable State parent) {
            this.inventory = inventory;
            this.crafts = crafts;
            this.parent = parent;
            this.hash = Arrays.hashCode(crafts);
        }

        @Override
        public int hashCode() { return this.hash; }

        @Override
        public boolean equals(Object other) {
            // Equal recipe counts imply equal inventory, but equal inventory alone does not imply equal CPU tasks.
            return other instanceof State state && Arrays.equals(this.crafts, state.crafts);
        }
    }

    private static final class Recipe {
        final IPatternDetails pattern;
        final KeyCounter inputs;
        final KeyCounter outputs;
        int[] keys;
        long[] in;
        long[] out;

        Recipe(IPatternDetails pattern, KeyCounter inputs, KeyCounter outputs) {
            this.pattern = pattern;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        void compile(Map<AEKey, Integer> indices) {
            var entries = new ArrayList<>(this.inputs.keySet());
            for (var key : this.outputs.keySet()) if (this.inputs.get(key) == 0) entries.add(key);
            this.keys = new int[entries.size()];
            this.in = new long[entries.size()];
            this.out = new long[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                this.keys[i] = indices.get(entries.get(i));
                this.in[i] = this.inputs.get(entries.get(i));
                this.out[i] = this.outputs.get(entries.get(i));
            }
        }

        long runnable(long[] inventory) {
            long maximum = Long.MAX_VALUE;
            for (int i = 0; i < this.keys.length; i++) {
                if (this.in[i] > 0) maximum = Math.min(maximum, inventory[this.keys[i]] / this.in[i]);
            }
            return maximum;
        }

        @Nullable long[] run(long[] inventory, long times) {
            var result = inventory.clone();
            try {
                for (int i = 0; i < this.keys.length; i++) {
                    result[this.keys[i]] = Math.addExact(result[this.keys[i]] - Math.multiplyExact(this.in[i], times),
                        Math.multiplyExact(this.out[i], times));
                }
            } catch (ArithmeticException overflow) {
                return null;
            }
            return result;
        }
    }
}
