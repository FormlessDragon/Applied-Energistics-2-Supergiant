package ae2.crafting.graph;

import ae2.api.config.Actionable;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.CraftBranchFailure;
import ae2.crafting.CraftingCalculation;
import ae2.crafting.inv.ChildCraftingSimulationState;
import ae2.crafting.inv.CraftingSimulationState;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Set;

public class GraphExecutor {
    private final CraftingCalculation calc;
    private final CraftingGraph graph;

    public GraphExecutor(CraftingCalculation calc, CraftingGraph graph) {
        this.calc = calc;
        this.graph = graph;
    }

    public CraftingGraphDisplaySnapshot.Builder applyGraph(CraftingSimulationState inv) throws InterruptedException {
        var order = graph.getTopologicalOrder();
        var snapshot = CraftingGraphDisplaySnapshot.builder(graph);
        var appliedComponents = new IntOpenHashSet();

        for (int nodeIndex = order.size() - 1; nodeIndex >= 0; nodeIndex--) {
            var node = order.get(nodeIndex);
            calc.handlePausing();
            if (node.getLocalComponentId() >= 0) {
                if (appliedComponents.add(node.getLocalComponentId())) {
                    applyLocalComponent(node.getLocalComponentId(), inv, snapshot);
                }
                continue;
            }
            if (node.isExternalLeaf()) {
                boolean root = node == graph.getRootNode();
                inv.addStackBytes(node.getWhat(), 1, node.getDemandAmount());
                long extracted = root ? 0 : inv.extract(node.getWhat(), node.getDemandAmount(), Actionable.MODULATE);
                long missing = node.getDemandAmount() - extracted;
                snapshot.recordNode(node);
                snapshot.recordExternal(node, extracted);
                snapshot.recordMissing(node, missing);
                if (missing > 0) calc.addMissing(node.getWhat(), missing);
                continue;
            }
            snapshot.recordNode(node);
            if (node.getCraftTimes() == 0 && !hasCycleSeed(node)) continue;
            if (node.isLocalUnit()) {
                applyLocalUnit(node, inv, snapshot);
            } else {
                applyNode(node, inv, snapshot);
            }
        }
        var root = graph.getRootNode();
        if (root == null) {
            throw new IllegalStateException("Graph has no root node");
        }
        if (root.getLocalComponentId() < 0 && !root.isLocalUnit() && !root.isExternalLeaf()
            && root.getCraftTimes() >= 0) {
            // No parent edge owns the root native/emitter output stack.
            inv.addStackBytes(root.getWhat(), root.getDemandAmount(), 1);
        }
        // The display snapshot is only materialized when the GUI requests it, not during calculation.
        snapshot.setRequestedAmount(root.getDemandAmount());
        return snapshot;
    }

    private void applyLocalComponent(int componentId, CraftingSimulationState inv,
                                     CraftingGraphDisplaySnapshot.Builder snapshot) throws InterruptedException {
        var discovery = graph.getLocalComponentPlan(componentId);
        if (discovery == null) {
            throw new IllegalStateException("Recursive component has no local plan: " + componentId);
        }
        var component = graph.getTopology().getComponents().get(componentId);
        var topology = graph.getTopology();
        var marker = calc.createCalculationMarker();
        var transaction = new ChildCraftingSimulationState(inv);
        var boundaryResults = new ObjectArrayList<BoundaryResult>();
        try {
            for (var boundary : discovery.boundaryDemands()) {
                calc.handlePausing();
                var edge = findBoundaryEdge(topology, componentId, component.nodes(), boundary.getKey());
                long extracted = transaction.extract(boundary.getKey(), boundary.getLongValue(), Actionable.MODULATE);
                transaction.addStackBytes(boundary.getKey(), extracted, 1);
                boundaryResults.add(new BoundaryResult(boundary.getKey(), boundary.getLongValue(), extracted, edge));
            }
            discovery.commit(calc, transaction);
            transaction.applyDiff(inv);
        } catch (RuntimeException | Error failure) {
            calc.restoreCalculationMarker(marker);
            throw failure;
        }

        for (var result : boundaryResults) {
            if (result.edge != null) snapshot.recordEdge(result.edge, result.needed, result.extracted);
            if (result.extracted < result.needed) {
                calc.addMissing(result.key, result.needed - result.extracted);
            }
        }
        for (var entry : discovery.entries()) {
            if (entry.nodeIndex() < 0 || entry.nodeIndex() >= component.nodes().size()) {
                throw new IllegalStateException("Local component entry node index out of range: " + entry.nodeIndex());
            }
            var node = component.nodes().get(entry.nodeIndex());
            node.setCraftTimes(entry.craftTimes());
            snapshot.recordNode(node);
            snapshot.recordLocalDisplay(node, entry.display());
            if (entry.pattern() == null) continue;
            for (var output : entry.pattern().getOutputs()) {
                snapshot.recordOutput(node, output.what(),
                    LongMath.saturatedMultiply(output.amount(), entry.craftTimes()));
            }
        }
        for (var entry : discovery.entries()) {
            var node = component.nodes().get(entry.nodeIndex());
            for (var edge : node.getInputs()) {
                if (edge.producer() != null && topology.getComponentId(edge.producer()) == componentId) {
                    snapshot.recordInternalEdge(edge,
                        LongMath.saturatedMultiply(edge.amountPerCraft(), entry.craftTimes()));
                }
            }
        }
    }

    private static void ensureBoundaryCovers(KeyCounter planned, KeyCounter actual, String message) {
        actual.forEach((entryKey, entryAmount) -> {
            if (entryAmount > planned.get(entryKey)) {
                throw new IllegalStateException(message + ": " + entryKey);
            }
        });
    }

    private record BoundaryResult(AEKey key, long needed, long extracted,
                                  CraftingGraphEdge edge) {
    }

    private static void ensurePlannedBoundaryCovers(CraftingGraphNode node,
                                                    KeyCounter actual) {
        var planned = node.getPlannedBoundaryDemands();
        if (planned == null) {
            throw new IllegalStateException("Local crafting unit has no boundary plan for " + node.getWhat());
        }
        ensureBoundaryCovers(planned, actual, "Local crafting boundary grew for " + node.getWhat());
    }

    private void applyLocalUnit(CraftingGraphNode node, CraftingSimulationState inv,
                                CraftingGraphDisplaySnapshot.Builder snapshot) throws InterruptedException {
        try {
            calc.recordLocalReplan();
            var plan = graph.getLocalUnitPlan(node);
            if (plan == null || !plan.canApplyTo(inv) || plan.craftTimes() != node.getCraftTimes()) {
                // The discovery plan is stale (different demand or the execution inventory no longer satisfies it);
                // resolve lazily instead of failing on a stale reuse.
                plan = calc.previewLocalUnit(node.getWhat(), node.getPatternCandidates(), node.getDemandAmount(),
                    Set.of(node.getWhat()), inv);
            }
            var boundaryDemands = plan.boundaryDemands();
            ensurePlannedBoundaryCovers(node, boundaryDemands);
            var transaction = new ChildCraftingSimulationState(inv);
            var marker = calc.createCalculationMarker();
            var boundaryResults = new ObjectArrayList<BoundaryResult>();
            try {
                boundaryDemands.forEach((entryKey, entryAmount) -> {
                    var edge = findBoundaryEdge(node, entryKey);
                    long extracted = transaction.extract(entryKey, entryAmount, Actionable.MODULATE);
                    transaction.addStackBytes(entryKey, extracted, 1);
                    boundaryResults.add(new BoundaryResult(entryKey, entryAmount, extracted, edge));
                });
                calc.commitLocalPattern(plan, transaction);
                transaction.applyDiff(inv);
            } catch (RuntimeException | Error failure) {
                calc.restoreCalculationMarker(marker);
                throw failure;
            }
            node.setCraftTimes(plan.craftTimes());
            for (var result : boundaryResults) {
                if (result.edge != null) {
                    snapshot.recordEdge(result.edge, result.needed, result.extracted);
                }
                if (result.extracted < result.needed) {
                    calc.addMissing(result.key, result.needed - result.extracted);
                }
            }
            snapshot.recordLocalDisplay(node, plan.displayFragment());
            for (var output : plan.pattern().getOutputs()) {
                snapshot.recordOutput(node, output.what(),
                    LongMath.saturatedMultiply(output.amount(), plan.craftTimes()));
            }
        } catch (CraftBranchFailure e) {
            throw new IllegalStateException("Local crafting unit failed for " + node.getWhat(), e);
        }
    }

    private static CraftingGraphEdge findBoundaryEdge(CraftingGraphNode node, AEKey key) {
        for (var edge : node.getInputs()) {
            if (edge.inputKey().equals(key)) return edge;
        }
        return null;
    }

    private static CraftingGraphEdge findBoundaryEdge(CraftingGraphTopology topology, int componentId,
                                                      List<CraftingGraphNode> nodes, AEKey key) {
        for (var node : nodes) {
            for (var edge : node.getInputs()) {
                if (edge.inputKey().equals(key)
                    && (edge.producer() == null || topology.getComponentId(edge.producer()) != componentId)) {
                    return edge;
                }
            }
        }
        return null;
    }

    private void applyNode(CraftingGraphNode node, CraftingSimulationState inv,
                           CraftingGraphDisplaySnapshot.Builder snapshot) {
        long times = node.getCraftTimes();
        var pattern = node.getPattern();

        if (node.isEmitter()) {
            applyEmitter(node, inv, snapshot, times, node == graph.getRootNode());
            return;
        }

        var sccPlan = graph.getTopology().isCyclic(node)
            ? graph.getSccPlan(graph.getTopology().getComponentId(node)) : null;
        boolean ring = sccPlan != null && sccPlan.ring();
        CraftingGraphEdge cycleCut = null;
        var inputs = node.getInputs();
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            var edge = inputs.get(inputIndex);
            if (cycleCut == null && edge.cycleCut()) cycleCut = edge;
            long needed = edge.cycleCut() ? edge.seedDemand()
                : LongMath.saturatedMultiply(edge.amountPerCraft(), times);
            boolean ownsInputBytes = edge.producer() == null
                || (edge.producer().getLocalComponentId() < 0 && !edge.producer().isLocalUnit());
            if (ownsInputBytes && !edge.cycleCut()) {
                inv.addStackBytes(edge.inputKey(), edge.amountPerCraft(), times);
            }
            if (edge.cycleCut() && needed == 0) {
                snapshot.recordEdge(edge, 0, 0);
                continue;
            }
            long got = inv.extract(edge.inputKey(), needed, Actionable.MODULATE);
            if (edge.pseudoLane() && got < needed) {
                got = LongMath.saturatedAdd(got,
                    inv.extractPseudo(edge.inputKey(), needed - got, Actionable.MODULATE));
            }

            long shortfall = needed - got;
            if (edge.cycleCut()) {
                if (ownsInputBytes) inv.addStackBytes(edge.inputKey(), got, 1);
                edge.recordSeedResult(got, shortfall);
                if (ring) {
                    // A ring plans its reserve itself. A seed it lacks is a recursive missing seed, which keeps it
                    // missing although the ring crafts more of it.
                    if (shortfall > 0) {
                        calc.recordGraphRecursiveSeed(node.getWhat(), edge.inputKey(), needed, got, shortfall, 0);
                    }
                } else {
                    calc.recordGraphRecursiveSeed(node.getWhat(), edge.inputKey(), needed, got, shortfall,
                        edge.reserveDemand());
                }
            }
            snapshot.recordEdge(edge, needed, got);
            if (shortfall > 0) {
                if (!edge.cycleCut()) {
                    calc.addMissing(edge.inputKey(), shortfall);
                }
            }
        }

        var outputs = node.getOutputs();
        for (int outputIndex = 0; outputIndex < outputs.size(); outputIndex++) {
            var output = outputs.get(outputIndex);
            long outputAmount = LongMath.saturatedMultiply(output.amount(), times);
            if (ring && output.what().equals(node.getWhat())) {
                // A node of the ring applied before this one already took its share by a cut input
                outputAmount = Math.max(0, outputAmount - sccPlan.takenByCycle(node));
            } else if (cycleCut != null && output.what().equals(node.getWhat())) {
                if (sccPlan != null) {
                    // Replace the planned seed portion with the amount actually allocated. Missing seeds must not
                    // become synthetic output; the remaining planned amount is the real net gain of crafted batches.
                    long planned = sccPlan.plannedOutput(node);
                    long netGain = Math.max(0, planned - cycleCut.seedRequired());
                    outputAmount = LongMath.saturatedAdd(cycleCut.seedAllocated(), netGain);
                }
            }
            if (PseudoPatternDetails.isPseudo(pattern) && output.what().equals(calc.getOutput())) {
                inv.insertPseudo(output.what(), outputAmount, Actionable.MODULATE);
            } else {
                inv.insert(output.what(), outputAmount, Actionable.MODULATE);
            }
            snapshot.recordOutput(node, output.what(), outputAmount);
        }

        inv.addCrafting(pattern, times);

        inv.addBytes(times);

        if (ring && node == graph.getRootNode()) {
            // The ring takes back what was crafted of the requested item beyond the request; the CPU keeps it
            long crafted = LongMath.saturatedMultiply(node.getOutputPerCraft(), times);
            calc.addGraphIntermediateFinalOutput(Math.max(0, crafted - node.getDemandAmount()));
        }
    }

    private static boolean hasCycleSeed(CraftingGraphNode node) {
        for (var edge : node.getInputs()) {
            if (edge.cycleCut() && edge.seedDemand() > 0) return true;
        }
        return false;
    }

    private void applyEmitter(CraftingGraphNode node, CraftingSimulationState inv,
                              CraftingGraphDisplaySnapshot.Builder snapshot, long amount, boolean root) {
        long extracted = root ? 0 : inv.extract(node.getWhat(), amount, Actionable.MODULATE);
        long emitted = amount - extracted;
        if (emitted > 0) {
            inv.emitItems(node.getWhat(), emitted);
        }
        // Downstream graph consumers still extract their input from the simulation inventory. Reinsert the fulfilled
        // amount for them; at the root, the extraction/emission itself is the final fulfillment.
        if (!node.getParents().isEmpty()) {
            inv.insert(node.getWhat(), amount, Actionable.MODULATE);
        }
        snapshot.recordOutput(node, node.getWhat(), amount);
    }
}
