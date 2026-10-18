package ae2.crafting.graph;

import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.stacks.AEKey;
import ae2.crafting.execution.CraftingSupplierLocation;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable graph execution decisions with deterministic display ownership for produced output.
 */
public record CraftingGraphDisplaySnapshot(AEKey rootWhat, long requestedAmount, List<Node> nodes) {
    public CraftingGraphDisplaySnapshot {
        // The node list is built once and never mutated afterwards; wrapping avoids a redundant copy.
        nodes = Collections.unmodifiableList(nodes);
    }

    public record Node(AEKey what, @Nullable IPatternDetails pattern, long outputPerCraft,
                       long demandAmount, long craftTimes, boolean executed, List<Edge> inputs,
                       List<PatternContainerGroup> machines,
                       Map<PatternContainerGroup, List<CraftingSupplierLocation>> machineLocations,
                       @Nullable LocalDisplayFragment localDisplay, long external, long missing,
                       int componentId, int componentOrder) {
        public Node {
            inputs = Collections.unmodifiableList(inputs);
            var copiedLocations = new Object2ObjectLinkedOpenHashMap<PatternContainerGroup, List<CraftingSupplierLocation>>();
            for (var machine : machines) {
                var locations = machineLocations.get(machine);
                if (locations != null && !locations.isEmpty()) {
                    copiedLocations.put(machine, locations);
                }
            }
            machines = Collections.unmodifiableList(machines);
            machineLocations = Object2ObjectMaps.unmodifiable(copiedLocations);
        }

        public Node(AEKey what, @Nullable IPatternDetails pattern, long outputPerCraft,
                    long demandAmount, long craftTimes, boolean executed, List<Edge> inputs) {
            this(what, pattern, outputPerCraft, demandAmount, craftTimes, executed, inputs, List.of(), Map.of(), null,
                0, 0, -1, -1);
        }
    }

    public record Edge(AEKey inputKey, long amountPerCraft, int producerIndex,
                       boolean cycleCut, long needed, long allocatedOutput,
                       long extractedExternal, long missing, long seedRequired,
                       long seedAllocated, long seedMissing, long reserve) {
        @SuppressWarnings("unused")
        public Edge(AEKey inputKey, long amountPerCraft, int producerIndex,
                    boolean cycleCut, long needed, long allocatedOutput,
                    long extractedExternal, long missing) {
            this(inputKey, amountPerCraft, producerIndex, cycleCut, needed, allocatedOutput,
                extractedExternal, missing, 0, 0, 0, 0);
        }
    }

    public static Builder builder(CraftingGraph graph) {
        return new Builder(graph);
    }

    public static final class Builder {
        private static final EdgeResult EMPTY_EDGE_RESULT = new EdgeResult(0, 0, 0, 0);

        private final CraftingGraph graph;
        private final ObjectArrayList<CraftingGraphNode> orderedNodes = new ObjectArrayList<>();
        private final Reference2IntMap<CraftingGraphNode> indexes = new Reference2IntOpenHashMap<>();
        private final Reference2ObjectMap<CraftingGraphNode, Object2LongOpenHashMap<AEKey>> remainingOutput =
            new Reference2ObjectOpenHashMap<>();
        private final ReferenceOpenHashSet<CraftingGraphNode> executed = new ReferenceOpenHashSet<>();
        private final Reference2ObjectMap<CraftingGraphNode, LocalDisplayFragment> localDisplays =
            new Reference2ObjectOpenHashMap<>();
        private final Reference2LongMap<CraftingGraphNode> nodeMissing = new Reference2LongOpenHashMap<>();
        private final Reference2LongMap<CraftingGraphNode> nodeExternal = new Reference2LongOpenHashMap<>();
        private final Reference2ObjectMap<CraftingGraphEdge, EdgeResult> edgeResults =
            new Reference2ObjectOpenHashMap<>();
        private final Reference2IntMap<CraftingGraphNode> componentOrder = new Reference2IntOpenHashMap<>();
        private long buildRequestedAmount;

        private Builder(CraftingGraph graph) {
            this.graph = graph;
            var root = graph.getRootNode();
            if (root == null) {
                throw new IllegalStateException("Graph has no root node");
            }
            indexes.defaultReturnValue(-1);
            componentOrder.defaultReturnValue(-1);
            collect(root);
        }

        void recordNode(CraftingGraphNode node) {
            executed.add(node);
        }

        /**
         * Sets the final requested amount used when the display snapshot is later built on first GUI open.
         */
        public void setRequestedAmount(long requestedAmount) {
            this.buildRequestedAmount = requestedAmount;
        }

        void recordLocalDisplay(CraftingGraphNode node, LocalDisplayFragment display) {
            localDisplays.put(node, display);
        }

        void recordEdge(CraftingGraphEdge edge, long needed, long extracted) {
            long allocated = edge.cycleCut() ? 0 : consume(edge.producer(), edge.inputKey(), extracted);
            edgeResults.put(edge, new EdgeResult(needed, allocated, extracted - allocated, needed - extracted));
        }

        void recordInternalEdge(CraftingGraphEdge edge, long consumed) {
            consume(edge.producer(), edge.inputKey(), consumed);
            if (edge.cycleCut() && edge.seedMissing() > 0) {
                // Surface a component-local seed shortfall as a terminal leaf so it is claimed from the global
                // missing budget instead of disappearing from the display tree.
                edgeResults.put(edge, new EdgeResult(0, 0, 0, edge.seedMissing()));
            } else {
                edgeResults.put(edge, new EdgeResult(0, 0, 0, 0));
            }
        }

        void recordOutput(CraftingGraphNode node, AEKey key, long amount) {
            if (amount > 0) {
                var outputs = remainingOutput.computeIfAbsent(node,
                    ignored -> new Object2LongOpenHashMap<>());
                long current = outputs.getLong(key);
                if (current >= Long.MAX_VALUE - amount) {
                    outputs.put(key, Long.MAX_VALUE);
                } else {
                    outputs.addTo(key, amount);
                }
            }
        }

        void recordMissing(CraftingGraphNode node, long amount) {
            if (amount > 0) nodeMissing.put(node, amount);
        }

        void recordExternal(CraftingGraphNode node, long amount) {
            if (amount > 0) nodeExternal.put(node, amount);
        }

        public CraftingGraphDisplaySnapshot build() {
            long requestedAmount = this.buildRequestedAmount;
            if (requestedAmount <= 0) {
                throw new IllegalStateException("Graph display snapshot was built without a requested amount");
            }
            return build(requestedAmount);
        }

        CraftingGraphDisplaySnapshot build(long requestedAmount) {
            var topology = graph.getTopology();
            for (var component : topology.getComponents()) {
                var nodes = component.nodes();
                for (int i = 0, size = nodes.size(); i < size; i++) {
                    componentOrder.put(nodes.get(i), i);
                }
            }
            var snapshots = new ObjectArrayList<Node>(orderedNodes.size());
            for (var node : orderedNodes) {
                var edges = new ObjectArrayList<Edge>(node.getInputs().size());
                for (var edge : node.getInputs()) {
                    var result = edgeResults.get(edge);
                    if (result == null) {
                        result = EMPTY_EDGE_RESULT;
                    }
                    var producer = edge.producer();
                    edges.add(new Edge(edge.inputKey(), edge.amountPerCraft(),
                        producer == null ? -1 : indexes.getInt(producer),
                        edge.cycleCut(),
                        result.needed, result.allocated, result.external, result.missing,
                        edge.seedRequired(), edge.seedAllocated(), edge.seedMissing(), edge.reserveDemand()));
                }
                snapshots.add(new Node(node.getWhat(), node.getPattern(), node.getOutputPerCraft(),
                    node.getDemandAmount(), node.getCraftTimes(), executed.contains(node), edges,
                    List.of(), Map.of(),
                    localDisplays.get(node), nodeExternal.getLong(node), nodeMissing.getLong(node),
                    topology.getComponentId(node), componentOrder.getInt(node)));
            }
            return new CraftingGraphDisplaySnapshot(orderedNodes.getFirst().getWhat(), requestedAmount, snapshots);
        }

        private long consume(@Nullable CraftingGraphNode producer, AEKey key, long amount) {
            if (producer == null || amount <= 0) return 0;
            var outputs = remainingOutput.get(producer);
            if (outputs == null) return 0;
            long available = outputs.getLong(key);
            long allocated = Math.min(available, amount);
            if (allocated == available) {
                outputs.removeLong(key);
                if (outputs.isEmpty()) remainingOutput.remove(producer);
            } else {
                outputs.addTo(key, -allocated);
            }
            return allocated;
        }

        private void collect(CraftingGraphNode node) {
            if (indexes.containsKey(node)) return;
            indexes.put(node, orderedNodes.size());
            orderedNodes.add(node);
            for (var edge : node.getInputs()) {
                if (edge.producer() != null) collect(edge.producer());
            }
        }

        private record EdgeResult(long needed, long allocated, long external, long missing) {
        }
    }
}
