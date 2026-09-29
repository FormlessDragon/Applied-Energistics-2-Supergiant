package ae2.crafting.graph;

import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Tree snapshot of a crafting graph's material structure. Unlike a flat key set it keeps the topology: every node
 * knows whether the network emits the key, the fuzzy substitute used when the key has no pattern of its own, and its
 * candidate patterns with their per-craft output and inputs.
 *
 * <p>The root is judged against the current inventory from the top down. The subset walk aggregates demand like
 * {@code DemandPropagation} does and consumes stock in the same greedy order, so subtrees that stock already covers
 * are left out and the subset stays close to what the request really consumes.</p>
 *
 * <p>Requests that can only ever report missing items are detected separately: a key is satisfiable when the network
 * emits it, when its fuzzy group covers the demand, when its substitute is satisfiable, or when any single candidate
 * pattern has every input satisfied. Candidate patterns are alternatives, so one satisfied candidate is enough; all
 * of them failing means no execution can complete the request.</p>
 */
public final class MaterialClosure {
    private final AEKey output;
    private final Object2ObjectMap<AEKey, Node> nodes;
    private final ObjectSet<AEKey> fuzzyKeys;
    /**
     * Whether the graph resolves through the legacy tree. Legacy trees may replay a cached bundle and consume pattern
     * inputs before looking at the node's own stock, so covering stock must not prune their subtrees, and demand is
     * settled by a solver that this structure does not model, so only empty fuzzy groups are conclusive there.
     */
    private final boolean legacyTree;
    /**
     * Whether the structure contains a crafting cycle. Cycles can be supplied by net-positive loops this structure
     * does not model, so no verdict is given for them.
     */
    private final boolean recursive;
    private ObjectSet<AEKey> keys;

    public MaterialClosure(AEKey output, Object2ObjectMap<AEKey, Node> nodes, ObjectSet<AEKey> fuzzyKeys,
                           boolean legacyTree, boolean recursive) {
        this.output = output;
        this.nodes = nodes;
        this.fuzzyKeys = fuzzyKeys;
        this.legacyTree = legacyTree;
        this.recursive = recursive;
    }

    public static MaterialClosure of(AEKey output, CraftingGraph graph) {
        return new MaterialClosure(output, graph.getClosureNodes(), graph.getFuzzyKeys(),
            graph.requiresLegacyFallback(), graph.hasRecursion());
    }

    /**
     * Material structure of one key. {@code inputs} is the union over {@code candidates}, used to walk the inventory
     * subset; {@code candidates} keeps the per-pattern grouping the verdict needs, because the inputs of different
     * patterns are alternatives rather than a combined requirement.
     */
    public record Node(boolean emittable, @Nullable AEKey substitute, List<Input> inputs, List<Candidate> candidates) {
        public static final Node EMITTER = new Node(true, null, List.of(), List.of());
    }

    public record Candidate(long outputPerCraft, List<Input> inputs) {
    }

    public record Input(AEKey key, long amount) {
    }

    public record CheckResult(Set<AEKey> subset, boolean guaranteedMissing) {
    }

    public AEKey output() {
        return this.output;
    }

    /**
     * @return the subset of the given keys whose whole fuzzy group the tree can consume, i.e. inputs of pattern slots
     *         with ingredient substitution, an assembler-pattern feature. Every other key is only ever extracted exactly.
     */
    public Set<AEKey> fuzzyKeysOf(Collection<AEKey> wanted) {
        if (this.fuzzyKeys.isEmpty()) {
            return Set.of();
        }
        var result = new ObjectOpenHashSet<AEKey>();
        for (var key : wanted) {
            if (this.fuzzyKeys.contains(key)) {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * Every key of the structure, i.e. the unpruned material closure.
     */
    public ObjectSet<AEKey> keys() {
        if (this.keys == null) {
            this.keys = ObjectSets.unmodifiable(this.nodes.keySet());
        }
        return this.keys;
    }

    /**
     * Judges the request against the current inventory.
     *
     * @return the subset of keys the simulation will read, and whether the request can only ever report missing items
     */
    public CheckResult check(long demand, KeyCounter inventory) {
        long required = Math.max(1, demand);
        var subset = new Walk(inventory, required).run();
        // The verdict can revisit shared subtrees, so budget it by the structure instead of a flat limit.
        boolean guaranteedMissing = !this.recursive && !satisfiable(this.output, required, inventory,
            new ObjectOpenHashSet<>(), new int[] { Math.max(64, this.nodes.size() * 8) });
        return new CheckResult(ObjectSets.unmodifiable(subset), guaranteedMissing);
    }

    /**
     * @return true when emission, stock, a substitute or a single candidate pattern can cover the key
     */
    private boolean satisfiable(AEKey what, long required, KeyCounter inventory, ObjectOpenHashSet<AEKey> visiting,
                                int[] budget) {
        if (budget[0]-- <= 0) {
            return true; // give up instead of stalling, keeping the conservative answer
        }
        var node = this.nodes.get(what);
        if (node == null || node.emittable()) {
            return true;
        }
        long stock = groupStock(what, inventory);
        if (this.legacyTree ? stock > 0 : stock >= required) {
            return true;
        }
        var substitute = node.substitute();
        if (substitute != null) {
            if (!visiting.add(what)) {
                return true; // cycles are resolved dynamically by the simulation
            }
            try {
                return satisfiable(substitute, required, inventory, visiting, budget);
            } finally {
                visiting.remove(what);
            }
        }
        if (node.candidates().isEmpty()) {
            return false;
        }
        if (!visiting.add(what)) {
            return true;
        }
        try {
            long shortfall = Math.max(0, required - stock);
            for (var candidate : node.candidates()) {
                long times = Math.ceilDiv(shortfall, Math.max(1, candidate.outputPerCraft()));
                if (candidateSatisfiable(candidate, times, inventory, visiting, budget)) {
                    return true;
                }
            }
            return false;
        } finally {
            visiting.remove(what);
        }
    }

    private boolean candidateSatisfiable(Candidate candidate, long times, KeyCounter inventory,
                                         ObjectOpenHashSet<AEKey> visiting, int[] budget) {
        for (var input : candidate.inputs()) {
            if (input.amount() <= 0) {
                continue;
            }
            if (!satisfiable(input.key(), LongMath.saturatedMultiply(times, input.amount()), inventory, visiting,
                budget)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stock of the whole fuzzy group of a key: the simulation may consume any variant of it.
     */
    private long groupStock(AEKey what, KeyCounter inventory) {
        long stock = 0;
        for (var entry : inventory.findFuzzy(what, FuzzyMode.IGNORE_ALL)) {
            stock += Math.max(0, entry.getLongValue());
        }
        return stock;
    }

    private final class Walk {
        private final KeyCounter inventory;
        private final ObjectOpenHashSet<AEKey> subset = new ObjectOpenHashSet<>();
        private final ObjectOpenHashSet<AEKey> reachable = new ObjectOpenHashSet<>();
        private final Object2LongOpenHashMap<AEKey> demands = new Object2LongOpenHashMap<>();
        private final Object2LongOpenHashMap<AEKey> stock = new Object2LongOpenHashMap<>();
        private final Object2IntOpenHashMap<AEKey> pendingParents = new Object2IntOpenHashMap<>();
        private final ObjectOpenHashSet<AEKey> queued = new ObjectOpenHashSet<>();
        private final ArrayDeque<AEKey> queue = new ArrayDeque<>();

        Walk(KeyCounter inventory, long demand) {
            this.inventory = inventory;
            this.demands.defaultReturnValue(0);
            this.stock.defaultReturnValue(Long.MIN_VALUE);
            this.demands.put(MaterialClosure.this.output, demand);
        }

        ObjectSet<AEKey> run() {
            countParents();
            this.queued.add(MaterialClosure.this.output);
            this.queue.add(MaterialClosure.this.output);
            while (!this.queue.isEmpty()) {
                visit(this.queue.pollFirst());
            }
            // Cyclic structures never drain their parents: keep their keys instead of pruning them.
            for (var key : this.reachable) {
                this.subset.add(key);
            }
            return this.subset;
        }

        private void countParents() {
            var stack = new ArrayDeque<AEKey>();
            stack.push(MaterialClosure.this.output);
            this.reachable.add(MaterialClosure.this.output);
            while (!stack.isEmpty()) {
                var node = MaterialClosure.this.nodes.get(stack.pop());
                if (node == null) {
                    continue;
                }
                for (var input : node.inputs()) {
                    countParent(input.key(), stack);
                }
                if (node.substitute() != null) {
                    countParent(node.substitute(), stack);
                }
            }
        }

        private void countParent(AEKey key, ArrayDeque<AEKey> stack) {
            this.pendingParents.addTo(key, 1);
            if (this.reachable.add(key)) {
                stack.push(key);
            }
        }

        private void visit(AEKey what) {
            long required = this.demands.getLong(what);
            var node = MaterialClosure.this.nodes.get(what);
            long times = 0;
            if (required > 0 && node != null && !node.emittable()) {
                this.subset.add(what);
                if (!what.equals(MaterialClosure.this.output) && !MaterialClosure.this.legacyTree) {
                    required -= take(what, required);
                }
                if (required > 0) {
                    this.demands.put(what, required);
                    // The smallest candidate yield asks for the most crafts, keeping the demand an upper bound.
                    long outputPerCraft = Long.MAX_VALUE;
                    for (var candidate : node.candidates()) {
                        outputPerCraft = Math.min(outputPerCraft, Math.max(1, candidate.outputPerCraft()));
                    }
                    if (outputPerCraft == Long.MAX_VALUE) {
                        outputPerCraft = 1;
                    }
                    times = Math.ceilDiv(required, outputPerCraft);
                }
            } else if (required > 0) {
                this.subset.add(what);
            }
            pushDemand(what, times);
        }

        /**
         * Hands the demand of one craft down to the node's inputs, or zero when the node is not processed, so the
         * remaining parent counts drain and every node is reached exactly once.
         */
        private void pushDemand(AEKey what, long times) {
            var node = MaterialClosure.this.nodes.get(what);
            if (node == null) {
                return;
            }
            for (var input : node.inputs()) {
                enqueue(input.key(), LongMath.saturatedMultiply(times, input.amount()));
            }
            if (node.substitute() != null) {
                enqueue(node.substitute(), times);
            }
        }

        private void enqueue(AEKey key, long amount) {
            this.demands.addTo(key, amount);
            if (this.pendingParents.addTo(key, -1) <= 0 && this.queued.add(key)) {
                this.queue.add(key);
            }
        }

        /**
         * Consumes stock exactly like the simulation does, so keys shared between branches are accounted once.
         */
        private long take(AEKey what, long required) {
            long remaining = this.stock.getLong(what);
            if (remaining == Long.MIN_VALUE) {
                remaining = Math.max(0, this.inventory.get(what));
            }
            long taken = Math.min(remaining, required);
            this.stock.put(what, remaining - taken);
            return taken;
        }
    }
}
