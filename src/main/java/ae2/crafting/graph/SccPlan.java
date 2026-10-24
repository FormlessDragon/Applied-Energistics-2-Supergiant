package ae2.crafting.graph;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftingCalculation;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;

import java.util.Collections;
import java.util.List;

/**
 * Immutable SCC planning result. A local plan is intentionally component scoped.
 */
public final class SccPlan {
    public record SeedLedger(long required, long allocated, long missing, long reserve) {
        public SeedLedger {
            if (required < 0 || allocated < 0 || missing < 0 || reserve < 0) {
                throw new IllegalArgumentException("SCC seed ledger values must be non-negative");
            }
        }
    }

    private final int componentId;
    private final boolean local;
    private final boolean ring;
    private final Reference2LongMap<CraftingGraphNode> takenByCycle;
    private final List<CraftingGraphNode> nodes;
    private final Reference2LongMap<CraftingGraphNode> craftTimes;
    private final Reference2LongMap<CraftingGraphNode> plannedOutput;
    private final Reference2ObjectMap<CraftingGraphEdge, SeedLedger> seeds;
    private final KeyCounter boundaryDemands;

    private SccPlan(int componentId, boolean local, List<CraftingGraphNode> nodes,
                    Reference2LongMap<CraftingGraphNode> craftTimes,
                    Reference2LongMap<CraftingGraphNode> plannedOutput,
                    Reference2ObjectMap<CraftingGraphEdge, SeedLedger> seeds, KeyCounter boundaryDemands) {
        this(componentId, local, false, nodes, craftTimes, plannedOutput, seeds, boundaryDemands, emptyLongMap());
    }

    private SccPlan(int componentId, boolean local, boolean ring, List<CraftingGraphNode> nodes,
                    Reference2LongMap<CraftingGraphNode> craftTimes,
                    Reference2LongMap<CraftingGraphNode> plannedOutput,
                    Reference2ObjectMap<CraftingGraphEdge, SeedLedger> seeds, KeyCounter boundaryDemands,
                    Reference2LongMap<CraftingGraphNode> takenByCycle) {
        this.componentId = componentId;
        this.local = local;
        this.ring = ring;
        this.takenByCycle = Reference2LongMaps.unmodifiable(takenByCycle);
        // The node list is always the frozen component list from the topology; wrapping avoids a redundant copy.
        this.nodes = Collections.unmodifiableList(nodes);
        this.craftTimes = Reference2LongMaps.unmodifiable(craftTimes);
        this.plannedOutput = Reference2LongMaps.unmodifiable(plannedOutput);
        this.seeds = Reference2ObjectMaps.unmodifiable(seeds);
        var boundary = new KeyCounter();
        boundary.addAll(boundaryDemands);
        this.boundaryDemands = boundary;
    }

    public static SccPlan nativePlan(int componentId, List<CraftingGraphNode> nodes,
                                     Reference2LongMap<CraftingGraphNode> craftTimes,
                                     Reference2LongMap<CraftingGraphNode> plannedOutput,
                                     Reference2ObjectMap<CraftingGraphEdge, SeedLedger> seeds) {
        return new SccPlan(componentId, false, nodes, craftTimes, plannedOutput, seeds, new KeyCounter());
    }

    /**
     * A ring planned natively, see DemandPropagation#solveRing.
     *
     * @param takenByCycle of each node, how much of its output a node of the ring applied before it takes
     */
    public static SccPlan ringPlan(int componentId, List<CraftingGraphNode> nodes,
                                   Reference2LongMap<CraftingGraphNode> craftTimes,
                                   Reference2LongMap<CraftingGraphNode> plannedOutput,
                                   Reference2ObjectMap<CraftingGraphEdge, SeedLedger> seeds,
                                   Reference2LongMap<CraftingGraphNode> takenByCycle) {
        return new SccPlan(componentId, false, true, nodes, craftTimes, plannedOutput, seeds, new KeyCounter(),
            takenByCycle);
    }

    public static SccPlan localPlan(int componentId, List<CraftingGraphNode> nodes,
                                    KeyCounter boundaryDemands) {
        return new SccPlan(componentId, true, nodes, emptyLongMap(), emptyLongMap(),
            Reference2ObjectMaps.emptyMap(), boundaryDemands);
    }

    public static SccPlan localPlan(int componentId, List<CraftingGraphNode> nodes,
                                    KeyCounter boundaryDemands,
                                    CraftingCalculation.CalculationDelta delta) {
        var seeds = new Reference2ObjectLinkedOpenHashMap<CraftingGraphEdge, SeedLedger>();
        var assignedKeys = new ObjectOpenHashSet<AEKey>();
        for (var node : nodes) {
            for (var edge : node.getInputs()) {
                if (edge.producer() == null || !nodes.contains(edge.producer())) continue;
                var key = edge.inputKey();
                if (assignedKeys.contains(key)) continue;
                long missing = delta.recursiveMissingSeeds().get(key);
                long reserve = delta.recursiveReserveCandidates().get(key);
                boolean allocatedSeed = delta.realRecursiveSeeds().contains(key);
                if (missing <= 0 && reserve <= 0 && !allocatedSeed) continue;
                long allocated = allocatedSeed ? edge.amountPerCraft() : 0;
                long required = LongMath.saturatedAdd(allocated, missing);
                seeds.put(edge, new SeedLedger(required, allocated, missing, reserve));
                assignedKeys.add(key);
            }
        }
        var result = new SccPlan(componentId, true, nodes, emptyLongMap(), emptyLongMap(), seeds, boundaryDemands);
        result.restorePlanning();
        return result;
    }

    private static Reference2LongMap<CraftingGraphNode> emptyLongMap() {
        var result = new Reference2LongOpenHashMap<CraftingGraphNode>();
        result.defaultReturnValue(0);
        return Reference2LongMaps.unmodifiable(result);
    }

    public int componentId() {
        return componentId;
    }

    public boolean local() {
        return local;
    }

    public boolean ring() {
        return ring;
    }

    /**
     * For a ring, how much of the node's output is taken back by the ring before it leaves the craft.
     */
    public long takenByCycle(CraftingGraphNode node) {
        return takenByCycle.getLong(node);
    }

    public List<CraftingGraphNode> nodes() {
        return nodes;
    }

    public long craftTimes(CraftingGraphNode node) {
        return craftTimes.getLong(node);
    }

    public long plannedOutput(CraftingGraphNode node) {
        return plannedOutput.getLong(node);
    }

    public KeyCounter boundaryDemands() {
        var copy = new KeyCounter();
        copy.addAll(boundaryDemands);
        return copy;
    }

    public void restorePlanning() {
        for (var node : nodes) {
            for (var edge : node.getInputs()) {
                var seed = seeds.get(edge);
                if (seed == null) continue;
                edge.markCycleCut(seed.required());
                edge.recordSeedResult(seed.allocated(), seed.missing());
                edge.setReserveDemand(seed.reserve());
            }
        }
    }
}
