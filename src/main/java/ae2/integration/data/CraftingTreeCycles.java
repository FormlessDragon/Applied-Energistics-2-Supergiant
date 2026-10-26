package ae2.integration.data;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Marks strongly connected recipe groups, including references to producers on sibling branches. */
public final class CraftingTreeCycles {
    private final ObjectArrayList<LiteCraftTreeProc> above = new ObjectArrayList<>();
    private final ObjectArrayList<LiteCraftTreeProc> processes = new ObjectArrayList<>();
    private final Reference2IntOpenHashMap<LiteCraftTreeProc> indices = new Reference2IntOpenHashMap<>();
    private final Int2ObjectOpenHashMap<LiteCraftTreeProc> sources = new Int2ObjectOpenHashMap<>();
    private final ObjectArrayList<IntArrayList> edges = new ObjectArrayList<>();
    private final Reference2ObjectOpenHashMap<LiteCraftTreeNode, Link> links = new Reference2ObjectOpenHashMap<>();
    private final Map<IPatternDetails, Set<AEKey>> outputs = new Reference2ObjectOpenHashMap<>();
    private final Map<IPatternDetails, KeyCounter> remainders;
    private final boolean explicitFeedback;
    private int[] cycles;

    private CraftingTreeCycles(Map<IPatternDetails, KeyCounter> remainders, boolean explicitFeedback) {
        this.remainders = remainders;
        this.explicitFeedback = explicitFeedback;
    }

    public static LiteCraftTreeNode annotate(LiteCraftTreeNode root) {
        return new CraftingTreeCycles(Map.of(), false).annotateTree(root);
    }

    /** Final-plan inputs distinguish physical stock from circulating materials, even without remainders. */
    public static LiteCraftTreeNode annotate(LiteCraftTreeNode root, Map<IPatternDetails, KeyCounter> remainders) {
        return new CraftingTreeCycles(remainders, true).annotateTree(root);
    }

    private LiteCraftTreeNode annotateTree(LiteCraftTreeNode root) {
        index(root);
        visit(root);
        this.cycles = components();
        var copy = copy(root, null);
        return copy == null ? root : copy;
    }

    private void index(LiteCraftTreeNode node) {
        for (var proc : node.inputs()) {
            this.indices.put(proc, this.processes.size());
            this.processes.add(proc);
            this.edges.add(new IntArrayList());
            if (proc.sourceId() >= 0) this.sources.put(proc.sourceId(), proc);
            for (var input : proc.inputs()) index(input);
        }
    }

    private void visit(LiteCraftTreeNode node) {
        for (var proc : node.inputs()) {
            this.above.add(proc);
            Set<AEKey> fedBack = new ObjectOpenHashSet<>();
            for (var input : proc.inputs()) {
                if (input.feedback() == LiteCraftTreeNode.FEEDBACK_PENDING) fedBack.add(input.output().what());
            }
            for (var input : proc.inputs()) {
                for (var producer : input.inputs()) {
                    this.edges.get(this.indices.getInt(proc)).add(this.indices.getInt(producer));
                }
                if (!input.inputs().isEmpty()) {
                    visit(input);
                } else if (!fedBack.contains(input.output().what())
                    || input.feedback() == LiteCraftTreeNode.FEEDBACK_PENDING) {
                    link(input, proc);
                }
            }
            this.above.removeLast();
        }
    }

    private void link(LiteCraftTreeNode input, LiteCraftTreeProc consumer) {
        int sourceId = LiteCraftTreeNode.feedbackSourceId(input.feedback());
        if (sourceId >= 0) {
            var source = this.sources.get(sourceId);
            if (source != null) addLink(input, consumer, source, input.feedback());
            return;
        }
        if (input.feedback() == LiteCraftTreeNode.FEEDBACK_REMAINDER
            || this.explicitFeedback && input.feedback() != LiteCraftTreeNode.FEEDBACK_PENDING) return;
        int taking = this.above.size() - 1;
        for (int i = taking; i >= 0; i--) {
            var source = this.above.get(i);
            if (makes(source, input.output().what())) {
                addLink(input, consumer, source, taking - i);
                return;
            }
        }
    }

    private void addLink(LiteCraftTreeNode input, LiteCraftTreeProc consumer, LiteCraftTreeProc source, int feedback) {
        this.links.put(input, new Link(source, feedback));
        this.edges.get(this.indices.getInt(consumer)).add(this.indices.getInt(source));
    }

    private boolean makes(LiteCraftTreeProc proc, AEKey what) {
        var pattern = proc.pattern();
        if (pattern == null) return false;
        return this.outputs.computeIfAbsent(pattern, p -> {
            Set<AEKey> keys = new ObjectOpenHashSet<>();
            for (var output : p.getOutputs()) keys.add(output.what());
            var returned = this.remainders.get(p);
            if (returned != null) keys.addAll(returned.keySet());
            return keys;
        }).contains(what);
    }

    // Iterative Tarjan: display size, rather than Java stack depth or requested quantity, bounds the work.
    private int[] components() {
        int size = this.processes.size();
        int[] index = new int[size], low = new int[size], component = new int[size], next = new int[size];
        Arrays.fill(index, -1);
        Arrays.fill(component, -1);
        var path = new IntArrayList();
        var stack = new IntArrayList();
        int serial = 0, id = 0;
        for (int root = 0; root < size; root++) {
            if (index[root] >= 0) continue;
            path.add(root);
            stack.add(root);
            index[root] = low[root] = serial++;
            while (!path.isEmpty()) {
                int node = path.getInt(path.size() - 1);
                var inputs = this.edges.get(node);
                if (next[node] < inputs.size()) {
                    int child = inputs.getInt(next[node]++);
                    if (index[child] < 0) {
                        path.add(child);
                        stack.add(child);
                        index[child] = low[child] = serial++;
                    } else if (component[child] < 0) low[node] = Math.min(low[node], index[child]);
                } else {
                    path.removeInt(path.size() - 1);
                    if (!path.isEmpty()) {
                        int parent = path.getInt(path.size() - 1);
                        low[parent] = Math.min(low[parent], low[node]);
                    }
                    if (low[node] == index[node]) {
                        int member;
                        do {
                            member = stack.removeInt(stack.size() - 1);
                            component[member] = id;
                        } while (member != node);
                        id++;
                    }
                }
            }
        }
        int[] counts = new int[id];
        for (int value : component) counts[value]++;
        for (int i = 0; i < size; i++) {
            if (counts[component[i]] == 1 && !this.edges.get(i).contains(i)) component[i] = -1;
        }
        return component;
    }

    private int cycle(LiteCraftTreeProc proc) {
        return this.cycles[this.indices.getInt(proc)];
    }

    private @Nullable LiteCraftTreeNode copy(LiteCraftTreeNode node, @Nullable LiteCraftTreeProc parent) {
        var link = this.links.get(node);
        if (node.feedback() == LiteCraftTreeNode.FEEDBACK_PENDING && link == null) return null;
        List<LiteCraftTreeProc> procs = new ObjectArrayList<>(node.inputs().size());
        for (var proc : node.inputs()) {
            List<LiteCraftTreeNode> inputs = new ObjectArrayList<>(proc.inputs().size());
            var copied = new LiteCraftTreeProc(inputs, proc.machines(), proc.machineLocations(), proc.pattern(),
                cycle(proc), proc.sourceId());
            for (var input : proc.inputs()) {
                var child = copy(input, copied);
                if (child != null) inputs.add(child);
            }
            if (!inputs.isEmpty()) procs.add(copied);
        }
        int feedback = node.feedback() == LiteCraftTreeNode.FEEDBACK_REMAINDER ? node.feedback() : -1;
        if (link != null && parent != null && parent.cycle() >= 0 && parent.cycle() == cycle(link.source())) {
            feedback = link.feedback();
        }
        return new LiteCraftTreeNode(parent, node.output(), procs, node.missing(), feedback);
    }

    private record Link(LiteCraftTreeProc source, int feedback) {
    }
}
