package ae2.integration.data;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Marks the crafting cycles of a display tree, for the crafting tree screen to show each as one node and as the ring
 * it is. An input with nothing under it that a craft above it makes is fed back around a cycle: it is linked to that
 * craft, and the crafts from that one down to the one taking it are one cycle, merged with any cycle sharing a craft.
 * Inputs a cycle cut off that turn out to have no such craft above them are left out.
 */
public final class CraftingTreeCycles {
    private final ObjectArrayList<LiteCraftTreeProc> above = new ObjectArrayList<>();
    private final Reference2IntOpenHashMap<LiteCraftTreeNode> feedback = new Reference2IntOpenHashMap<>();
    // Union-find over the crafts of cycles
    private final Reference2ObjectOpenHashMap<LiteCraftTreeProc, LiteCraftTreeProc> joined =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<LiteCraftTreeProc> numbers = new Reference2IntOpenHashMap<>();
    private final Map<IPatternDetails, Set<AEKey>> outputs = new Reference2ObjectOpenHashMap<>();
    private boolean pending;

    private CraftingTreeCycles() {
        this.numbers.defaultReturnValue(-1);
    }

    public static LiteCraftTreeNode annotate(LiteCraftTreeNode root) {
        var cycles = new CraftingTreeCycles();
        cycles.visit(root);
        if (cycles.feedback.isEmpty() && !cycles.pending) {
            return root;
        }
        var copy = cycles.copy(root, null);
        return copy != null ? copy : root;
    }

    private void visit(LiteCraftTreeNode node) {
        for (var proc : node.inputs()) {
            this.above.add(proc);
            Set<AEKey> fedBack = null;
            for (var input : proc.inputs()) {
                if (input.feedback() == LiteCraftTreeNode.FEEDBACK_PENDING) {
                    this.pending = true;
                    if (fedBack == null) fedBack = new ObjectOpenHashSet<>();
                    fedBack.add(input.output().what());
                }
            }
            for (var input : proc.inputs()) {
                // Beside what is marked fed back, the same item is what the craft takes from storage
                if (fedBack != null && input.inputs().isEmpty()
                    && input.feedback() != LiteCraftTreeNode.FEEDBACK_PENDING
                    && fedBack.contains(input.output().what())) {
                    continue;
                }
                if (input.inputs().isEmpty()) {
                    link(input);
                } else {
                    visit(input);
                }
            }
            this.above.removeLast();
        }
    }

    private void link(LiteCraftTreeNode input) {
        var what = input.output().what();
        int taking = this.above.size() - 1;
        for (int i = taking; i >= 0; i--) {
            if (!makes(this.above.get(i), what)) continue;
            this.feedback.put(input, taking - i);
            for (int j = i + 1; j <= taking; j++) {
                union(this.above.get(i), this.above.get(j));
            }
            find(this.above.get(i));
            return;
        }
    }

    private boolean makes(LiteCraftTreeProc proc, AEKey what) {
        var pattern = proc.pattern();
        if (pattern == null) return false;
        return this.outputs.computeIfAbsent(pattern, p -> {
            Set<AEKey> keys = new ObjectOpenHashSet<>();
            for (var output : p.getOutputs()) keys.add(output.what());
            return keys;
        }).contains(what);
    }

    private LiteCraftTreeProc find(LiteCraftTreeProc proc) {
        var parent = this.joined.get(proc);
        if (parent == null) {
            this.joined.put(proc, proc);
            return proc;
        }
        if (parent == proc) return proc;
        var root = find(parent);
        this.joined.put(proc, root);
        return root;
    }

    private void union(LiteCraftTreeProc a, LiteCraftTreeProc b) {
        var rootA = find(a);
        var rootB = find(b);
        if (rootA != rootB) this.joined.put(rootB, rootA);
    }

    private int number(LiteCraftTreeProc proc) {
        if (!this.joined.containsKey(proc)) return -1;
        var root = find(proc);
        int number = this.numbers.getInt(root);
        if (number < 0) {
            number = this.numbers.size();
            this.numbers.put(root, number);
        }
        return number;
    }

    private @Nullable LiteCraftTreeNode copy(LiteCraftTreeNode node, @Nullable LiteCraftTreeProc parent) {
        boolean linked = this.feedback.containsKey(node);
        if (node.feedback() == LiteCraftTreeNode.FEEDBACK_PENDING && !linked) {
            return null;
        }
        List<LiteCraftTreeProc> procs = new ObjectArrayList<>(node.inputs().size());
        for (var proc : node.inputs()) {
            List<LiteCraftTreeNode> inputs = new ObjectArrayList<>(proc.inputs().size());
            var copied = new LiteCraftTreeProc(inputs, proc.machines(), proc.machineLocations(), proc.pattern(),
                number(proc));
            for (var input : proc.inputs()) {
                var child = copy(input, copied);
                if (child != null) inputs.add(child);
            }
            if (!inputs.isEmpty()) procs.add(copied);
        }
        return new LiteCraftTreeNode(parent, node.output(), procs, node.missing(),
            linked ? this.feedback.getInt(node) : -1);
    }
}
