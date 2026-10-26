package ae2.crafting.graph;

import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyAmounts;
import ae2.api.stacks.GenericStack;
import ae2.crafting.CraftingCalculation;
import ae2.crafting.pattern.AECraftingPattern;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraphBuilder {
    private final CraftingCalculation calc;
    private final boolean trackPerformance;
    private long patternLookupNanos;
    private long emitLookupNanos;
    private long nodeCount;
    private final Set<AEKey> substituteClosureVisited = new ObjectOpenHashSet<>();
    private final Map<AEKey, IPatternDetails> choices;

    public GraphBuilder(CraftingCalculation calc) {
        this(calc, Map.of());
    }

    /**
     * Builds the graph with the given pattern of each key instead of the one of highest priority.
     */
    public GraphBuilder(CraftingCalculation calc, Map<AEKey, IPatternDetails> choices) {
        this.calc = calc;
        this.trackPerformance = calc.isPerformanceTrackingEnabled();
        this.choices = choices;
    }

    /**
     * For each key reachable from the output, the pattern needing the least of the scarce keys per item it makes. On a
     * tie, the one of highest priority. Patterns with dynamic inputs are validated by a fresh legacy attempt.
     */
    public Map<AEKey, IPatternDetails> choosePatterns(AEKey output, Object2DoubleMap<AEKey> scarcity)
        throws InterruptedException {
        var chosen = new Object2ObjectOpenHashMap<AEKey, IPatternDetails>();
        price(output, scarcity, new Object2DoubleOpenHashMap<>(), chosen);
        return chosen;
    }

    private double price(AEKey what, Object2DoubleMap<AEKey> scarcity, Object2DoubleOpenHashMap<AEKey> prices,
                         Map<AEKey, IPatternDetails> chosen) throws InterruptedException {
        if (prices.containsKey(what)) {
            return prices.getDouble(what);
        }
        double own = scarcity.getDouble(what);
        if (calc.canEmitFor(what)) {
            prices.put(what, 0);
            return 0;
        }
        var patterns = calc.getCraftingFor(what);
        // A cycle back to the key is priced as free, so cycles alone never change the choice
        prices.put(what, 0);
        calc.handlePausing();
        var primary = patterns.isEmpty() ? null : patterns.getFirst();
        IPatternDetails bestPattern = primary;
        double best = Double.POSITIVE_INFINITY;
        for (int patternIndex = 0, patternCount = patterns.size(); patternIndex < patternCount; patternIndex++) {
            var pattern = patterns.get(patternIndex);
            long made = candidateOutputAmount(what, pattern);
            if (made <= 0) continue;
            double cost = 0;
            for (var input : pattern.getInputs()) {
                var possible = input.possibleInputs();
                if (possible.length == 0 || input.getMultiplier() <= 0) continue;
                double amount = (double) possible[0].amount() * input.getMultiplier();
                cost += amount * price(possible[0].what(), scarcity, prices, chosen);
            }
            cost /= made;
            if (cost < best) {
                best = cost;
                bestPattern = pattern;
            }
        }
        if (bestPattern != primary) {
            chosen.put(what, bestPattern);
        }
        double result = best == Double.POSITIVE_INFINITY ? own : own + best;
        prices.put(what, result);
        return result;
    }

    private static boolean isNativePattern(IPatternDetails pattern) {
        if (PseudoPatternDetails.isPseudo(pattern)) return true;
        if (pattern instanceof AECraftingPattern assembler && assembler.hasRemainders()) return false;
        if (pattern.getOutputs().size() != 1) return false;
        for (var input : pattern.getInputs()) {
            var possible = input.possibleInputs();
            if (possible.length != 1 || possible[0].amount() <= 0 || input.getMultiplier() <= 0
                || input.getRemainingKey(possible[0].what()) != null) {
                return false;
            }
        }
        return true;
    }

    public CraftingGraph buildGraph(AEKey output, long requestedAmount) throws InterruptedException {
        long start = this.trackPerformance ? System.nanoTime() : 0;
        this.patternLookupNanos = 0;
        this.emitLookupNanos = 0;
        this.nodeCount = 0;
        this.substituteClosureVisited.clear();
        var graph = new CraftingGraph();
        var requestStack = new ObjectOpenHashSet<AEKey>();

        var rootNode = buildNodeRecursive(output, graph, requestStack, true, false);
        if (rootNode == null) throw new IllegalStateException("Graph root was not created");
        graph.setRootNode(rootNode);
        rootNode.setDemandAmount(requestedAmount);

        if (this.trackPerformance) {
            long total = System.nanoTime() - start;
            calc.recordPerformanceCount("buildGraphNodes", nodeCount);
            calc.recordPerformanceStage("buildGraphPatternLookup", patternLookupNanos);
            calc.recordPerformanceStage("buildGraphEmitLookup", emitLookupNanos);
            calc.recordPerformanceStage("buildGraphStructure",
                Math.max(0, total - patternLookupNanos - emitLookupNanos));
        }
        return graph;
    }

    /**
     * Records the material structure the tree can reach through fuzzy substitution.
     *
     * <p>The tree replaces a key without patterns of its own by a craftable fuzzy variant and crafts that variant's
     * patterns instead, so their material is consumed even though the graph never expands it. Every craftable fuzzy
     * variant and every candidate pattern is followed so the snapshot keeps a complete material set.</p>
     */
    private void collectSubstituteClosure(AEKey what, CraftingGraph graph) throws InterruptedException {
        if (!this.substituteClosureVisited.add(what)) {
            return;
        }
        graph.addExtraSnapshotKey(what);
        calc.handlePausing();
        var patterns = calc.getCraftingFor(what);
        for (int patternIndex = 0, patternCount = patterns.size(); patternIndex < patternCount; patternIndex++) {
            var pattern = patterns.get(patternIndex);
            graph.recordPatternDefinition(pattern.getDefinition());
        }
        var aggregated = new Object2LongLinkedOpenHashMap<AEKey>();
        var candidates = new ObjectArrayList<MaterialClosure.Candidate>(patterns.size());
        for (int patternIndex = 0, patternCount = patterns.size(); patternIndex < patternCount; patternIndex++) {
            var pattern = patterns.get(patternIndex);
            var patternInputs = new Object2LongLinkedOpenHashMap<AEKey>();
            for (var input : pattern.getInputs()) {
                if (input.getMultiplier() <= 0) {
                    continue;
                }
                for (var possible : input.possibleInputs()) {
                    if (possible.amount() <= 0) {
                        continue;
                    }
                    var amount = KeyAmounts.multiply(possible.what(), possible.amount(), input.getMultiplier());
                    patternInputs.put(possible.what(), Math.max(patternInputs.getLong(possible.what()), amount));
                }
            }
            for (var entry : patternInputs.object2LongEntrySet()) {
                aggregated.put(entry.getKey(), Math.max(aggregated.getLong(entry.getKey()), entry.getLongValue()));
            }
            long candidateOutput = candidateOutputAmount(what, pattern);
            if (candidateOutput > 0) {
                candidates.add(new MaterialClosure.Candidate(candidateOutput, copyInputs(patternInputs)));
            }
        }
        boolean emittable = calc.canEmitFor(what);
        graph.addClosureNode(what, new MaterialClosure.Node(emittable, null, copyInputs(aggregated), candidates));
        if (emittable) {
            return;
        }
        if (patterns.isEmpty()) {
            collectFuzzyVariants(what, graph);
            return;
        }
        for (int patternIndex = 0, patternCount = patterns.size(); patternIndex < patternCount; patternIndex++) {
            var pattern = patterns.get(patternIndex);
            for (var input : pattern.getInputs()) {
                if (input.getMultiplier() <= 0) {
                    continue;
                }
                for (var possible : input.possibleInputs()) {
                    if (possible.amount() > 0) {
                        collectSubstituteClosure(possible.what(), graph);
                    }
                }
            }
        }
    }

    private static List<MaterialClosure.Input> copyInputs(Object2LongLinkedOpenHashMap<AEKey> inputs) {
        var result = new ObjectArrayList<MaterialClosure.Input>(inputs.size());
        for (var entry : inputs.object2LongEntrySet()) {
            result.add(new MaterialClosure.Input(entry.getKey(), entry.getLongValue()));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * @return how much of the key one craft of the pattern yields, or 0 when the pattern cannot produce the key
     */
    private static long candidateOutputAmount(AEKey what, IPatternDetails pattern) {
        long amount = 0;
        for (var output : pattern.getOutputs()) {
            if (output.amount() > 0 && output.what().equals(what)) {
                amount = KeyAmounts.add(what, amount, output.amount());
            }
        }
        return amount;
    }

    private void collectFuzzyVariants(AEKey what, CraftingGraph graph) throws InterruptedException {
        var collected = new ObjectOpenHashSet<AEKey>();
        while (true) {
            var variant = calc.getFuzzyCraftable(what, candidate -> !collected.contains(candidate));
            if (variant == null) {
                return;
            }
            collected.add(variant);
            collectSubstituteClosure(variant, graph);
        }
    }

    private static long getRequestedOutputAmount(AEKey what, IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        long requestedOutputAmount = 0;
        for (var output : outputs) {
            if (output.amount() <= 0) {
                throw invalidPattern("empty pattern output");
            }
            if (output.what().equals(what)) {
                requestedOutputAmount = KeyAmounts.add(what, requestedOutputAmount, output.amount());
            }
        }
        if (requestedOutputAmount <= 0) {
            throw invalidPattern("mismatched pattern outputs");
        }
        return requestedOutputAmount;
    }

    private CraftingGraphNode buildNodeRecursive(
        AEKey what,
        CraftingGraph graph,
        Set<AEKey> requestStack,
        boolean root,
        boolean substitutable
    ) throws InterruptedException {
        long emitStart = this.trackPerformance ? System.nanoTime() : 0;
        boolean canEmit = calc.canEmitFor(what);
        if (this.trackPerformance) {
            this.emitLookupNanos += System.nanoTime() - emitStart;
        }
        if (canEmit) {
            var nodeKey = new CraftingGraph.NodeKey(what, null);
            var existing = graph.getNode(nodeKey);
            if (existing != null) {
                return existing;
            }
            graph.addClosureNode(what, MaterialClosure.Node.EMITTER);
            var emitter = new CraftingGraphNode(what, null, List.of(new GenericStack(what, 1)), 1);
            graph.putNode(nodeKey, emitter);
            this.nodeCount++;
            return emitter;
        }

        long patternStart = this.trackPerformance ? System.nanoTime() : 0;
        var patterns = calc.getCraftingFor(what);
        if (this.trackPerformance) {
            this.patternLookupNanos += System.nanoTime() - patternStart;
        }
        if (patterns.isEmpty()) {
            var substitute = substitutable ? calc.getFuzzyCraftable(what) : null;
            if (substitute != null) {
                graph.requireLegacyFallback();
                collectSubstituteClosure(substitute, graph);
            }
            graph.addClosureNode(what, new MaterialClosure.Node(false, substitute, List.of(), List.of()));
            if (root) {
                var nodeKey = new CraftingGraph.NodeKey(what, null);
                var leaf = new CraftingGraphNode(what, null, List.of(), 1, false, true);
                graph.putNode(nodeKey, leaf);
                this.nodeCount++;
                return leaf;
            }
            return null;
        }
        // With several patterns, the graph is planned with the one of highest priority first, see
        // CraftingCalculation#tryAlternatives
        boolean localUnit = false;
        String localReason = null;
        if (patterns.size() != 1) {
            graph.markAlternatives();
            // A native node commits to one producer. Recursive alternatives may have to share the request
            // when each has a different finite fuel supply; choosing either one alone invents a shortage.
            localUnit = patterns.stream().anyMatch(pattern -> java.util.Arrays.stream(pattern.getInputs())
                .anyMatch(input -> java.util.Arrays.stream(input.possibleInputs()).anyMatch(stack -> stack.what().equals(what))));
            if (localUnit) localReason = "recursive-alternatives";
        }

        var primaryPattern = this.choices.getOrDefault(what, patterns.getFirst());
        for (int patternIndex = 0, patternCount = patterns.size(); patternIndex < patternCount; patternIndex++) {
            var pattern = patterns.get(patternIndex);
            graph.recordPatternDefinition(pattern.getDefinition());
        }
        var nodeKey = new CraftingGraph.NodeKey(what, primaryPattern);

        var existing = graph.getNode(nodeKey);
        if (existing != null) {
            return existing;
        }

        if (requestStack.contains(what)) {
            graph.markRecursion();
            return graph.getNode(nodeKey);
        }

        long outputAmount = getRequestedOutputAmount(what, primaryPattern);
        if (outputAmount <= 0) {
            throw invalidPattern("mismatched pattern outputs");
        }
        if (!localUnit && !isNativePattern(primaryPattern)) {
            localUnit = true;
            localReason = "unsupported-pattern-semantics";
        }
        if (localUnit || PseudoPatternDetails.isPseudo(primaryPattern)) {
            graph.requireLegacyFallback();
        }
        var node = new CraftingGraphNode(what, primaryPattern, patterns, primaryPattern.getOutputs(), outputAmount,
            localUnit, false, localReason);
        graph.putNode(nodeKey, node);
        this.nodeCount++;

        requestStack.add(what);
        calc.handlePausing();

        var graphInputs = new Object2LongLinkedOpenHashMap<AEKey>();
        // The closure keeps the inputs of every candidate, the graph only those of the patterns it plans with
        var closureInputs = new Object2LongLinkedOpenHashMap<AEKey>();
        var candidates = new ObjectArrayList<MaterialClosure.Candidate>(patterns.size());
        for (int patternIndex = 0, patternCount = patterns.size(); patternIndex < patternCount; patternIndex++) {
            var pattern = patterns.get(patternIndex);
            boolean planned = localUnit || pattern == primaryPattern;
            var patternInputs = new Object2LongLinkedOpenHashMap<AEKey>();
            for (var inputEntry : pattern.getInputs()) {
                var possibleInputs = inputEntry.possibleInputs();
                if (possibleInputs.length == 0 || inputEntry.getMultiplier() <= 0) continue;
                // Fuzzy variants are only consumed by ingredient substitution, an assembler-pattern feature, so gate
                // on the pattern type instead of inferring it from the input list. Pseudo patterns wrap the real one.
                boolean inputSubstitutable = PseudoPatternDetails.unwrap(pattern) instanceof IAssemblerPattern assembler
                    && assembler.canSubstitute();
                int possibleInputIndex = 0;
                while (possibleInputIndex < possibleInputs.length) {
                    var possible = possibleInputs[possibleInputIndex++];
                    if (possible.amount() > 0 && (!possible.what().equals(what) || !localUnit)) {
                        long amount = KeyAmounts.multiply(possible.what(), possible.amount(), inputEntry.getMultiplier());
                        patternInputs.put(possible.what(),
                            KeyAmounts.add(possible.what(), patternInputs.getLong(possible.what()), amount));
                        if (inputSubstitutable) {
                            graph.addFuzzyKey(possible.what());
                        }
                    }
                }
            }
            // A local unit is previewed with one selected pattern. Keep the largest per-key demand
            // across candidates, while still summing repeated slots within the same candidate.
            for (var input : patternInputs.object2LongEntrySet()) {
                closureInputs.put(input.getKey(),
                    Math.max(closureInputs.getLong(input.getKey()), input.getLongValue()));
                if (planned) {
                    graphInputs.put(input.getKey(),
                        Math.max(graphInputs.getLong(input.getKey()), input.getLongValue()));
                }
            }
            long candidateOutput = pattern == primaryPattern ? outputAmount : candidateOutputAmount(what, pattern);
            if (candidateOutput > 0) {
                candidates.add(new MaterialClosure.Candidate(candidateOutput, copyInputs(patternInputs)));
            }
        }
        graph.addClosureNode(what, new MaterialClosure.Node(false, null, copyInputs(closureInputs), candidates));

        for (var graphInput : graphInputs.object2LongEntrySet()) {
            var inputKey = graphInput.getKey();
            long inputAmount = graphInput.getLongValue();

            boolean inputSubstitutable = PseudoPatternDetails.unwrap(primaryPattern) instanceof IAssemblerPattern assembler
                && assembler.canSubstitute();
            var childNode = buildNodeRecursive(inputKey, graph, requestStack, false, inputSubstitutable);

            var edge = new CraftingGraphEdge(inputKey, inputAmount, childNode,
                PseudoPatternDetails.isPseudo(primaryPattern));
            node.addInput(edge);

            if (childNode != null) {
                childNode.addParent(node);
            }
        }

        // Material only the other patterns take still belongs in the snapshot and the closure, without nodes
        for (var closureInput : closureInputs.keySet()) {
            if (!graphInputs.containsKey(closureInput) && !graph.getClosureNodes().containsKey(closureInput)) {
                collectSubstituteClosure(closureInput, graph);
            }
        }

        requestStack.remove(what);
        return node;
    }

    private static IllegalArgumentException invalidPattern(String reason) {
        return new IllegalArgumentException("Invalid graph crafting pattern: " + reason);
    }
}
