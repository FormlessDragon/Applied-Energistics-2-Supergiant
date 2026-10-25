package ae2.integration.data;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingCalculation;
import ae2.crafting.CraftingTreeProcess;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A lazy, quantity-preserving view of the final plan. Each recipe is expanded once, including shared producers.
 */
public final class CraftingPlanTree {
    private final Object2ObjectOpenHashMap<AEKey, List<IPatternDetails>> producers = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<IPatternDetails, KeyCounter> inputs = new Object2ObjectOpenHashMap<>();
    private final Set<IPatternDetails> expanded = new ObjectOpenHashSet<>();
    private final KeyCounter totalInputs = new KeyCounter();
    private final KeyCounter remainderCredit = new KeyCounter();
    private final KeyCounter external = new KeyCounter();
    private final KeyCounter missing = new KeyCounter();
    private final KeyCounter ancestors = new KeyCounter();
    private final CraftingTreeStackRegistry.DecodeLimits limits = new CraftingTreeStackRegistry.DecodeLimits();
    private final Set<AEKey> keys = new ObjectOpenHashSet<>();
    private final Function<IPatternDetails, CraftingTreeProcess.MachineInfo> machines;
    private final Map<IPatternDetails, KeyCounter> remainders;

    private CraftingPlanTree(Object2LongMap<IPatternDetails> times, KeyCounter used, KeyCounter emitted,
                             KeyCounter missing, CraftingCalculation calculation,
                             Map<IPatternDetails, KeyCounter> allocations,
                             Map<IPatternDetails, KeyCounter> remainders) {
        this.machines = calculation::getMachineInfo;
        this.remainders = remainders;
        this.limits.checkRegistrySize(used.size());
        this.limits.checkRegistrySize(emitted.size());
        this.limits.checkRegistrySize(missing.size());
        this.external.addAll(used);
        this.external.addAll(emitted);
        this.missing.addAll(missing);
        for (var returns : remainders.values()) this.remainderCredit.addAll(returns);
        int inputNodes = 1;
        for (var entry : times.object2LongEntrySet()) {
            if (entry.getLongValue() <= 0) continue;
            if (this.inputs.size() >= CraftingTreeStackRegistry.MAX_TREE_PROCESSES) {
                throw new IllegalArgumentException("Crafting tree process limit exceeded");
            }
            var pattern = entry.getKey();
            var allocated = allocations.get(pattern);
            if (allocated == null) {
                allocated = new KeyCounter();
                int distinctInputs = 0;
                for (var input : pattern.getInputs()) {
                    var possible = input.possibleInputs();
                    if (possible.length == 0) continue;
                    var stack = possible[0];
                    if (allocated.get(stack.what()) == 0) this.limits.checkProcessChildCount(++distinctInputs);
                    allocated.add(stack.what(), LongMath.saturatedMultiply(entry.getLongValue(),
                        LongMath.saturatedMultiply(stack.amount(), input.getMultiplier())));
                }
            }
            inputNodes += allocated.size();
            if (inputNodes > CraftingTreeStackRegistry.MAX_TREE_NODES) {
                throw new IllegalArgumentException("Crafting tree node limit exceeded: " + inputNodes);
            }
            this.inputs.put(pattern, allocated);
            this.totalInputs.addAll(allocated);
            for (var output : pattern.getOutputs()) {
                this.producers.computeIfAbsent(output.what(), k -> new ArrayList<>()).add(pattern);
            }
            var returned = remainders.get(pattern);
            if (returned != null) for (var key : returned.keySet()) {
                this.producers.computeIfAbsent(key, k -> new ArrayList<>()).add(pattern);
            }
        }
        trimRemainderCredit();
    }

    private void trimRemainderCredit() {
        for (var input : this.totalInputs) {
            var key = input.getKey();
            long returned = this.remainderCredit.get(key);
            if (returned <= 0) continue;
            long demand = input.getLongValue();
            long external = LongMath.saturatedAdd(this.external.get(key), this.missing.get(key));
            long credit = Math.max(0, demand - Math.min(demand, external));
            if (returned > credit) this.remainderCredit.set(key, credit);
        }
        this.remainderCredit.removeZeros();
    }

    public static LiteCraftTreeNode build(GenericStack output, Object2LongMap<IPatternDetails> times,
                                          KeyCounter used, KeyCounter emitted, KeyCounter missing,
                                          CraftingCalculation calculation, Map<IPatternDetails, KeyCounter> allocations) {
        return build(output, times, used, emitted, missing, calculation, allocations, Map.of());
    }

    public static LiteCraftTreeNode build(GenericStack output, Object2LongMap<IPatternDetails> times,
                                          KeyCounter used, KeyCounter emitted, KeyCounter missing,
                                          CraftingCalculation calculation, Map<IPatternDetails, KeyCounter> allocations,
                                          Map<IPatternDetails, KeyCounter> remainders) {
        var builder = new CraftingPlanTree(times, used, emitted, missing, calculation, allocations, remainders);
        var root = builder.node(output.what(), output.amount(), null, 0);
        // A repair may add a reserve recipe that is not on the originally chosen route to the requested output.
        // Keep its actual inputs visible as another process, rather than silently dropping its costs.
        for (var pattern : times.keySet()) {
            if (times.getLong(pattern) > 0 && !builder.expanded.contains(pattern)) {
                builder.process(pattern, root.inputs(), 0);
            }
        }
        builder.missing.removeZeros();
        if (!builder.missing.isEmpty()) {
            var children = new ArrayList<LiteCraftTreeNode>();
            var group = new LiteCraftTreeProc(children, List.of(), Map.of());
            for (var entry : builder.missing) {
                if (entry.getLongValue() > 0) children.add(builder.leaf(entry.getKey(), entry.getLongValue(), group,
                    entry.getLongValue(), -1, 1));
            }
            builder.limits.addProcess();
            builder.limits.checkProcessChildCount(children.size());
            root.inputs().add(group);
        }
        builder.limits.checkNodeChildCount(root.inputs().size());
        return CraftingTreeCycles.annotate(root, remainders);
    }

    private static long take(KeyCounter source, AEKey key, long amount) {
        long taken = Math.clamp(source.get(key), 0, amount);
        if (taken > 0) source.remove(key, taken);
        return taken;
    }

    private LiteCraftTreeNode node(AEKey key, long amount, LiteCraftTreeProc parent, int depth) {
        count(key, depth);
        var processes = new ArrayList<LiteCraftTreeProc>();
        var makers = this.producers.remove(key);
        if (makers != null) for (var pattern : makers) {
            if (!this.expanded.contains(pattern)) process(pattern, processes, depth);
        }
        this.limits.checkNodeChildCount(processes.size());
        long absent = processes.isEmpty() ? take(this.missing, key, amount) : 0;
        if (processes.isEmpty()) take(this.external, key, amount - absent);
        return new LiteCraftTreeNode(parent, new GenericStack(key, amount), processes, absent);
    }

    private void process(IPatternDetails pattern, List<LiteCraftTreeProc> processes, int depth) {
        if (!this.expanded.add(pattern)) return;
        this.limits.addProcess();
        var info = this.machines.apply(pattern);
        this.limits.checkMachineCount(info.groups().size());
        for (var locations : info.locations().values()) this.limits.addMachineLocations(locations.size());
        var children = new ArrayList<LiteCraftTreeNode>();
        var process = new LiteCraftTreeProc(children, info.groups(), info.locations(), pattern, -1);
        for (var output : pattern.getOutputs()) this.ancestors.add(output.what(), 1);
        var returned = this.remainders.get(pattern);
        if (returned != null) for (var key : returned.keySet()) this.ancestors.add(key, 1);
        for (var input : this.inputs.get(pattern)) {
            var key = input.getKey();
            long amount = input.getLongValue();
            if (amount <= 0) continue;
            if (this.ancestors.get(key) > 0) {
                long stored = take(this.external, key, amount);
                long absent = take(this.missing, key, amount - stored);
                long seed = stored + absent;
                if (seed > 0) children.add(leaf(key, seed, process, absent, -1, depth + 1));
                long feedback = amount - seed;
                if (feedback > 0) {
                    take(this.remainderCredit, key, feedback);
                    children.add(leaf(key, feedback, process, 0, LiteCraftTreeNode.FEEDBACK_PENDING, depth + 1));
                }
            } else {
                long returnedCredit = take(this.remainderCredit, key, amount);
                if (returnedCredit > 0) children.add(leaf(key, returnedCredit, process, 0,
                    LiteCraftTreeNode.FEEDBACK_REMAINDER, depth + 1));
                if (amount > returnedCredit) children.add(node(key, amount - returnedCredit, process, depth + 1));
            }
        }
        for (var output : pattern.getOutputs()) this.ancestors.remove(output.what(), 1);
        if (returned != null) for (var key : returned.keySet()) this.ancestors.remove(key, 1);
        this.limits.checkProcessChildCount(children.size());
        if (!children.isEmpty()) processes.add(process);
    }

    private LiteCraftTreeNode leaf(AEKey key, long amount, LiteCraftTreeProc parent, long missing, int feedback, int depth) {
        count(key, depth);
        return new LiteCraftTreeNode(parent, new GenericStack(key, amount), List.of(), missing, feedback);
    }

    private void count(AEKey key, int depth) {
        this.limits.addNode(depth);
        this.keys.add(key);
        this.limits.checkRegistrySize(this.keys.size());
    }
}
