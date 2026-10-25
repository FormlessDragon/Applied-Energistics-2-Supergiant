package ae2.client.gui.me.crafting;

import ae2.integration.data.LiteCraftTreeNode;
import ae2.integration.data.LiteCraftTreeProc;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The crafting cycles of a crafting tree as marked by the server: each contracted into the node it is entered by, with
 * what it takes from outside under that node, and each shown on its own as the ring it is. A ring is laid out as the
 * tree lays out a branch, what it makes on top, with one round of the cycle down its first column and every input fed
 * back linked to the craft above making it. A long round is folded every few crafts.
 */
public final class CraftingTreeCycleView {
    public static final int SEGMENT = 6;
    private static final int MAX_SEARCHED = 4096;

    private CraftingTreeCycleView() {
    }

    /**
     * One place a cycle is in the tree: the node it is entered by and its crafts there.
     */
    public record Region(LiteCraftTreeNode entry, int cycle, List<LiteCraftTreeProc> procs, Set<LiteCraftTreeNode> inner,
                  List<LiteCraftTreeNode> outside, int feedbacks) {
        public int crafts() {
            return procs.size();
        }

        public boolean missing() {
            for (var node : inner) {
                if (node.missing() > 0) return true;
            }
            return false;
        }
    }

    /**
     * The tree with each cycle contracted.
     *
     * @param cycles  per node standing for a cycle, that cycle
     * @param entries per node a cycle is entered by in the tree as received, that cycle
     */
    public record Contracted(LiteCraftTreeNode root, Map<LiteCraftTreeNode, Region> cycles,
                      Map<LiteCraftTreeNode, Region> entries) {
    }

    /**
     * @param feedback per input fed back, the craft above making it
     * @param folds    per node the round is folded into, the node of the tree it stands for and the crafts below it
     * @param nested   per input of the ring entering a cycle of its own, that cycle
     */
    public record Ring(Region region, LiteCraftTreeNode root, Map<LiteCraftTreeNode, LiteCraftTreeProc> feedback,
                Map<LiteCraftTreeNode, Fold> folds, Map<LiteCraftTreeNode, Region> nested) {
    }

    public record Fold(LiteCraftTreeNode node, int crafts) {
    }

    public static Contracted contract(LiteCraftTreeNode root) {
        var entries = new Reference2ObjectOpenHashMap<LiteCraftTreeNode, Region>();
        findRegions(root, entries);
        var cycles = new Reference2ObjectOpenHashMap<LiteCraftTreeNode, Region>();
        if (entries.isEmpty()) {
            return new Contracted(root, cycles, entries);
        }
        var byEntry = new Reference2ObjectOpenHashMap<LiteCraftTreeNode, List<Region>>();
        for (var region : entries.values()) {
            byEntry.computeIfAbsent(region.entry(), ignored -> new ObjectArrayList<>()).add(region);
        }
        return new Contracted(contractNode(root, null, byEntry, cycles), cycles, entries);
    }

    // A craft of a cycle under a node not of the same cycle starts a region there
    private static void findRegions(LiteCraftTreeNode root, Map<LiteCraftTreeNode, Region> entries) {
        var pending = new ObjectArrayList<LiteCraftTreeNode>();
        pending.add(root);
        var grouped = new Reference2ObjectLinkedOpenHashMap<LiteCraftTreeNode, Int2ObjectLinkedOpenHashMap<List<LiteCraftTreeProc>>>();
        while (!pending.isEmpty()) {
            var node = pending.pop();
            var parent = node.parent();
            for (var proc : node.inputs()) {
                if (proc.cycle() >= 0 && (parent == null || parent.cycle() != proc.cycle())) {
                    grouped.computeIfAbsent(node, ignored -> new Int2ObjectLinkedOpenHashMap<>())
                           .computeIfAbsent(proc.cycle(), ignored -> new ObjectArrayList<>())
                           .add(proc);
                }
                pending.addAll(proc.inputs());
            }
        }
        for (var entry : grouped.entrySet()) {
            for (var tops : entry.getValue().int2ObjectEntrySet()) {
                // Several cycles entered by one node: the node stands for the first
                entries.putIfAbsent(entry.getKey(), region(entry.getKey(), tops.getIntKey(), tops.getValue()));
            }
        }
    }

    private static Region region(LiteCraftTreeNode entry, int cycle, List<LiteCraftTreeProc> tops) {
        var procs = new ObjectArrayList<LiteCraftTreeProc>();
        var inner = new ReferenceOpenHashSet<LiteCraftTreeNode>();
        var outside = new ObjectArrayList<LiteCraftTreeNode>();
        int feedbacks = 0;
        var pending = new ObjectArrayList<>(tops);
        while (!pending.isEmpty()) {
            var proc = pending.removeFirst();
            procs.add(proc);
            for (var input : proc.inputs()) {
                boolean inCycle = LiteCraftTreeNode.isCycleFeedback(input.feedback());
                for (var below : input.inputs()) inCycle |= below.cycle() == cycle;
                if (!inCycle) {
                    outside.add(input);
                    continue;
                }
                inner.add(input);
                if (LiteCraftTreeNode.isCycleFeedback(input.feedback())) feedbacks++;
                for (var below : input.inputs()) {
                    if (below.cycle() == cycle) {
                        pending.add(below);
                    } else {
                        // Another way to make it, not of the cycle: what that takes comes from outside too
                        outside.addAll(below.inputs());
                    }
                }
            }
        }
        return new Region(entry, cycle, procs, inner, outside, feedbacks);
    }

    private static LiteCraftTreeNode contractNode(LiteCraftTreeNode node, @Nullable LiteCraftTreeProc parent,
                                                 Map<LiteCraftTreeNode, List<Region>> byEntry,
                                                 Map<LiteCraftTreeNode, Region> cycles) {
        var regions = byEntry.get(node);
        var procs = new ObjectArrayList<LiteCraftTreeProc>(node.inputs().size());
        var copy = new LiteCraftTreeNode(parent, node.output(), procs, node.missing(), node.feedback());
        Set<LiteCraftTreeProc> contracted = new ReferenceOpenHashSet<>();
        if (regions != null) {
            for (var region : regions) contracted.addAll(region.procs());
        }
        for (var proc : node.inputs()) {
            if (contracted.contains(proc)) continue;
            var inputs = new ObjectArrayList<LiteCraftTreeNode>(proc.inputs().size());
            var procCopy = new LiteCraftTreeProc(inputs, proc.machines(), proc.machineLocations(), null, proc.cycle());
            for (var input : proc.inputs()) inputs.add(contractNode(input, procCopy, byEntry, cycles));
            procs.add(procCopy);
        }
        if (regions != null) {
            var region = regions.getFirst();
            for (var other : regions) {
                // What the cycle takes from outside hangs under it in place of its crafts
                var inputs = new ObjectArrayList<LiteCraftTreeNode>(other.outside().size());
                var outside = new LiteCraftTreeProc(inputs, List.of(), Map.of(), null, other.cycle());
                for (var input : other.outside()) inputs.add(contractNode(input, outside, byEntry, cycles));
                if (!inputs.isEmpty()) procs.add(outside);
                if (other.missing()) copy.markMissing();
            }
            cycles.put(copy, region);
        }
        return copy;
    }

    /**
     * @param unfolded the nodes of the tree the round is not folded into
     * @param entries  per node of the tree a cycle is entered by, that cycle
     */
    public static Ring ring(Region region, Set<LiteCraftTreeNode> unfolded, Map<LiteCraftTreeNode, Region> entries) {
        var round = round(region);
        var builder = new RingBuilder(region, round, unfolded, entries);
        var procs = new ObjectArrayList<LiteCraftTreeProc>();
        var root = new LiteCraftTreeNode(null, region.entry().output(), procs, region.entry().missing(), -1);
        // Its crafts under the entry, compared as the same objects: records equal by content may be others
        for (var proc : region.entry().inputs()) {
            if (proc.cycle() == region.cycle() && !proc.inputs().isEmpty()) procs.add(builder.copy(proc));
        }
        return new Ring(region, root, builder.feedback, builder.folds, builder.nested);
    }

    // From the entry down to the first input fed back found, depth first
    private static Set<LiteCraftTreeNode> round(Region region) {
        var found = new ReferenceOpenHashSet<LiteCraftTreeNode>();
        var parents = new Reference2ObjectOpenHashMap<LiteCraftTreeNode, LiteCraftTreeNode>();
        var pending = new ObjectArrayList<LiteCraftTreeNode>();
        pending.add(region.entry());
        while (!pending.isEmpty()) {
            var node = pending.pop();
            if (LiteCraftTreeNode.isCycleFeedback(node.feedback())) {
                for (var step = node; step != null; step = parents.get(step)) found.add(step);
                return found;
            }
            var below = new ObjectArrayList<LiteCraftTreeNode>();
            for (var proc : node.inputs()) {
                if (proc.cycle() != region.cycle()) continue;
                for (var input : proc.inputs()) {
                    if (region.inner().contains(input)) {
                        parents.put(input, node);
                        below.add(input);
                    }
                }
            }
            for (int i = below.size() - 1; i >= 0; i--) pending.push(below.get(i));
        }
        return found;
    }

    private static final class RingBuilder {
        private final Region region;
        private final Set<LiteCraftTreeNode> round;
        private final Set<LiteCraftTreeNode> unfolded;
        private final Map<LiteCraftTreeNode, Region> entries;
        private final ObjectArrayList<LiteCraftTreeProc> above = new ObjectArrayList<>();
        private final Map<LiteCraftTreeNode, LiteCraftTreeProc> feedback = new Reference2ObjectOpenHashMap<>();
        private final Map<LiteCraftTreeNode, Fold> folds = new Reference2ObjectOpenHashMap<>();
        private final Map<LiteCraftTreeNode, Region> nested = new Reference2ObjectOpenHashMap<>();
        private int roundDepth;

        private RingBuilder(Region region, Set<LiteCraftTreeNode> round, Set<LiteCraftTreeNode> unfolded,
                            Map<LiteCraftTreeNode, Region> entries) {
            this.region = region;
            this.round = round;
            this.unfolded = unfolded;
            this.entries = entries;
        }

        private LiteCraftTreeProc copy(LiteCraftTreeProc proc) {
            var inputs = new ObjectArrayList<LiteCraftTreeNode>(proc.inputs().size());
            var copy = new LiteCraftTreeProc(inputs, proc.machines(), proc.machineLocations(), null, proc.cycle());
            this.above.add(copy);
            // The round first, so it runs down the first column, then the rest of the cycle, then what comes in
            for (int pass = 0; pass < 3; pass++) {
                for (var input : proc.inputs()) {
                    int kind = this.round.contains(input) ? 0 : this.region.inner().contains(input) ? 1 : 2;
                    if (kind == pass) inputs.add(copy(input, copy));
                }
            }
            this.above.removeLast();
            return copy;
        }

        private LiteCraftTreeNode copy(LiteCraftTreeNode node, LiteCraftTreeProc parent) {
            if (!this.region.inner().contains(node)) {
                var leaf = new LiteCraftTreeNode(parent, node.output(), List.of(), node.missing(), -1);
                var cycle = cycleOf(node);
                if (cycle != null) this.nested.put(leaf, cycle);
                if (LiteCraftTreeNode.isMissing(node)) leaf.markMissing();
                return leaf;
            }
            if (LiteCraftTreeNode.isCycleFeedback(node.feedback())) {
                var leaf = new LiteCraftTreeNode(parent, node.output(), List.of(), node.missing(), node.feedback());
                int maker = this.above.size() - 1 - node.feedback();
                if (maker >= 0) this.feedback.put(leaf, this.above.get(maker));
                return leaf;
            }
            boolean onRound = this.round.contains(node);
            if (onRound) this.roundDepth++;
            try {
                if (onRound && this.roundDepth % SEGMENT == 0 && !this.unfolded.contains(node)) {
                    int crafts = craftsBelow(node);
                    if (crafts > 0) {
                        var leaf = new LiteCraftTreeNode(parent, node.output(), List.of(), node.missing(), -1);
                        this.folds.put(leaf, new Fold(node, crafts));
                        return leaf;
                    }
                }
                var procs = new ObjectArrayList<LiteCraftTreeProc>(node.inputs().size());
                var copy = new LiteCraftTreeNode(parent, node.output(), procs, node.missing(), -1);
                for (var proc : node.inputs()) {
                    if (proc.cycle() == this.region.cycle()) {
                        procs.add(copy(proc));
                    } else {
                        // Another way to make it: what that takes, as what comes in
                        var inputs = new ObjectArrayList<LiteCraftTreeNode>(proc.inputs().size());
                        var other = new LiteCraftTreeProc(inputs, proc.machines(), proc.machineLocations(), null, -1);
                        for (var input : proc.inputs()) {
                            var leaf = new LiteCraftTreeNode(other, input.output(), List.of(), input.missing(), -1);
                            var cycle = cycleOf(input);
                            if (cycle != null) this.nested.put(leaf, cycle);
                            inputs.add(leaf);
                        }
                        if (!inputs.isEmpty()) procs.add(other);
                    }
                }
                return copy;
            } finally {
                if (onRound) this.roundDepth--;
            }
        }

        /**
         * The cycle an input from outside is made in: one it enters, or the first one below it that the item is part
         * of, as when it is made by a craft feeding a cycle of its own.
         */
        @Nullable
        private Region cycleOf(LiteCraftTreeNode input) {
            var entered = this.entries.get(input);
            if (entered != null) {
                return entered;
            }
            var what = input.output().what();
            var pending = new ObjectArrayList<LiteCraftTreeNode>();
            pending.add(input);
            for (int seen = 0; !pending.isEmpty() && seen < MAX_SEARCHED; seen++) {
                var node = pending.removeFirst();
                var region = this.entries.get(node);
                if (region != null) {
                    if (region.entry().output().what().equals(what)) return region;
                    for (var inner : region.inner()) {
                        if (inner.output().what().equals(what)) return region;
                    }
                }
                for (var proc : node.inputs()) pending.addAll(proc.inputs());
            }
            return null;
        }

        private int craftsBelow(LiteCraftTreeNode node) {
            int crafts = 0;
            var pending = new ObjectArrayList<LiteCraftTreeNode>();
            pending.add(node);
            while (!pending.isEmpty()) {
                for (var proc : pending.pop().inputs()) {
                    if (proc.cycle() != this.region.cycle()) continue;
                    crafts++;
                    for (var input : proc.inputs()) {
                        if (this.region.inner().contains(input)) pending.add(input);
                    }
                }
            }
            return crafts;
        }
    }
}
