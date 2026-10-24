package ae2.integration.data;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.graph.CraftingGraphDisplaySnapshot;
import ae2.crafting.graph.LocalDisplayFragment;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts immutable graph display ownership into the existing display tree DTO.
 */
public final class CraftingGraphDisplayTreeConverter {
    private CraftingGraphDisplayTreeConverter() {
    }

    public static LiteCraftTreeNode toLiteTree(CraftingGraphDisplaySnapshot snapshot, long finalOutputAmount,
                                               KeyCounter missingItems) {
        if (snapshot.nodes().isEmpty()) {
            throw new IllegalArgumentException("Graph display snapshot has no nodes");
        }
        checkBudget(snapshot);
        return materialize(snapshot, 0, null, finalOutputAmount,
            new BitSet(snapshot.nodes().size()), new BitSet(snapshot.nodes().size()),
            new MissingAllocator(missingItems));
    }

    private static void checkBudget(CraftingGraphDisplaySnapshot snapshot) {
        var budget = new Budget();
        var expandedGraphNodes = new BitSet(snapshot.nodes().size());
        var pending = new ArrayDeque<BudgetTask>();
        pending.push(new GraphNodeBudgetTask(0, 0));

        while (!pending.isEmpty()) {
            switch (pending.pop()) {
                case GraphNodeBudgetTask task -> {
                    int nodeIndex = task.nodeIndex();
                    if (nodeIndex < 0 || nodeIndex >= snapshot.nodes().size()) {
                        throw new IllegalArgumentException("Graph display node index out of range: " + nodeIndex);
                    }
                    var node = snapshot.nodes().get(nodeIndex);
                    budget.addNode(node.what(), task.depth());
                    if (!node.executed() || expandedGraphNodes.get(nodeIndex)) {
                        budget.checkNodeChildCount(0);
                        continue;
                    }
                    expandedGraphNodes.set(nodeIndex);

                    int boundaryChildren = countGraphChildren(node.inputs(), snapshot.nodes().size());
                    int processCount;
                    if (node.localDisplay() != null) {
                        processCount = countLocalProcesses(node.localDisplay());
                        if (boundaryChildren > 0) {
                            processCount++;
                        }
                        budget.checkNodeChildCount(processCount);
                        if (boundaryChildren > 0) {
                            budget.addProcess(boundaryChildren, List.of(), Map.of());
                        }
                        addLocalProcesses(node.localDisplay(), task.depth(), budget, pending);
                    } else {
                        processCount = boundaryChildren > 0 ? 1 : 0;
                        budget.checkNodeChildCount(processCount);
                        if (boundaryChildren > 0) {
                            budget.addProcess(boundaryChildren, node.machines(), node.machineLocations());
                        }
                    }
                    pushGraphChildren(node.inputs(), task.depth() + 1, pending);

                    if (processCount == 0 && node.external() > 0) {
                        budget.addProcess(1, List.of(), Map.of());
                        pending.push(new LeafNodeBudgetTask(node.what(), task.depth() + 1));
                    }
                }
                case LocalNodeBudgetTask task -> {
                    var fragment = task.fragment();
                    budget.addNode(fragment.output(), task.depth());
                    int processCount = countLocalProcesses(fragment);
                    budget.checkNodeChildCount(processCount);
                    addLocalProcesses(fragment, task.depth(), budget, pending);
                }
                case LeafNodeBudgetTask task -> {
                    budget.addNode(task.what(), task.depth());
                    budget.checkNodeChildCount(0);
                }
            }
        }
    }

    private static int countLocalProcesses(LocalDisplayFragment fragment) {
        int count = 0;
        for (var process : fragment.processes()) {
            if (!process.inputs().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void addLocalProcesses(LocalDisplayFragment fragment, int parentDepth, Budget budget,
                                          ArrayDeque<BudgetTask> pending) {
        var processes = fragment.processes();
        for (int processIndex = processes.size() - 1; processIndex >= 0; processIndex--) {
            var process = processes.get(processIndex);
            if (process.inputs().isEmpty()) {
                continue;
            }
            budget.addProcess(process.inputs().size(), process.machines(), process.locations());
            var inputs = process.inputs();
            for (int inputIndex = inputs.size() - 1; inputIndex >= 0; inputIndex--) {
                pending.push(new LocalNodeBudgetTask(inputs.get(inputIndex), parentDepth + 1));
            }
        }
    }

    private static int countGraphChildren(List<CraftingGraphDisplaySnapshot.Edge> edges, int nodeCount) {
        int count = 0;
        for (var edge : edges) {
            if (edge.producerIndex() >= 0 && edge.allocatedOutput() > 0) {
                if (edge.producerIndex() >= nodeCount) {
                    throw new IllegalArgumentException("Graph display node index out of range: " + edge.producerIndex());
                }
                count++;
            }
            if (edge.extractedExternal() + edge.missing() > 0) {
                count++;
            }
            if (isFeedback(edge)) {
                count++;
            }
        }
        return count;
    }

    private static void pushGraphChildren(List<CraftingGraphDisplaySnapshot.Edge> edges, int depth,
                                          ArrayDeque<BudgetTask> pending) {
        for (int edgeIndex = edges.size() - 1; edgeIndex >= 0; edgeIndex--) {
            var edge = edges.get(edgeIndex);
            if (isFeedback(edge)) {
                pending.push(new LeafNodeBudgetTask(edge.inputKey(), depth));
            }
            if (edge.extractedExternal() + edge.missing() > 0) {
                pending.push(new LeafNodeBudgetTask(edge.inputKey(), depth));
            }
            if (edge.producerIndex() >= 0 && edge.allocatedOutput() > 0) {
                pending.push(new GraphNodeBudgetTask(edge.producerIndex(), depth));
            }
        }
    }

    // What a cycle takes back from a craft above it: not in the tree as the producer's child, so it is shown as an
    // input of its own, linked to that craft by CraftingTreeCycles
    private static boolean isFeedback(CraftingGraphDisplaySnapshot.Edge edge) {
        return edge.cycleCut() && edge.producerIndex() >= 0 && edge.amountPerCraft() > 0;
    }

    private sealed interface BudgetTask permits GraphNodeBudgetTask, LocalNodeBudgetTask, LeafNodeBudgetTask {
    }

    private record GraphNodeBudgetTask(int nodeIndex, int depth) implements BudgetTask {
    }

    private record LocalNodeBudgetTask(LocalDisplayFragment fragment, int depth) implements BudgetTask {
    }

    private record LeafNodeBudgetTask(AEKey what, int depth) implements BudgetTask {
    }

    private static final class Budget {
        private int nodes;
        private int processes;
        private int machineLocations;
        private final Set<AEKey> registryKeys = new ObjectOpenHashSet<>();

        void addNode(AEKey what, int depth) {
            if (depth > CraftingTreeStackRegistry.MAX_TREE_DEPTH) {
                throw new IllegalArgumentException("Crafting tree depth limit exceeded: " + depth);
            }
            nodes++;
            if (nodes > CraftingTreeStackRegistry.MAX_TREE_NODES) {
                throw new IllegalArgumentException("Crafting tree node limit exceeded: " + nodes);
            }
            if (registryKeys.add(what)
                && registryKeys.size() > CraftingTreeStackRegistry.MAX_REGISTRY_ENTRIES) {
                throw new IllegalArgumentException("Crafting tree registry size out of range: " + registryKeys.size());
            }
        }

        void addProcess(int childCount, List<?> machines, Map<?, ? extends List<?>> locations) {
            processes++;
            if (processes > CraftingTreeStackRegistry.MAX_TREE_PROCESSES) {
                throw new IllegalArgumentException("Crafting tree process limit exceeded: " + processes);
            }
            checkProcessChildCount(childCount);
            if (machines.size() > CraftingTreeStackRegistry.MAX_MACHINES_PER_PROCESS) {
                throw new IllegalArgumentException("Crafting tree machine count out of range: " + machines.size());
            }
            for (var machine : machines) {
                var machineLocationList = locations.get(machine);
                int locationCount = machineLocationList == null ? 0 : machineLocationList.size();
                if (locationCount > CraftingTreeStackRegistry.MAX_MACHINE_LOCATIONS_PER_MACHINE) {
                    throw new IllegalArgumentException(
                        "Crafting tree machine location count out of range: " + locationCount);
                }
                machineLocations += locationCount;
                if (machineLocations > CraftingTreeStackRegistry.MAX_MACHINE_LOCATIONS_TOTAL) {
                    throw new IllegalArgumentException(
                        "Crafting tree machine location limit exceeded: " + machineLocations);
                }
            }
        }

        void checkNodeChildCount(int count) {
            if (count > CraftingTreeStackRegistry.MAX_CHILDREN_PER_NODE) {
                throw new IllegalArgumentException("Crafting tree node child count out of range: " + count);
            }
        }

        private void checkProcessChildCount(int count) {
            if (count > CraftingTreeStackRegistry.MAX_CHILDREN_PER_PROCESS) {
                throw new IllegalArgumentException("Crafting tree process child count out of range: " + count);
            }
        }
    }

    private static LiteCraftTreeNode materialize(CraftingGraphDisplaySnapshot snapshot, int nodeIndex,
                                                 LiteCraftTreeProc parent, long amount,
                                                 BitSet path, BitSet materializedProcesses,
                                                 MissingAllocator missingAllocator) {
        if (nodeIndex < 0 || nodeIndex >= snapshot.nodes().size()) {
            throw new IllegalArgumentException("Graph display node index out of range: " + nodeIndex);
        }
        var node = snapshot.nodes().get(nodeIndex);
        if (!node.executed() || materializedProcesses.get(nodeIndex)) {
            return new LiteCraftTreeNode(parent, new GenericStack(node.what(), amount), List.of(),
                missingAllocator.allocate(node.what(), node.missing()));
        }
        if (path.get(nodeIndex)) {
            return new LiteCraftTreeNode(parent, new GenericStack(node.what(), amount), List.of(),
                missingAllocator.allocate(node.what(), node.missing()));
        }

        path.set(nodeIndex);
        materializedProcesses.set(nodeIndex);
        try {
            var processes = new ObjectArrayList<LiteCraftTreeProc>();
            if (node.localDisplay() != null) {
                appendLocalProcesses(node.localDisplay(), processes, node.inputs(), node.craftTimes(), snapshot, path,
                    materializedProcesses, missingAllocator);
                if (processes.isEmpty()) appendGraphProcess(node, nodeIndex, processes, snapshot, path,
                    materializedProcesses, missingAllocator);
            } else {
                appendGraphProcess(node, nodeIndex, processes, snapshot, path, materializedProcesses,
                    missingAllocator);
            }
            if (processes.isEmpty() && node.external() > 0) {
                var terminal = new ObjectArrayList<LiteCraftTreeNode>(1);
                var process = new LiteCraftTreeProc(terminal, List.of(), Map.of());
                terminal.add(new LiteCraftTreeNode(process, new GenericStack(node.what(), node.external()),
                    List.of(), 0));
                processes.add(process);
            }
            long missing = node.missing();
            if (node.localDisplay() != null) {
                missing = node.localDisplay().missing();
            }
            return new LiteCraftTreeNode(parent, new GenericStack(node.what(), amount), processes,
                missingAllocator.allocate(node.what(), missing));
        } finally {
            path.clear(nodeIndex);
        }
    }

    private static void appendGraphProcess(CraftingGraphDisplaySnapshot.Node node, int nodeIndex,
                                           List<LiteCraftTreeProc> processes,
                                           CraftingGraphDisplaySnapshot snapshot,
                                           BitSet path, BitSet materialized, MissingAllocator missingAllocator) {
        var children = new ObjectArrayList<LiteCraftTreeNode>(node.inputs().size());
        var process = new LiteCraftTreeProc(children, node.machines(), node.machineLocations(), node.pattern(), -1);
        appendEdges(node.inputs(), nodeIndex, node.craftTimes(), children, process, snapshot, path, materialized,
            missingAllocator);
        if (!children.isEmpty()) processes.add(process);
    }

    private static void appendLocalProcesses(LocalDisplayFragment fragment,
                                             List<LiteCraftTreeProc> processes,
                                             List<CraftingGraphDisplaySnapshot.Edge> edges, long craftTimes,
                                             CraftingGraphDisplaySnapshot snapshot,
                                             BitSet path, BitSet materialized, MissingAllocator missingAllocator) {
        for (var local : fragment.processes()) {
            var children = new ObjectArrayList<LiteCraftTreeNode>(local.inputs().size());
            var process = new LiteCraftTreeProc(children, local.machines(), local.locations(), local.pattern(), -1);
            for (var child : local.inputs()) {
                children.add(materializeLocal(child, process, missingAllocator));
            }
            processes.add(process);
        }
        processes.removeIf(process -> process.inputs().isEmpty());
        // Boundary edges belong to the compatibility unit as a whole, not to any real local process.
        if (snapshot != null) {
            var children = new ObjectArrayList<LiteCraftTreeNode>();
            var boundaryProcess = new LiteCraftTreeProc(children, List.of(), Map.of());
            appendEdges(edges, -1, craftTimes, children, boundaryProcess, snapshot, path, materialized,
                missingAllocator);
            if (!children.isEmpty()) {
                processes.add(boundaryProcess);
            }
        }
    }

    private static LiteCraftTreeNode materializeLocal(LocalDisplayFragment fragment, LiteCraftTreeProc parent,
                                                      MissingAllocator missingAllocator) {
        var processes = new ObjectArrayList<LiteCraftTreeProc>();
        appendLocalProcesses(fragment, processes, List.of(), 0, null, new BitSet(), new BitSet(), missingAllocator);
        return new LiteCraftTreeNode(parent, new GenericStack(fragment.output(), fragment.amount()), processes,
            missingAllocator.allocate(fragment.output(), fragment.missing()));
    }

    /**
     * @param nodeIndex the node taking the edges, or -1 for the boundary of a local unit
     */
    private static void appendEdges(List<CraftingGraphDisplaySnapshot.Edge> edges, int nodeIndex, long craftTimes,
                                    List<LiteCraftTreeNode> children, LiteCraftTreeProc process,
                                    CraftingGraphDisplaySnapshot snapshot, BitSet path, BitSet materialized,
                                    MissingAllocator missingAllocator) {
        for (var edge : edges) {
            if (edge.producerIndex() >= 0 && edge.allocatedOutput() > 0) {
                children.add(materialize(snapshot, edge.producerIndex(), process,
                    edge.allocatedOutput(), path, materialized, missingAllocator));
            }
            if (isFeedback(edge) && craftTimes > 0) {
                long fedBack = LongMath.saturatedMultiply(edge.amountPerCraft(), craftTimes);
                if (edge.producerIndex() != nodeIndex) {
                    // Around a ring, what comes from storage or is missing is shown as such below, not fed back
                    fedBack = Math.max(0, fedBack - edge.seedRequired());
                }
                if (fedBack > 0) {
                    children.add(new LiteCraftTreeNode(process, new GenericStack(edge.inputKey(), fedBack), List.of(),
                        0, LiteCraftTreeNode.FEEDBACK_PENDING));
                }
            }
            long terminalAmount = edge.extractedExternal() + edge.missing();
            if (terminalAmount > 0) {
                children.add(new LiteCraftTreeNode(process, new GenericStack(edge.inputKey(), terminalAmount),
                    List.of(), missingAllocator.allocate(edge.inputKey(), edge.missing())));
            }
        }
    }

    private static final class MissingAllocator {
        private final Object2LongOpenHashMap<AEKey> remaining = new Object2LongOpenHashMap<>();

        private MissingAllocator(KeyCounter missingItems) {
            missingItems.forEach(remaining::put);
        }

        private long allocate(AEKey key, long requested) {
            if (requested <= 0) return 0;
            long available = remaining.getLong(key);
            long allocated = Math.min(requested, available);
            if (allocated > 0) remaining.addTo(key, -allocated);
            return allocated;
        }
    }
}
