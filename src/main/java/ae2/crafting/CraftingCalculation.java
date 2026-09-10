/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.crafting;

import ae2.api.config.Actionable;
import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSimulationRequester;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.AEKeyFilter;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.crafting.execution.CraftingSupplierLocator;
import ae2.crafting.execution.InputTemplate;
import ae2.crafting.graph.CraftingGraph;
import ae2.crafting.graph.CraftingGraphNode;
import ae2.crafting.graph.DemandPropagation;
import ae2.crafting.graph.GraphBuilder;
import ae2.crafting.graph.GraphExecutor;
import ae2.crafting.graph.LocalComponentPlan;
import ae2.crafting.graph.LocalDisplayFragment;
import ae2.crafting.graph.LocalPatternPlan;
import ae2.crafting.graph.SccPlan;
import ae2.crafting.inv.ChildCraftingSimulationState;
import ae2.crafting.inv.CraftingSimulationState;
import ae2.crafting.inv.NetworkCraftingSimulationState;
import ae2.debug.TileCraftingTreeTest;
import ae2.hooks.ticking.TickHandler;
import ae2.me.service.CraftingService;
import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class CraftingCalculation {
    private static final int MAX_LOCAL_REPLANS = 1_000_000;

    final ICraftingSimulationRequester simRequester;
    private final ICraftingService craftingService;
    private final NetworkCraftingSimulationState networkInv;
    private final World level;
    private final KeyCounter missing = new KeyCounter();
    private final KeyCounter recursiveMissingSeeds = new KeyCounter();
    private final Object monitor = new Object();
    private final CraftingPerformanceListener performanceListener;
    private final CraftingAttemptMetrics attemptMetrics = new CraftingAttemptMetrics();
    private final Stopwatch watch = Stopwatch.createUnstarted();
    private int localReplans;
    private @Nullable CraftingTreeNode tree;
    private final AEKey output;
    private final ObjectArrayList<AEKey> requestStack = new ObjectArrayList<>();
    private final ObjectArrayList<AEKey> availabilityStack = new ObjectArrayList<>();
    private final ObjectArrayList<CraftingTreeProcess> processStack = new ObjectArrayList<>();
    private final IntArrayList processHashPrefixes = new IntArrayList();
    private final IntArrayList processHashPowers = new IntArrayList();
    private final List<TimingFrame> timingStack = new ObjectArrayList<>();
    private final Map<AEKey, ObjectList<IPatternDetails>> patternCache = new Object2ObjectOpenHashMap<>();
    private @Nullable Map<AEKey, List<IPatternDetails>> additionalPatternsByOutput;
    private final Map<IPatternDetails, AEKey2LongMap> expandedPatternNetOutputCache = new Reference2ObjectOpenHashMap<>();
    private final Map<IPatternDetails, AEKey2LongMap> patternOutputCountCache = new Reference2ObjectOpenHashMap<>();
    private final Map<IPatternDetails, AEKey2LongMap> patternInputCountCache = new Reference2ObjectOpenHashMap<>();
    private final Map<IPatternDetails, Map<AEKey, RecursivePatternBatch>> recursivePatternBatchCache =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2BooleanOpenHashMap<IPatternDetails> positiveRecursiveNetCache =
        new Reference2BooleanOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IPatternDetails, CraftingTreeProcess.MachineInfo> machineInfoCache =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<ICraftingProvider, CraftingSupplierLocation> machineLocationCache =
        new Reference2ObjectOpenHashMap<>();
    private @Nullable IGrid machineLocationCacheGrid;
    private final Map<RecursiveNetKey, RecursiveNet> recursiveNetCache = new Object2ObjectOpenHashMap<>();
    private final RecursiveNetKey recursiveNetLookup = RecursiveNetKey.mutableProbe();
    private final ObjectPool<RecursiveNetKey> recursiveNetKeyPool = new ObjectPool<>(
        RecursiveNetKey::new,
        100
    );
    private final Set<AEKey> realSeededRecursiveRequests = new ObjectOpenHashSet<>();
    private final Set<AEKey> realRecursiveSeeds = new ObjectOpenHashSet<>();
    private final Set<AEKey> realSeededRecursiveKeys = new ObjectOpenHashSet<>();
    private final Cache<Object, Object> memoCache;
    private boolean memoReplayEnabled = true;
    private final Set<AEKey> recursiveFinalOutputInputs = new ObjectOpenHashSet<>();
    private final KeyCounter recursiveReserveCandidates = new KeyCounter();
    private final Reference2LongOpenHashMap<CraftingTreeNode> recursiveDisplayRequests = new Reference2LongOpenHashMap<>();
    private @Nullable CraftingGraph graph;
    private final List<ICraftingProvider> temporaryProviders;
    private final long recursiveIngredientReserveAmount;
    // The initially requested amount of "output", may be reduced depending on the strategy used
    private final long requestedAmount;
    private final CalculationStrategy strategy;
    private final List<CraftAttempt> attempts = AELog.isCraftingLogEnabled() ? new ObjectArrayList<>() : null;
    private boolean simulate = false;
    private boolean allowMissing = false;
    private int missingSuppression = 0;
    private boolean running = false;
    private boolean done = false;
    private int time = 5;
    private int incTime = Integer.MAX_VALUE;
    private int maxRequestDepth = 0;
    private long intermediateFinalOutputAmount = 0;
    private int recursiveMissingSeedSuppression = 0;
    @Nullable
    private KeyCounter reserveProtectedMissingSeeds = null;
    private boolean applyingRecursiveIngredientReserve = false;
    private boolean recursiveReserveBlockedByMissingSeed = false;
    private boolean localUnitMode = false;
    private @Nullable Set<AEKey> localUnitScope;
    private @Nullable KeyCounter localBoundaryDemands;
    private boolean usedCraftingTreeTestPattern;

    public CraftingCalculation(World level, IGrid grid, ICraftingSimulationRequester simRequester,
                               GenericStack output, CalculationStrategy strategy,
                               Object sharedMemoCache) {
        this(level, grid, simRequester, output, strategy, createPerformanceListener(), sharedMemoCache);
    }

    private CraftingCalculation(World level, IGrid grid, ICraftingSimulationRequester simRequester,
                                GenericStack output, CalculationStrategy strategy,
                                CraftingPerformanceListener performanceListener,
                                @Nullable Object sharedMemoCache) {
        this.level = level;
        this.output = output.what();
        this.requestedAmount = output.amount();
        this.strategy = strategy;
        this.simRequester = simRequester;
        this.performanceListener = performanceListener;
        this.temporaryProviders = List.copyOf(simRequester.getAdditionalProviders());

        if (sharedMemoCache instanceof Cache) {
            @SuppressWarnings("unchecked")
            Cache<Object, Object> cache = (Cache<Object, Object>) sharedMemoCache;
            this.memoCache = cache;
        } else {
            this.memoCache = CacheBuilder.newBuilder().maximumSize(0).build();
        }

        var storage = grid.getStorageService();
        var craftingService = grid.getCraftingService();
        this.craftingService = craftingService;
        this.networkInv = createNetworkInventory(storage);
        this.recursiveIngredientReserveAmount = Math.max(0, craftingService.getRecursiveIngredientReserveAmount());
        this.processHashPrefixes.add(0);
        this.processHashPowers.add(1);

    }

    /**
     * Snapshots the network inventory on the server thread before the job runs. Small networks are copied in full
     * (cheap); large networks reuse the cached graph structure of the requested output to copy only the fuzzy groups
     * that the crafting graph actually touches. Cold starts without a cached graph fall back to the full copy.
     */
    private NetworkCraftingSimulationState createNetworkInventory(IStorageService storage) {
        // Fetch the cached network inventory exactly once: fetching may trigger an expensive full rebuild when the
        // cache is dirty, so it must not happen once per snapshot branch.
        var cached = storage.getCachedInventory();
        if (cached.size() > NetworkCraftingSimulationState.SNAPSHOT_SUBSET_THRESHOLD
            && this.craftingService instanceof CraftingService service) {
            var graph = service.peekCachedGraph(this.output);
            if (graph != null) {
                var keys = graph.getSnapshotKeys();
                var subset = new NetworkCraftingSimulationState(cached, keys);
                recordPerformanceCount("inventorySnapshotKeys", subset.getSnapshotEntryCount());
                return subset;
            }
        }
        var snapshot = new NetworkCraftingSimulationState(cached);
        recordPerformanceCount("inventorySnapshotKeys", snapshot.getSnapshotEntryCount());
        return snapshot;
    }

    private CraftingTreeNode getOrCreateLegacyTree() {
        var result = this.tree;
        if (result != null) {
            return result;
        }

        if (isPerformanceTrackingEnabled()) {
            long treeStart = System.nanoTime();
            result = new CraftingTreeNode(this.craftingService, this, this.output, 1, null, -1);
            recordPerformanceStage("construct-tree", System.nanoTime() - treeStart);

            long preloadStart = System.nanoTime();
            preloadPatternCache(this.output);
            recordPerformanceStage("preload-pattern-cache", System.nanoTime() - preloadStart);
        } else {
            result = new CraftingTreeNode(this.craftingService, this, this.output, 1, null, -1);
            preloadPatternCache(this.output);
        }
        this.tree = result;
        return result;
    }

    private void preloadPatternCache(AEKey what) {
        var patterns = getCraftingFor(what);
        for (int i = 0, size = patterns.size(); i < size; i++) {
            var pattern = patterns.get(i);
            var inputs = pattern.getInputs();
            for (var input : inputs) {
                var possibleInputs = input.possibleInputs();
                for (var possible : possibleInputs) {
                    getCraftingFor(possible.what());
                }
            }
        }
    }

    private static Map<AEKey, List<IPatternDetails>> indexAdditionalPatterns(List<IPatternDetails> additionalPatterns) {
        var patternsByOutput = new Object2ObjectOpenHashMap<AEKey, List<IPatternDetails>>();
        if (additionalPatterns instanceof RandomAccess) {
            for (int i = 0, size = additionalPatterns.size(); i < size; i++) {
                var pattern = additionalPatterns.get(i);
                var output = pattern.getPrimaryOutput();
                patternsByOutput.computeIfAbsent(output.what(), ignored -> new ObjectArrayList<>()).add(pattern);
            }
        } else {
            for (var pattern : additionalPatterns) {
                var output = pattern.getPrimaryOutput();
                patternsByOutput.computeIfAbsent(output.what(), ignored -> new ObjectArrayList<>()).add(pattern);
            }
        }
        return patternsByOutput;
    }

    private Map<AEKey, List<IPatternDetails>> getAdditionalPatternsByOutput() {
        var result = this.additionalPatternsByOutput;
        if (result == null) {
            result = indexAdditionalPatterns(this.simRequester.getAdditionalPatterns());
            this.additionalPatternsByOutput = result;
        }
        return result;
    }

    private static CraftingPerformanceListener createPerformanceListener() {
        try {
            if (AEConfig.instance().isCraftingPerformanceLogEnabled()) {
                return new LoggingCraftingPerformanceListener();
            }
        } catch (IllegalStateException ignored) {
            // Tests can construct calculations before the mod configuration exists.
        }
        return CraftingPerformanceListener.NOOP;
    }

    private long getPatternOutputCount(IPatternDetails pattern, AEKey what) {
        var outputsByKey = this.patternOutputCountCache.computeIfAbsent(pattern,
            ignored -> new AEKey2LongMap.OpenHashMap());
        return outputsByKey.computeIfAbsent(what, key -> computePatternOutputCount(pattern, (AEKey) key));
    }

    private static long computePatternOutputCount(IPatternDetails pattern, AEKey what) {
        long total = 0;
        var outputs = pattern.getOutputs();
        if (outputs instanceof RandomAccess) {
            for (int i = 0, size = outputs.size(); i < size; i++) {
                var output = outputs.get(i);
                if (what.matches(output)) {
                    total = LongMath.saturatedAdd(total, output.amount());
                }
            }
        } else {
            for (var output : outputs) {
                if (what.matches(output)) {
                    total = LongMath.saturatedAdd(total, output.amount());
                }
            }
        }
        return total;
    }

    private long getPatternInputCount(IPatternDetails pattern, AEKey what) {
        var inputsByKey = this.patternInputCountCache.computeIfAbsent(pattern,
            ignored -> new AEKey2LongMap.OpenHashMap());
        return inputsByKey.computeIfAbsent(what, key -> computePatternInputCount(pattern, (AEKey) key));
    }

    private static long computePatternInputCount(IPatternDetails pattern, AEKey what) {
        long total = 0;
        for (var input : pattern.getInputs()) {
            var possibleInputs = input.possibleInputs();
            for (var possibleInput : possibleInputs) {
                if (what.matches(possibleInput)) {
                    total = LongMath.saturatedAdd(total,
                        LongMath.saturatedMultiply(possibleInput.amount(), input.getMultiplier()));
                    break;
                }
            }
        }
        return total;
    }

    private static void accumulatePatternNet(KeyCounter netByKey, IPatternDetails pattern) {
        accumulatePatternNet(netByKey, pattern, 1);
    }

    private static void accumulatePatternNet(KeyCounter netByKey, IPatternDetails pattern, long times) {
        accumulatePatternNet(netByKey, null, pattern, times);
    }

    private static void accumulatePatternNet(KeyCounter netByKey, @Nullable Collection<AEKey> inputKeys,
                                             IPatternDetails pattern, long times) {
        var outputs = pattern.getOutputs();
        if (outputs instanceof RandomAccess) {
            for (int i = 0, size = outputs.size(); i < size; i++) {
                var output = outputs.get(i);
                netByKey.add(output.what(), LongMath.saturatedMultiply(output.amount(), times));
            }
        } else {
            for (var output : outputs) {
                netByKey.add(output.what(), LongMath.saturatedMultiply(output.amount(), times));
            }
        }

        for (var input : pattern.getInputs()) {
            var possibleInputs = input.possibleInputs();
            if (possibleInputs.length == 0) {
                continue;
            }
            var primaryInput = possibleInputs[0];
            var inputKey = primaryInput.what();
            long inputAmount = LongMath.saturatedMultiply(primaryInput.amount(),
                LongMath.saturatedMultiply(input.getMultiplier(), times));
            netByKey.add(inputKey, LongMath.saturatedSubtract(0, inputAmount));
            if (inputKeys != null) {
                inputKeys.add(inputKey);
            }
        }
    }

    private static long divideCeil(long dividend, long divisor) {
        long quotient = dividend / divisor;
        if (dividend % divisor == 0) {
            return quotient;
        }
        return quotient + 1;
    }

    public void addMissing(AEKey what, long amount) {
        if (this.realSeededRecursiveKeys.contains(what) || this.realRecursiveSeeds.contains(what)
            || hasRealSeededRecursiveRequestFor(what)) {
            return;
        }
        missing.add(what, amount);
    }

    public ObjectList<IPatternDetails> getCraftingFor(AEKey what) {
        return this.patternCache.computeIfAbsent(what, key -> {
            var networkPatterns = this.craftingService.getCraftingFor(key);
            var patterns = new ObjectArrayList<IPatternDetails>();
            if (networkPatterns instanceof List<?> networkPatternList && networkPatternList instanceof RandomAccess) {
                for (int i = 0, size = networkPatternList.size(); i < size; i++) {
                    var pattern = (IPatternDetails) networkPatternList.get(i);
                    patterns.add(pattern);
                    if (TileCraftingTreeTest.isCraftingTreeTestPattern(pattern)) {
                        this.usedCraftingTreeTestPattern = true;
                    }
                }
            } else {
                for (var pattern : networkPatterns) {
                    patterns.add(pattern);
                    if (TileCraftingTreeTest.isCraftingTreeTestPattern(pattern)) {
                        this.usedCraftingTreeTestPattern = true;
                    }
                }
            }
            var additionalPatterns = this.getAdditionalPatternsByOutput().get(key);
            if (additionalPatterns != null) {
                for (int i = 0; i < additionalPatterns.size(); i++) {
                    var pattern = additionalPatterns.get(i);
                    patterns.add(pattern);
                    if (TileCraftingTreeTest.isCraftingTreeTestPattern(pattern)) {
                        this.usedCraftingTreeTestPattern = true;
                    }
                }
            }
            return patterns;
        });
    }

    public boolean canEmitFor(AEKey what) {
        return this.craftingService.canEmitFor(what);
    }

    @Nullable
    public AEKey getFuzzyCraftable(AEKey what) {
        return this.craftingService.getFuzzyCraftable(what, AEKeyFilter.none());
    }

    CraftingTreeProcess.MachineInfo getMachineInfo(ICraftingService craftingService, IPatternDetails pattern) {
        var grid = craftingService instanceof CraftingService service ? service.getGrid() : null;
        updateMachineLocationCacheGrid(grid);

        var machineInfo = this.machineInfoCache.get(pattern);
        if (machineInfo == null) {
            machineInfo = CraftingTreeProcess.collectMachineInfo(this, craftingService, pattern);
            this.machineInfoCache.put(pattern, machineInfo);
        }
        return machineInfo;
    }

    public CraftingTreeProcess.MachineInfo getMachineInfo(IPatternDetails pattern) {
        return getMachineInfo(this.craftingService, pattern);
    }

    public LocalPatternPlan previewLocalPattern(AEKey what, IPatternDetails pattern, long craftTimes,
                                                Set<AEKey> scope, CraftingSimulationState parent)
        throws InterruptedException, CraftBranchFailure {
        var localInventory = new ChildCraftingSimulationState(parent);
        var localRoot = new CraftingTreeNode(this.craftingService, this, what, 1, null, -1);
        var localProcess = new CraftingTreeProcess(this.craftingService, this, pattern, localRoot);
        try (var context = enterLocalUnit(scope)) {
            localProcess.request(localInventory, craftTimes);
            var boundaryDemands = context.boundaryDemands();
            var displayFragment = LocalDisplayFragment.capture(localRoot, craftTimes, keysOf(boundaryDemands),
                new LocalDisplayFragment.CaptureContext(context.missingDelta()));
            var calculationDelta = context.calculationDelta();
            context.rollback();
            this.attemptMetrics.recordLocalPatternPlan();
            return new LocalPatternPlan(pattern, craftTimes, boundaryDemands, localInventory.freezeDiff(),
                calculationDelta, displayFragment);
        }
    }

    public LocalPatternPlan previewLocalUnit(AEKey what, List<IPatternDetails> candidates, long requestedAmount,
                                             Set<AEKey> scope, CraftingSimulationState parent)
        throws InterruptedException, CraftBranchFailure {
        CraftBranchFailure lastFailure = null;
        for (int i = 0, size = candidates.size(); i < size; i++) {
            var pattern = candidates.get(i);
            long outputPerCraft = getPatternOutputCount(pattern, what);
            try {
                return previewLocalPattern(what, pattern, divideCeil(requestedAmount, outputPerCraft), scope, parent);
            } catch (CraftBranchFailure failure) {
                lastFailure = failure;
            }
        }
        if (lastFailure != null) throw lastFailure;
        throw new CraftBranchFailure(what, requestedAmount);
    }

    public LocalComponentPlan previewLocalComponent(List<CraftingGraphNode> nodes,
                                                    CraftingSimulationState parent)
        throws InterruptedException, CraftBranchFailure {
        return previewLocalComponent(nodes, null, 0, parent);
    }

    private LocalComponentPlan previewLocalComponent(List<CraftingGraphNode> nodes,
                                                     @Nullable CraftingGraphNode requestedNode,
                                                     long requestedAmount,
                                                     CraftingSimulationState parent)
        throws InterruptedException, CraftBranchFailure {
        var scope = new ObjectOpenHashSet<AEKey>();
        for (var node : nodes) scope.add(node.getWhat());

        var localInventory = new ChildCraftingSimulationState(parent);
        var roots = new Reference2ObjectOpenHashMap<CraftingGraphNode, CraftingTreeNode>();
        try (var context = enterLocalUnit(scope)) {
            for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
                var node = nodes.get(nodeIndex);
                long nodeDemand = requestedNode == null ? node.getDemandAmount()
                    : node == requestedNode ? requestedAmount : 0;
                if (nodeDemand <= 0 || node.getPattern() == null) continue;
                var root = new CraftingTreeNode(this.craftingService, this, node.getWhat(), 1, null, -1);
                roots.put(node, root);
                root.request(localInventory, nodeDemand, null);
            }
            var boundaryDemands = context.boundaryDemands();
            var fragmentBoundaries = keysOf(boundaryDemands);
            var entries = new ObjectArrayList<LocalComponentPlan.Entry>();
            var remainingCrafts = new Reference2LongOpenHashMap<IPatternDetails>();
            remainingCrafts.putAll(localInventory.getCrafts());
            var displayContext = new LocalDisplayFragment.CaptureContext(context.missingDelta());
            for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
                var node = nodes.get(nodeIndex);
                IPatternDetails selected = null;
                long times = 0;
                for (var candidate : node.getPatternCandidates()) {
                    long candidateTimes = remainingCrafts.getLong(candidate);
                    if (candidateTimes > 0) {
                        selected = candidate;
                        times = candidateTimes;
                        remainingCrafts.removeLong(candidate);
                        break;
                    }
                }
                var root = roots.get(node);
                if (selected == null && root == null) continue;
                long nodeDemand = requestedNode == null ? node.getDemandAmount()
                    : node == requestedNode ? requestedAmount : 0;
                var display = root == null
                    ? new LocalDisplayFragment(node.getWhat(), nodeDemand, 0, List.of())
                    : LocalDisplayFragment.capture(root, nodeDemand, fragmentBoundaries, displayContext);
                entries.add(new LocalComponentPlan.Entry(nodeIndex, selected, times, display));
            }
            var calculationDelta = context.calculationDelta();
            if (requestedNode == null && this.graph != null && !nodes.isEmpty()) {
                int componentId = this.graph.getTopology().getComponentId(nodes.getFirst());
                if (componentId >= 0) {
                    this.graph.setSccPlan(componentId, SccPlan.localPlan(
                        componentId, nodes, boundaryDemands, calculationDelta));
                }
            }
            context.rollback();
            this.attemptMetrics.recordLocalComponentPlan();
            return new LocalComponentPlan(entries, boundaryDemands, localInventory.freezeDiff(), calculationDelta);
        }
    }

    private static Set<AEKey> keysOf(KeyCounter counter) {
        var result = new ObjectOpenHashSet<AEKey>();
        for (var entry : counter) result.add(entry.getKey());
        return result;
    }

    @Nullable
    CraftingSupplierLocation resolveMachineLocation(IGrid grid, ICraftingProvider provider) {
        updateMachineLocationCacheGrid(grid);

        var location = this.machineLocationCache.get(provider);
        if (location != null || this.machineLocationCache.containsKey(provider)) {
            return location;
        }

        location = CraftingSupplierLocator.resolveLocation(grid, provider);
        this.machineLocationCache.put(provider, location);
        return location;
    }

    private void updateMachineLocationCacheGrid(@Nullable IGrid grid) {
        if (this.machineLocationCacheGrid != grid) {
            invalidateMachineLocationCaches();
            this.machineLocationCacheGrid = grid;
        }
    }

    private void invalidateMachineLocationCaches() {
        this.machineInfoCache.clear();
        this.machineLocationCache.clear();
    }

    List<InputTemplate> collectValidTemplates(Iterable<InputTemplate> templates) {
        var collected = new ObjectArrayList<InputTemplate>();
        for (var template : templates) {
            collected.add(template);
        }
        return collected;
    }

    public ICraftingPlan run() throws InterruptedException {
        long calculationStart = System.nanoTime();
        try {
            startPerformanceListener();
            TickHandler.instance().registerCraftingSimulation(this.level, this);
            this.handlePausing();

            ICraftingPlan plan;
            if (isPerformanceTrackingEnabled()) {
                plan = timed("compute-plan", this::computePlan);
            } else {
                plan = computePlan();
            }
            this.logCraftingJob(plan);
            if (this.usedCraftingTreeTestPattern) {
                long elapsedNanos = System.nanoTime() - calculationStart;
                AELog.info(
                    "Crafting tree test completed output=%s requested=%d time=%d ms (%d us) "
                        + "treeDepth=%d treeNodeCount=%d patternNodeCount=%d maxRequestDepth=%d",
                    this.output,
                    this.requestedAmount,
                    TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
                    TimeUnit.NANOSECONDS.toMicros(elapsedNanos),
                    this.getTreeDepth(),
                    this.getTreeNodeCount(),
                    this.getPatternNodeCount(),
                    this.getMaxRequestDepth());
            }
            return plan;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } catch (Exception ex) {
            AELog.info(ex, "Exception during crafting calculation.");
            throw new RuntimeException(ex);
        } finally {
            this.finish();
        }
    }

    private void startPerformanceListener() {
        if (this.performanceListener.isEnabled()) {
            this.performanceListener.start(this.output, this.requestedAmount);
        }
    }

    private ICraftingPlan computePlan() throws InterruptedException {
        if (!isPerformanceTrackingEnabled()) {
            return computePlanUnmeasured();
        }

        long calculationStart = System.nanoTime();
        try {
            return computePlanUnmeasured();
        } finally {
            finishPerformanceListener(System.nanoTime() - calculationStart);
        }
    }

    private ICraftingPlan computePlanUnmeasured() throws InterruptedException {
        if (strategy == CalculationStrategy.CRAFT_LESS) {
            this.memoReplayEnabled = false;
            var craftLessPlan = runCraftLessAttempt(requestedAmount);
            if (craftLessPlan != null) {
                return craftLessPlan;
            }

            return runCraftAttempt(requestedAmount, requestedAmount);
        }

        return runGraphBasedAttempt(requestedAmount);
    }

    private CraftingPlan runCraftAttempt(long productionAmount, long finalAmount)
        throws InterruptedException {
        var tree = getOrCreateLegacyTree();
        this.simulate = false;
        this.allowMissing = true;
        this.missing.clear();
        this.recursiveMissingSeeds.clear();
        for (var key : this.recursiveNetCache.keySet()) {
            this.recursiveNetKeyPool.release(key);
        }
        this.recursiveNetCache.clear();
        this.intermediateFinalOutputAmount = 0;
        this.recursiveMissingSeedSuppression = 0;
        this.realSeededRecursiveRequests.clear();
        this.realRecursiveSeeds.clear();
        this.realSeededRecursiveKeys.clear();
        this.recursiveFinalOutputInputs.clear();
        this.recursiveReserveCandidates.clear();
        this.recursiveDisplayRequests.clear();
        this.graph = null;
        tree.resetPossible();

        final boolean logCrafting = AELog.isCraftingLogEnabled();
        final Stopwatch timer = logCrafting ? Stopwatch.createStarted() : null;
        final boolean trackPerformance = isPerformanceTrackingEnabled();
        final String attemptName = trackPerformance
            ? "attempt amount=%d final=%d".formatted(productionAmount, finalAmount)
            : null;
        final long attemptStart = trackPerformance ? System.nanoTime() : 0;

        ChildCraftingSimulationState craftingInventory = new ChildCraftingSimulationState(networkInv);

        // Do the crafting. Throws in case of failure.
        try {
            if (trackPerformance) {
                runTimedCrafting("tree-request " + attemptName,
                    () -> tree.request(craftingInventory, productionAmount, null));
            } else {
                tree.request(craftingInventory, productionAmount, null);
            }
        } catch (CraftBranchFailure failure) {
            if (failure.hasExplicitMessageKey()) {
                throw new CraftingCalculationFailure(failure.getLocalizedMessageKey());
            }
            if (logCrafting) {
                this.attempts.add(new CraftAttempt(productionAmount + " failed", timer));
            }
            if (trackPerformance) {
                recordPerformanceStage(attemptName + " failed", System.nanoTime() - attemptStart);
            }
            return null;
        }
        applyRecursiveIngredientReserve(craftingInventory);
        clearResolvedRecursiveMissingItems(craftingInventory);
        addRecursiveMissingSeedsToPlan();
        craftingInventory.addBytes((double) tree.getNodeCount() * 8);
        CraftingPlan plan;
        if (trackPerformance) {
            plan = timed("build-plan " + attemptName,
                () -> CraftingSimulationState.buildCraftingPlan(craftingInventory, this, finalAmount));
        } else {
            plan = CraftingSimulationState.buildCraftingPlan(craftingInventory, this, finalAmount);
        }
        if (logCrafting) {
            this.attempts.add(new CraftAttempt("%d succeeded (%d bytes)".formatted(productionAmount, plan.bytes()),
                timer));
        }
        if (trackPerformance) {
            recordPerformanceStage(attemptName + " completed", System.nanoTime() - attemptStart);
        }
        return plan;
    }

    private CraftingPlan runGraphBasedAttempt(long productionAmount) throws InterruptedException {
        long graphCalculationStart = System.nanoTime();
        this.simulate = false;
        this.allowMissing = true;

        var service = this.craftingService instanceof CraftingService cs ? cs : null;
        long graphRevision = service == null ? Long.MIN_VALUE : service.getGraphCacheRevision();
        var graph = service == null ? null : service.takeCachedGraph(this.output);
        if (graph == null) {
            var graphBuilder = new GraphBuilder(this);
            graph = timed("buildGraph", () -> graphBuilder.buildGraph(this.output, productionAmount));
            if (service != null) {
                graph.setCacheRevision(graphRevision);
            }
        } else {
            // Structural reuse: clear all per-attempt planning state, then re-anchor the root demand.
            graph.resetForReuse();
            var cachedRoot = graph.getRootNode();
            if (cachedRoot == null) {
                throw new IllegalStateException("Cached graph has no root node");
            }
            cachedRoot.setDemandAmount(productionAmount);
            recordPerformanceCount("graphCacheHit", 1);
        }
        this.graph = graph;
        if (graph.requiresLegacyFallback()) {
            try {
                return runCraftAttempt(productionAmount, productionAmount);
            } finally {
                if (service != null) {
                    service.putCachedGraph(this.output, graph);
                }
            }
        }
        if (graph.getTopology().isCondensed()) {
            for (var component : graph.getTopology().getComponents()) {
                if (component.cyclic() && component.nodes().size() > 1) {
                    graph.requireLegacyFallback();
                    try {
                        return runCraftAttempt(productionAmount, productionAmount);
                    } finally {
                        if (service != null) {
                            service.putCachedGraph(this.output, graph);
                        }
                    }
                }
            }
        }
        try {
            return runGraphBasedAttemptBody(graph, productionAmount, graphCalculationStart);
        } finally {
            if (service != null) {
                service.putCachedGraph(this.output, graph);
            }
        }
    }

    private CraftingPlan runGraphBasedAttemptBody(CraftingGraph graph, long productionAmount,
                                                  long graphCalculationStart) throws InterruptedException {
        recordPerformanceCount("graphNodes", graph.getNodeCount());
        recordPerformanceCount("graphEdges", graph.getEdgeCount());
        long sccUnits = 0;
        var topology = graph.getTopology();
        if (topology.isCondensed()) {
            for (var component : topology.getComponents()) {
                if (component.cyclic()) {
                    sccUnits++;
                }
            }
        }
        recordPerformanceCount("sccUnits", sccUnits);

        var propagation = new DemandPropagation(this);
        var planningInventory = new ChildCraftingSimulationState(this.networkInv);
        timed("propagateDemand", () -> {
            propagation.propagate(graph, planningInventory);
            return null;
        });
        if (graph.requiresLegacyFallback()) {
            return runCraftAttempt(productionAmount, productionAmount);
        }
        boolean hasLocalComponents = false;
        if (topology.isCondensed()) {
            for (var component : topology.getComponents()) {
                if (!component.cyclic() || component.nodes().getFirst().getLocalComponentId() < 0) continue;
                try {
                    var plan = previewLocalComponent(component.nodes(), this.networkInv);
                    graph.setLocalComponentPlan(component.id(), plan);
                    hasLocalComponents = true;
                } catch (CraftBranchFailure e) {
                    throw new IllegalStateException("Failed to discover recursive crafting component "
                        + component.id(), e);
                }
            }
        }
        long localUnits = 0;
        boolean hasLocalUnits = false;
        for (var node : graph.getAllNodes()) {
            if (!node.isLocalUnit()) continue;
            localUnits++;
            if (node.getLocalComponentId() >= 0 || node.getDemandAmount() <= 0) continue;
            hasLocalUnits = true;
            try {
                var discovery = previewLocalUnit(node.getWhat(), node.getPatternCandidates(),
                    node.getDemandAmount(), Set.of(node.getWhat()), this.networkInv);
                node.setPlannedBoundaryDemands(discovery.boundaryDemands());
                // Reuse the discovery plan at execution time when the demand and inventory still match, avoiding a
                // second full legacy solve for the same unit.
                graph.setLocalUnitPlan(node, discovery);
            } catch (CraftBranchFailure e) {
                throw new IllegalStateException("Failed to discover local crafting unit " + node.getWhat(), e);
            }
        }
        recordPerformanceCount("localUnits", localUnits);
        recordPerformanceCount("nativeUnits", graph.getNodeCount() - localUnits);
        if (hasLocalUnits || hasLocalComponents) {
            for (var node : graph.getAllNodes()) {
                node.resetPlanningAmounts();
            }
            var root = graph.getRootNode();
            if (root == null) throw new IllegalStateException("Graph has no root node");
            root.setDemandAmount(productionAmount);
            timed("propagateLocalDemand", () -> {
                propagation.propagate(graph);
                return null;
            });
        }

        if (topology.isCondensed()) {
            for (var component : topology.getComponents()) {
                if (!component.cyclic()) continue;
                var sccPlan = graph.getSccPlan(component.id());
                if (sccPlan != null && sccPlan.local()) {
                    this.attemptMetrics.recordLocalScc();
                } else {
                    this.attemptMetrics.recordNativeScc();
                }
            }
        }

        var craftingInventory = new ChildCraftingSimulationState(this.networkInv);
        var executor = new GraphExecutor(this, graph);
        var displayBuilder = timed("applyGraph", () -> executor.applyGraph(craftingInventory));

        applyRecursiveIngredientReserve(craftingInventory);
        clearResolvedRecursiveMissingItems(craftingInventory);
        addRecursiveMissingSeedsToPlan();
        craftingInventory.addBytes((double) graph.getNodeCount() * 8);
        this.attemptMetrics.recordGraphCalculation(System.nanoTime() - graphCalculationStart);

        return CraftingSimulationState.buildCraftingPlan(
            craftingInventory, this, productionAmount, displayBuilder);
    }

    /**
     * Executes one already planned compatibility unit after its graph boundaries have been produced.
     */
    public void commitLocalPattern(LocalPatternPlan plan, CraftingSimulationState inventory) {
        plan.commit(this, inventory);
    }

    public void recordLocalReplan() {
        if (++this.localReplans > MAX_LOCAL_REPLANS) {
            throw new IllegalStateException("Local crafting replan limit exceeded: " + MAX_LOCAL_REPLANS);
        }
        this.attemptMetrics.recordLocalReplan();
        recordPerformanceCount("localReplans", 1);
    }

    public CraftingAttemptMetrics getAttemptMetrics() {
        return this.attemptMetrics;
    }

    @Nullable
    private CraftingPlan runCraftLessAttempt(long amount) throws InterruptedException {
        var tree = getOrCreateLegacyTree();
        this.simulate = false;
        this.allowMissing = false;
        this.missing.clear();
        this.recursiveMissingSeeds.clear();
        for (var key : this.recursiveNetCache.keySet()) {
            this.recursiveNetKeyPool.release(key);
        }
        this.recursiveNetCache.clear();
        this.intermediateFinalOutputAmount = 0;
        this.realSeededRecursiveRequests.clear();
        this.realRecursiveSeeds.clear();
        this.realSeededRecursiveKeys.clear();
        this.recursiveFinalOutputInputs.clear();
        this.recursiveReserveCandidates.clear();
        this.recursiveDisplayRequests.clear();
        this.graph = null;
        tree.resetPossible();

        final boolean logCrafting = AELog.isCraftingLogEnabled();
        final Stopwatch timer = logCrafting ? Stopwatch.createStarted() : null;
        final boolean trackPerformance = isPerformanceTrackingEnabled();
        final String attemptName = trackPerformance ? "craft-less amount=%d".formatted(amount) : null;
        final long attemptStart = trackPerformance ? System.nanoTime() : 0;

        ChildCraftingSimulationState craftingInventory = new ChildCraftingSimulationState(networkInv);

        long craftableAmount;
        if (trackPerformance) {
            craftableAmount = timed("craft-less-available " + attemptName,
                () -> tree.extractAvailableForCrafting(craftingInventory, amount));
        } else {
            craftableAmount = tree.extractAvailableForCrafting(craftingInventory, amount);
        }
        if (craftableAmount <= 0) {
            if (logCrafting) {
                this.attempts.add(new CraftAttempt(amount + " craft-less failed", timer));
            }
            if (trackPerformance) {
                recordPerformanceStage(attemptName + " failed", System.nanoTime() - attemptStart);
            }
            return null;
        }

        craftingInventory.addBytes((double) tree.getNodeCount() * 8);

        CraftingPlan plan;
        if (trackPerformance) {
            plan = timed("build-plan " + attemptName,
                () -> CraftingSimulationState.buildCraftingPlan(craftingInventory, this, craftableAmount));
        } else {
            plan = CraftingSimulationState.buildCraftingPlan(craftingInventory, this, craftableAmount);
        }
        if (logCrafting) {
            this.attempts.add(new CraftAttempt("%d craft-less (%d bytes)".formatted(craftableAmount, plan.bytes()),
                timer));
        }
        if (trackPerformance) {
            recordPerformanceStage(attemptName + " completed", System.nanoTime() - attemptStart);
        }
        return plan;
    }

    public void handlePausing() throws InterruptedException {
        if (this.incTime > AEConfig.CRAFTING.craftingCalculationPausingInterval) {
            this.incTime = 0;

            synchronized (this.monitor) {
                if (this.watch.elapsed(TimeUnit.MICROSECONDS) > this.time) {
                    this.running = false;
                    this.watch.stop();
                    this.monitor.notify();
                }

                if (!this.running) {
                    AELog.craftingDebug("crafting job will now sleep");

                    while (!this.running) {
                        this.monitor.wait();
                    }

                    AELog.craftingDebug("crafting job now active");
                }
            }

            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }
        this.incTime++;
    }

    private void finish() {
        synchronized (this.monitor) {
            this.running = false;
            this.done = true;
            this.monitor.notify();
        }
    }

    public boolean isSimulation() {
        return this.simulate;
    }

    boolean canUseMissingItems() {
        return (this.simulate || this.allowMissing) && this.missingSuppression == 0;
    }

    void pushMissingSuppression() {
        this.missingSuppression++;
    }

    void popMissingSuppression() {
        this.missingSuppression--;
    }

    boolean isRequesting(AEKey what) {
        for (int i = 0; i < this.requestStack.size(); i++) {
            if (this.requestStack.get(i).equals(what)) {
                return true;
            }
        }
        return false;
    }

    boolean isCheckingAvailability(AEKey what) {
        for (int i = 0; i < this.availabilityStack.size(); i++) {
            if (this.availabilityStack.get(i).equals(what)) {
                return true;
            }
        }
        return false;
    }

    void pushRequest(AEKey what) {
        this.requestStack.add(what);
        this.maxRequestDepth = Math.max(this.maxRequestDepth, this.requestStack.size());
    }

    void popRequest() {
        this.requestStack.removeLast();
    }

    @Nullable
    AEKey getCurrentRequestKey() {
        return this.requestStack.isEmpty() ? null : this.requestStack.getLast();
    }

    void pushAvailabilityCheck(AEKey what) {
        this.availabilityStack.add(what);
    }

    void popAvailabilityCheck() {
        this.availabilityStack.removeLast();
    }

    void pushProcess(CraftingTreeProcess process) {
        this.processStack.add(process);
        int previousHash = this.processHashPrefixes.getInt(this.processHashPrefixes.size() - 1);
        this.processHashPrefixes.add(31 * previousHash + System.identityHashCode(process));
        int previousPower = this.processHashPowers.getInt(this.processHashPowers.size() - 1);
        this.processHashPowers.add(31 * previousPower);
    }

    void popProcess() {
        this.processStack.removeLast();
        this.processHashPrefixes.removeInt(this.processHashPrefixes.size() - 1);
        this.processHashPowers.removeInt(this.processHashPowers.size() - 1);
    }

    public boolean cycleHasNetOutput(AEKey what) {
        return getCycleNetOutput(what) > 0;
    }

    long getCycleNetOutput(AEKey what) {
        int requestIndex = -1;
        for (int i = this.requestStack.size() - 1; i >= 0; i--) {
            if (this.requestStack.get(i).equals(what)) {
                requestIndex = i;
                break;
            }
        }
        if (requestIndex < 0) {
            return 0;
        }

        long netOutput = 0;
        for (int i = requestIndex; i < this.processStack.size(); i++) {
            var process = this.processStack.get(i);
            netOutput = LongMath.saturatedAdd(netOutput, process.getOutputCount(what));
            netOutput = LongMath.saturatedSubtract(netOutput, process.getInputCount(what));
        }
        return netOutput;
    }

    boolean resolveRecursiveRequest(AEKey what, CraftingSimulationState inv, long amount) {
        var resolution = getRecursiveResolution(what, inv);
        if (resolution == null) {
            return false;
        }

        if (resolution.seed() != null) {
            if (resolution.missingSeed() != null) {
                clearRecursiveMissingSeed(resolution.missingSeed().what());
            }
            clearRecursiveMissingSeed(resolution.seed().what());
            inv.extract(resolution.seed().what(), resolution.seed().amount(), Actionable.MODULATE);
            inv.insert(what, amount, Actionable.MODULATE);
            return true;
        }

        if (resolution.missingSeeds()) {
            this.recursiveMissingSeedSuppression++;
            try {
                inv.insert(what, amount, Actionable.MODULATE);
                return true;
            } finally {
                this.recursiveMissingSeedSuppression--;
            }
        }

        return false;
    }

    boolean canResolveRecursiveRequest(AEKey what, CraftingSimulationState inv, long locallyExtractedAmount) {
        return getRecursiveResolution(what, inv, locallyExtractedAmount) != null;
    }

    private RecursiveResolution getRecursiveResolution(AEKey what, CraftingSimulationState inv) {
        return getRecursiveResolution(what, inv, 0);
    }

    private RecursiveResolution getRecursiveResolution(AEKey what, CraftingSimulationState inv,
                                                       long locallyExtractedAmount) {
        var recursiveNet = getRecursiveNet(what);
        if (recursiveNet == null || !recursiveNet.canResolve()) {
            return null;
        }

        var seed = getRecursiveSeed(inv, recursiveNet, what, locallyExtractedAmount);
        if (seed != null) {
            this.realSeededRecursiveRequests.add(getRecursiveRootKey(recursiveNet.requestIndex()));
            this.realRecursiveSeeds.add(seed.what());
            addRecursiveReserveCandidates(recursiveNet);
            addRealSeededRecursiveKeys(recursiveNet.netByKey());
            var missingSeed = getMissingRecursiveSeed(recursiveNet, what);
            if (missingSeed != null && recursiveNet.netByKey().get(missingSeed.what()) >= 0) {
                clearRecursiveMissingSeed(missingSeed.what());
            }
            return new RecursiveResolution(seed, false, missingSeed);
        }

        AEKey recursiveRoot = getRecursiveRootKey(recursiveNet.requestIndex());
        if (canUseMissingItems() && this.recursiveMissingSeedSuppression == 0) {
            var missingSeed = getMissingRecursiveSeed(recursiveNet, what);
            if (missingSeed == null) {
                return null;
            }
            if (this.applyingRecursiveIngredientReserve && isReserveProtectedMissingSeed(missingSeed.what())) {
                this.recursiveReserveBlockedByMissingSeed = true;
                return null;
            }
            if (!this.realSeededRecursiveRequests.contains(recursiveRoot)) {
                addRecursiveMissingSeed(missingSeed.what(), missingSeed.amount());
            }
            addRecursiveReserveCandidates(recursiveNet);
            return new RecursiveResolution(null, true, missingSeed);
        }

        return null;
    }

    private AEKey getRecursiveRootKey(int requestIndex) {
        return this.requestStack.get(requestIndex);
    }

    private RecursiveNet getRecursiveNet(AEKey what) {
        int requestIndex = findRecursiveRequestIndex(what);
        if (requestIndex < 0) {
            return null;
        }

        int processCount = this.processStack.size() - requestIndex;
        int processHash = getProcessSuffixHash(requestIndex, processCount);
        this.recursiveNetLookup.reset(what, requestIndex, this.processStack, requestIndex, processCount, processHash);

        var cached = this.recursiveNetCache.get(this.recursiveNetLookup);
        if (cached != null) {
            return cached;
        }

        var frozenKey = this.recursiveNetLookup.freeze(this.recursiveNetKeyPool);
        var computed = computeRecursiveNet(frozenKey);
        this.recursiveNetCache.put(frozenKey, computed);
        return computed;
    }

    private int getProcessSuffixHash(int offset, int length) {
        int endHash = this.processHashPrefixes.getInt(offset + length);
        int startHash = this.processHashPrefixes.getInt(offset);
        return endHash - startHash * this.processHashPowers.getInt(length);
    }

    private int findRecursiveRequestIndex(AEKey what) {
        for (int i = this.requestStack.size() - 1; i >= 0; i--) {
            if (this.requestStack.get(i).equals(what)) {
                return i;
            }
        }
        return -1;
    }

    private RecursiveNet computeRecursiveNet(RecursiveNetKey key) {
        recordPerformanceCount("recursive-net-analysis", 1);
        var netByKey = new KeyCounter();
        var inputKeys = new ObjectOpenHashSet<AEKey>();
        var includedPatterns = new ObjectOpenHashSet<IPatternDetails>();
        for (int i = 0; i < key.processCount(); i++) {
            var process = key.processAt(i);
            process.accumulateNet(netByKey);
            process.accumulateInputKeys(inputKeys);
            includedPatterns.add(process.details);
        }

        expandRecursiveNetClosure(netByKey, inputKeys, includedPatterns);

        boolean hasPositiveNet = false;
        for (var entry : netByKey) {
            if (entry.getLongValue() > 0) {
                hasPositiveNet = true;
                break;
            }
        }

        return new RecursiveNet(key.requestIndex(), netByKey, inputKeys,
            hasPositiveNet && netByKey.get(key.what()) >= 0);
    }

    public long getExpandedPatternNetOutput(IPatternDetails pattern, AEKey what) {
        var outputsByKey = this.expandedPatternNetOutputCache.computeIfAbsent(pattern,
            ignored -> new AEKey2LongMap.OpenHashMap());
        return outputsByKey.computeIfAbsent(what, key -> computeExpandedPatternNetOutput(pattern, (AEKey) key));
    }

    private long computeExpandedPatternNetOutput(IPatternDetails pattern, AEKey what) {
        long directOutput = getPatternOutputCount(pattern, what);
        if (directOutput <= 0) {
            return 0;
        }

        var netByKey = new KeyCounter();
        var includedPatterns = new ObjectOpenHashSet<IPatternDetails>();
        includedPatterns.add(pattern);
        accumulatePatternNet(netByKey, pattern);
        expandRecursiveNetClosure(netByKey, null, includedPatterns);

        return netByKey.get(what);
    }

    /**
     * Whether the fully expanded recursive closure of the pattern has any positive net output for any key. A closure
     * without any positive net can never grow the simulated inventory by crafting, so the SCC can be served natively
     * from seed inventory without falling back to a legacy transaction.
     */
    public boolean hasPositiveRecursiveNet(IPatternDetails pattern) {
        return this.positiveRecursiveNetCache.computeIfAbsent(pattern,
            this::computeHasPositiveRecursiveNet);
    }

    private boolean computeHasPositiveRecursiveNet(IPatternDetails pattern) {
        var netByKey = new KeyCounter();
        var includedPatterns = new ObjectOpenHashSet<IPatternDetails>();
        includedPatterns.add(pattern);
        accumulatePatternNet(netByKey, pattern);
        expandRecursiveNetClosure(netByKey, null, includedPatterns);
        for (var entry : netByKey) {
            if (entry.getLongValue() > 0) {
                return true;
            }
        }
        return false;
    }

    public RecursivePatternBatch getRecursivePatternBatch(IPatternDetails pattern, AEKey what) {
        var batchesByKey = this.recursivePatternBatchCache.computeIfAbsent(pattern,
            ignored -> new Object2ObjectOpenHashMap<>());
        return batchesByKey.computeIfAbsent(what, key -> computeRecursivePatternBatch(pattern, key));
    }

    private RecursivePatternBatch computeRecursivePatternBatch(IPatternDetails pattern, AEKey what) {
        long directOutput = getPatternOutputCount(pattern, what);
        if (directOutput <= 0) {
            return new RecursivePatternBatch(1, 0);
        }

        // Only the single-root pass is needed for SCC eligibility: a cycle that only turns positive after multiple
        // root crafts can never be native (rootTimes must be 1), so scanning further is wasted work whose result is
        // ignored by the local fallback anyway.
        boolean rootConsumesTarget = getPatternInputCount(pattern, what) > 0;
        var netByKey = new KeyCounter();
        var recursiveUse = new RecursiveUse(rootConsumesTarget);
        accumulatePatternNet(netByKey, pattern, 1);
        if (!expandRecursiveBatchNet(netByKey, what, recursiveUse)) {
            return new RecursivePatternBatch(1, directOutput);
        }

        if (!recursiveUse.get()) {
            return new RecursivePatternBatch(1, directOutput);
        }

        long netOutput = netByKey.get(what);
        return new RecursivePatternBatch(1, netOutput > 0 ? netOutput : directOutput);
    }

    /**
     * Checks whether a recursive seed is available. Positive-net recursive closures do not net-consume their seed, so
     * the immutable network snapshot remains authoritative even when the current local transaction has already counted
     * the seed in {@code requiredExtract}.
     */
    static boolean hasRecursiveSeedAvailability(CraftingSimulationState networkInventory,
                                                CraftingSimulationState localInventory, AEKey seed,
                                                long requiredAmount, long locallyExtractedAmount,
                                                boolean seedIsRequested) {
        long localAvailable = localInventory.getAvailableNonProducedAmount(seed);
        if (seedIsRequested) {
            localAvailable = LongMath.saturatedAdd(localAvailable, locallyExtractedAmount);
        }
        long networkAvailable = networkInventory.getOriginalAmount(seed);
        return Math.max(networkAvailable, localAvailable) >= requiredAmount;
    }

    private RecursiveSeed getRecursiveSeed(CraftingSimulationState inv, RecursiveNet recursiveNet, AEKey what,
                                           long locallyExtractedAmount) {
        for (AEKey seed : recursiveNet.inputKeys()) {
            if (recursiveNet.netByKey().get(seed) < 0 || isReserveProtectedMissingSeed(seed)) {
                continue;
            }
            long amount = getRecursiveSeedAmount(seed, recursiveNet.requestIndex());
            if (hasRecursiveSeedAvailability(this.networkInv, inv, seed, amount, locallyExtractedAmount,
                seed.equals(what))) {
                return new RecursiveSeed(seed, amount);
            }
        }
        return null;
    }

    private long getRecursiveSeedAmount(AEKey seed, int requestIndex) {
        if (requestIndex >= 0 && requestIndex < this.processStack.size()) {
            for (int i = requestIndex; i < this.processStack.size(); i++) {
                var process = this.processStack.get(i);
                long amount = process.getInputCount(seed);
                if (amount > 0) {
                    return amount;
                }
            }
        }
        return 1;
    }

    private RecursiveSeed getMissingRecursiveSeed(RecursiveNet recursiveNet, AEKey what) {
        int requestIndex = recursiveNet.requestIndex();
        if (requestIndex >= 0 && requestIndex < this.processStack.size()) {
            for (AEKey seed : recursiveNet.inputKeys()) {
                if (seed.equals(what) || recursiveNet.netByKey().get(seed) < 0 || isReserveProtectedMissingSeed(seed)) {
                    continue;
                }
                return new RecursiveSeed(seed, getRecursiveSeedAmount(seed, requestIndex));
            }
        }
        return null;
    }

    private void addRecursiveMissingSeed(AEKey what, long amount) {
        if (hasRealSeededRecursiveRequestFor(what)) {
            clearRecursiveMissingSeed(what);
            return;
        }
        if (this.realRecursiveSeeds.contains(what)) {
            clearRecursiveMissingSeed(what);
            return;
        }
        long existing = this.recursiveMissingSeeds.get(what);
        if (existing >= amount) {
            return;
        }

        long delta = amount - existing;
        this.recursiveMissingSeeds.add(what, delta);
    }

    /**
     * Records a seed cut resolved by the graph executor in the shared recursive bookkeeping.
     */
    public void recordGraphRecursiveSeed(AEKey recursiveRoot, AEKey what, long required, long allocated,
                                         long missing, long reserve) {
        if (missing > 0) {
            addRecursiveMissingSeed(what, missing);
        } else if (required > 0 && allocated >= required) {
            clearRecursiveMissingSeed(what);
            this.realSeededRecursiveRequests.add(recursiveRoot);
            this.realRecursiveSeeds.add(what);
            this.realSeededRecursiveKeys.add(what);
        }
        if (reserve > 0) {
            long existing = this.recursiveReserveCandidates.get(what);
            if (existing < reserve) {
                this.recursiveReserveCandidates.add(what, reserve - existing);
            }
        }
    }

    private void clearRecursiveMissingSeed(AEKey what) {
        this.recursiveMissingSeeds.remove(what);
        this.missing.remove(what);
    }

    private void applyRecursiveIngredientReserve(CraftingSimulationState inv) throws InterruptedException {
        if (this.recursiveIngredientReserveAmount <= 0 || this.recursiveReserveCandidates.isEmpty()) {
            return;
        }

        var protectedMissingSeeds = getRecursiveMissingSeedsMarker();
        this.reserveProtectedMissingSeeds = protectedMissingSeeds;
        this.applyingRecursiveIngredientReserve = true;
        try {
            var reserveCandidates = new ObjectArrayList<AEKey>();
            for (var entry : this.recursiveReserveCandidates) {
                reserveCandidates.add(entry.getKey());
            }
            for (int i = 0; i < reserveCandidates.size(); i++) {
                var what = reserveCandidates.get(i);
                if (protectedMissingSeeds.get(what) > 0) {
                    continue;
                }

                long reservePerBatch = this.recursiveReserveCandidates.get(what);
                if (reservePerBatch <= 0) {
                    continue;
                }

                long targetReserve = Math.min(
                    LongMath.saturatedMultiply(this.recursiveIngredientReserveAmount, reservePerBatch),
                    inv.getOriginalAmount(what));
                if (targetReserve <= 0) {
                    continue;
                }

                long available = inv.getAvailableAmount(what);
                if (available >= targetReserve) {
                    continue;
                }

                long deficit = targetReserve - available;
                if (getCraftingFor(what).isEmpty()) {
                    long returned = inv.returnExtractedForReserve(what, deficit);
                    if (returned > 0) {
                        this.missing.add(what, returned);
                    }
                    continue;
                }

                var branchInv = new ChildCraftingSimulationState(inv);
                var branchMarker = createRecursiveReserveBranchMarker();
                this.recursiveReserveBlockedByMissingSeed = false;
                try {
                    reserveLocalBranch(what, deficit, branchInv);
                    if (this.recursiveReserveBlockedByMissingSeed) {
                        restoreRecursiveReserveBranchMarker(branchMarker);
                        continue;
                    }
                    branchInv.applyDiff(inv);
                    addRecursiveReserveDisplayRequest(what, deficit);
                } catch (CraftBranchFailure failure) {
                    if (this.recursiveReserveBlockedByMissingSeed) {
                        restoreRecursiveReserveBranchMarker(branchMarker);
                        continue;
                    }
                    if (failure.hasExplicitMessageKey()) {
                        throw new CraftingCalculationFailure(failure.getLocalizedMessageKey());
                    }
                    restoreRecursiveReserveBranchMarker(branchMarker);
                    this.missing.add(what, deficit);
                } finally {
                    this.recursiveReserveBlockedByMissingSeed = false;
                }
            }
        } finally {
            restoreProtectedRecursiveMissingSeeds(protectedMissingSeeds);
            this.applyingRecursiveIngredientReserve = false;
            this.reserveProtectedMissingSeeds = null;
            this.recursiveReserveBlockedByMissingSeed = false;
        }
    }

    private void reserveLocalBranch(AEKey what, long deficit, ChildCraftingSimulationState branchInv)
        throws InterruptedException, CraftBranchFailure {
        var graph = this.graph;
        var node = graph == null ? null : graph.getNodeFor(what);
        if (node != null && node.getLocalComponentId() >= 0) {
            var component = graph.getTopology().getComponents().get(node.getLocalComponentId());
            recordLocalReplan();
            var componentPlan = previewLocalComponent(component.nodes(), node, deficit, branchInv);
            commitLocalPlanWithBoundaries(componentPlan, branchInv);
            return;
        }
        if (node != null && node.isLocalUnit()) {
            var unitPlan = previewLocalUnit(what, node.getPatternCandidates(), deficit,
                Set.of(what), branchInv);
            commitLocalPlanWithBoundaries(unitPlan, branchInv);
            return;
        }

        var patterns = getCraftingFor(what);
        var unitPlan = previewLocalUnit(what, patterns, deficit, Set.of(what), branchInv);
        commitLocalPlanWithBoundaries(unitPlan, branchInv);
    }

    private RecursiveReserveBranchMarker createRecursiveReserveBranchMarker() {
        return new RecursiveReserveBranchMarker(createCalculationMarker());
    }

    private void restoreRecursiveReserveBranchMarker(RecursiveReserveBranchMarker marker) {
        restoreCalculationMarker(marker.calculationMarker());
    }

    /**
     * Captures calculation-global state which is not part of a {@link ChildCraftingSimulationState} diff.
     */
    public CalculationMarker createCalculationMarker() {
        var recursiveFinalOutputInputs = new ObjectOpenHashSet<>(this.recursiveFinalOutputInputs);
        var recursiveReserveCandidates = new KeyCounter();
        recursiveReserveCandidates.addAll(this.recursiveReserveCandidates);
        KeyCounter reserveProtectedMissingSeeds = null;
        if (this.reserveProtectedMissingSeeds != null) {
            reserveProtectedMissingSeeds = new KeyCounter();
            reserveProtectedMissingSeeds.addAll(this.reserveProtectedMissingSeeds);
        }
        return new CalculationMarker(
            getMissingItemsMarker(),
            getRecursiveMissingSeedsMarker(),
            getRealSeededRecursiveRequestsMarker(),
            getRealRecursiveSeedsMarker(),
            getRealSeededRecursiveKeysMarker(),
            recursiveFinalOutputInputs,
            recursiveReserveCandidates,
            getRecursiveDisplayRequestsMarker(),
            getIntermediateFinalOutputMarker(),
            reserveProtectedMissingSeeds,
            this.missingSuppression,
            this.recursiveMissingSeedSuppression,
            this.applyingRecursiveIngredientReserve,
            this.recursiveReserveBlockedByMissingSeed);
    }

    public void restoreCalculationMarker(CalculationMarker marker) {
        restoreMissingItemsMarker(marker.missingItems());
        restoreRecursiveMissingSeedsMarker(marker.recursiveMissingSeeds());
        restoreRealSeededRecursiveRequestsMarker(marker.realSeededRecursiveRequests());
        restoreRealRecursiveSeedsMarker(marker.realRecursiveSeeds());
        restoreRealSeededRecursiveKeysMarker(marker.realSeededRecursiveKeys());
        this.recursiveFinalOutputInputs.clear();
        this.recursiveFinalOutputInputs.addAll(marker.recursiveFinalOutputInputs());
        this.recursiveReserveCandidates.clear();
        this.recursiveReserveCandidates.addAll(marker.recursiveReserveCandidates());
        restoreRecursiveDisplayRequestsMarker(marker.recursiveDisplayRequests());
        restoreIntermediateFinalOutputMarker(marker.intermediateFinalOutputAmount());
        if (marker.reserveProtectedMissingSeeds() == null) {
            this.reserveProtectedMissingSeeds = null;
        } else {
            this.reserveProtectedMissingSeeds = new KeyCounter();
            this.reserveProtectedMissingSeeds.addAll(marker.reserveProtectedMissingSeeds());
        }
        this.missingSuppression = marker.missingSuppression();
        this.recursiveMissingSeedSuppression = marker.recursiveMissingSeedSuppression();
        this.applyingRecursiveIngredientReserve = marker.applyingRecursiveIngredientReserve();
        this.recursiveReserveBlockedByMissingSeed = marker.recursiveReserveBlockedByMissingSeed();
    }

    public CalculationDelta createCalculationDelta(CalculationMarker before) {
        var displayRequests = new Reference2LongOpenHashMap<CraftingTreeNode>();
        for (var entry : this.recursiveDisplayRequests.reference2LongEntrySet()) {
            long delta = entry.getLongValue() - before.recursiveDisplayRequests().getLong(entry.getKey());
            if (delta > 0) {
                displayRequests.put(entry.getKey(), delta);
            }
        }
        return new CalculationDelta(
            positiveCounterDelta(before.missingItems(), this.missing),
            increasedCounterTargets(before.recursiveMissingSeeds(), this.recursiveMissingSeeds),
            setDelta(before.realSeededRecursiveRequests(), this.realSeededRecursiveRequests),
            setDelta(before.realRecursiveSeeds(), this.realRecursiveSeeds),
            setDelta(before.realSeededRecursiveKeys(), this.realSeededRecursiveKeys),
            setDelta(before.recursiveFinalOutputInputs(), this.recursiveFinalOutputInputs),
            increasedCounterTargets(before.recursiveReserveCandidates(), this.recursiveReserveCandidates),
            displayRequests,
            Math.max(0, this.intermediateFinalOutputAmount - before.intermediateFinalOutputAmount()));
    }

    public void mergeCalculationDelta(CalculationDelta delta) {
        this.missing.addAll(delta.missingItems());
        mergeCounterMaximum(this.recursiveMissingSeeds, delta.recursiveMissingSeeds());
        this.realSeededRecursiveRequests.addAll(delta.realSeededRecursiveRequests());
        this.realRecursiveSeeds.addAll(delta.realRecursiveSeeds());
        this.realSeededRecursiveKeys.addAll(delta.realSeededRecursiveKeys());
        this.recursiveFinalOutputInputs.addAll(delta.recursiveFinalOutputInputs());
        mergeCounterMaximum(this.recursiveReserveCandidates, delta.recursiveReserveCandidates());
        for (var entry : delta.recursiveDisplayRequests().reference2LongEntrySet()) {
            addRecursiveDisplayRequest(entry.getKey(), entry.getLongValue());
        }
        addIntermediateFinalOutput(delta.intermediateFinalOutputAmount());
    }

    private static KeyCounter positiveCounterDelta(KeyCounter before, KeyCounter after) {
        var result = new KeyCounter();
        for (var entry : after) {
            long delta = entry.getLongValue() - before.get(entry.getKey());
            if (delta > 0) {
                result.add(entry.getKey(), delta);
            }
        }
        return result;
    }

    private static KeyCounter increasedCounterTargets(KeyCounter before, KeyCounter after) {
        var result = new KeyCounter();
        for (var entry : after) {
            if (entry.getLongValue() > before.get(entry.getKey())) {
                result.add(entry.getKey(), entry.getLongValue());
            }
        }
        return result;
    }

    private static void mergeCounterMaximum(KeyCounter target, KeyCounter additions) {
        for (var entry : additions) {
            long current = target.get(entry.getKey());
            if (entry.getLongValue() > current) {
                target.add(entry.getKey(), entry.getLongValue() - current);
            }
        }
    }

    private static <T> Set<T> setDelta(Set<T> before, Set<T> after) {
        Set<T> result = new ObjectOpenHashSet<>();
        result.addAll(after);
        result.removeAll(before);
        return result;
    }

    /**
     * Starts a legacy compatibility unit. Requests outside {@code scope} are exposed as graph boundary demands instead
     * of recursively expanding another legacy subtree.
     */
    public LocalUnitContext enterLocalUnit(Set<AEKey> scope) {
        if (this.localUnitMode) {
            throw new IllegalStateException("Nested local crafting units are not supported");
        }
        boolean memoReplayEnabled = this.memoReplayEnabled;
        this.localUnitMode = true;
        this.localUnitScope = Set.copyOf(scope);
        this.localBoundaryDemands = new KeyCounter();
        this.memoReplayEnabled = false;
        return new LocalUnitContext(this, createCalculationMarker(), memoReplayEnabled);
    }

    boolean interceptLocalBoundaryRequest(AEKey what, long amount, CraftingSimulationState inventory) {
        var boundaryDemands = this.localBoundaryDemands;
        if (!this.localUnitMode || this.localUnitScope == null || boundaryDemands == null
            || this.localUnitScope.contains(what)) {
            return false;
        }
        if (amount > 0) {
            boundaryDemands.add(what, amount);
            inventory.insertBoundary(what, amount);
        }
        return true;
    }

    boolean interceptLocalBoundaryAvailability(AEKey what) {
        var boundaryDemands = this.localBoundaryDemands;
        return this.localUnitMode && this.localUnitScope != null && boundaryDemands != null
            && !this.localUnitScope.contains(what);
    }

    boolean isLocalUnitMode() {
        return this.localUnitMode;
    }

    private void commitLocalPlanWithBoundaries(LocalPatternPlan plan,
                                               ChildCraftingSimulationState parent)
        throws CraftBranchFailure {
        var marker = createCalculationMarker();
        var transaction = new ChildCraftingSimulationState(parent);
        try {
            extractLocalBoundaries(plan.boundaryDemands(), transaction);
            plan.commit(this, transaction);
            transaction.applyDiff(parent);
        } catch (RuntimeException | Error failure) {
            restoreCalculationMarker(marker);
            throw failure;
        }
    }

    private void commitLocalPlanWithBoundaries(LocalComponentPlan plan,
                                               ChildCraftingSimulationState parent)
        throws CraftBranchFailure {
        var marker = createCalculationMarker();
        var transaction = new ChildCraftingSimulationState(parent);
        try {
            extractLocalBoundaries(plan.boundaryDemands(), transaction);
            plan.commit(this, transaction);
            transaction.applyDiff(parent);
        } catch (RuntimeException | Error failure) {
            restoreCalculationMarker(marker);
            throw failure;
        }
    }

    private static void extractLocalBoundaries(KeyCounter boundaries,
                                               ChildCraftingSimulationState transaction)
        throws CraftBranchFailure {
        for (var boundary : boundaries) {
            long extracted = transaction.extract(boundary.getKey(), boundary.getLongValue(), Actionable.MODULATE);
            if (extracted < boundary.getLongValue()) {
                throw new CraftBranchFailure(boundary.getKey(), boundary.getLongValue() - extracted);
            }
            transaction.addStackBytes(boundary.getKey(), extracted, 1);
        }
    }

    private KeyCounter exitLocalUnit() {
        if (!this.localUnitMode || this.localBoundaryDemands == null) {
            throw new IllegalStateException("No local crafting unit is active");
        }
        var result = new KeyCounter();
        result.addAll(this.localBoundaryDemands);
        this.localUnitMode = false;
        this.localUnitScope = null;
        this.localBoundaryDemands = null;
        return result;
    }

    private void restoreLocalUnitDefaults(boolean memoReplayEnabled) {
        this.memoReplayEnabled = memoReplayEnabled;
    }

    public static final class LocalUnitContext implements AutoCloseable {
        private final CraftingCalculation calculation;
        private final CalculationMarker marker;
        private final boolean memoReplayEnabled;
        private boolean closed;

        private LocalUnitContext(CraftingCalculation calculation, CalculationMarker marker,
                                 boolean memoReplayEnabled) {
            this.calculation = calculation;
            this.marker = marker;
            this.memoReplayEnabled = memoReplayEnabled;
        }

        public KeyCounter finish() {
            if (this.closed) {
                throw new IllegalStateException("Local crafting unit is already closed");
            }
            this.closed = true;
            var demands = this.calculation.exitLocalUnit();
            this.calculation.restoreLocalUnitDefaults(this.memoReplayEnabled);
            return demands;
        }

        public KeyCounter boundaryDemands() {
            if (this.closed || this.calculation.localBoundaryDemands == null) {
                throw new IllegalStateException("Local crafting unit is already closed");
            }
            var result = new KeyCounter();
            result.addAll(this.calculation.localBoundaryDemands);
            return result;
        }

        public KeyCounter missingDelta() {
            if (this.closed) throw new IllegalStateException("Local crafting unit is already closed");
            var result = new KeyCounter();
            for (var entry : this.calculation.missing) {
                long delta = entry.getLongValue() - this.marker.missingItems().get(entry.getKey());
                if (delta > 0) result.add(entry.getKey(), delta);
            }
            return result;
        }

        public CalculationDelta calculationDelta() {
            if (this.closed) throw new IllegalStateException("Local crafting unit is already closed");
            return this.calculation.createCalculationDelta(this.marker);
        }

        public void rollback() {
            if (this.closed) {
                throw new IllegalStateException("Local crafting unit is already closed");
            }
            this.closed = true;
            this.calculation.exitLocalUnit();
            this.calculation.restoreCalculationMarker(this.marker);
            this.calculation.restoreLocalUnitDefaults(this.memoReplayEnabled);
        }

        @Override
        public void close() {
            if (!this.closed) {
                rollback();
            }
        }
    }

    private void restoreProtectedRecursiveMissingSeeds(KeyCounter protectedSeeds) {
        for (var entry : protectedSeeds) {
            long current = this.recursiveMissingSeeds.get(entry.getKey());
            if (current < entry.getLongValue()) {
                this.recursiveMissingSeeds.add(entry.getKey(), entry.getLongValue() - current);
            }
        }
    }

    private void addRecursiveReserveDisplayRequest(AEKey what, long amount) {
        var tree = this.tree;
        if (tree == null) {
            return;
        }
        var displayNode = tree.findDisplayNodeFor(what);
        if (displayNode != null) {
            addRecursiveDisplayRequest(displayNode, amount);
        }
    }

    private void addRecursiveMissingSeedsToPlan() {
        for (var entry : this.recursiveMissingSeeds) {
            if (hasRealSeededRecursiveRequestFor(entry.getKey()) || this.realRecursiveSeeds.contains(entry.getKey())
                || this.realSeededRecursiveKeys.contains(entry.getKey())
                || this.missing.get(entry.getKey()) >= entry.getLongValue()) {
                continue;
            }
            this.missing.add(entry.getKey(), entry.getLongValue());
        }
    }

    private void clearResolvedRecursiveMissingItems(CraftingSimulationState inv) {
        var keys = new ObjectArrayList<AEKey>();
        for (var entry : this.missing) {
            var key = entry.getKey();
            if (this.realSeededRecursiveKeys.contains(key) || this.realRecursiveSeeds.contains(key)
                || hasRealSeededRecursiveRequestFor(key)
                || (this.recursiveMissingSeeds.get(key) <= 0
                && inv.getCraftedAmount(key) >= entry.getLongValue())) {
                keys.add(key);
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            this.missing.remove(keys.get(i));
        }
    }

    private boolean hasRealSeededRecursiveRequestFor(AEKey seed) {
        if (isReserveProtectedMissingSeed(seed)) {
            return false;
        }
        for (AEKey recursiveRoot : this.realSeededRecursiveRequests) {
            var recursiveNet = getRecursiveNet(recursiveRoot);
            if (recursiveNet != null && recursiveNet.netByKey().get(seed) >= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isReserveProtectedMissingSeed(AEKey what) {
        return this.reserveProtectedMissingSeeds != null && this.reserveProtectedMissingSeeds.get(what) > 0;
    }

    private void addRealSeededRecursiveKeys(KeyCounter netByKey) {
        for (var entry : netByKey) {
            if (entry.getLongValue() >= 0) {
                var key = entry.getKey();
                if (isReserveProtectedMissingSeed(key)) {
                    continue;
                }
                this.realSeededRecursiveKeys.add(key);
                clearRecursiveMissingSeed(key);
            }
        }
    }

    private void addRecursiveReserveCandidates(RecursiveNet recursiveNet) {
        for (var entry : recursiveNet.netByKey()) {
            AEKey key = entry.getKey();
            if (entry.getLongValue() < 0 || isReserveProtectedMissingSeed(key)) {
                continue;
            }

            long reservePerBatch = entry.getLongValue();
            if (reservePerBatch <= 0 && recursiveNet.inputKeys().contains(key)) {
                reservePerBatch = getRecursiveSeedAmount(key, recursiveNet.requestIndex());
            }
            if (reservePerBatch <= 0) {
                continue;
            }

            long existing = this.recursiveReserveCandidates.get(key);
            if (existing < reservePerBatch) {
                this.recursiveReserveCandidates.add(key, reservePerBatch - existing);
            }
        }
    }

    private void expandRecursiveNetClosure(KeyCounter netByKey, @Nullable Collection<AEKey> inputKeys,
                                           Set<IPatternDetails> includedPatterns) {
        // Collect all currently negative keys once, then expand each until it turns positive or has no new pattern.
        // Newly negative keys introduced by an expansion are appended to the queue, so the whole net table is only
        // scanned once instead of on every closure round.
        var pending = new ObjectArrayList<AEKey>();
        var queued = new ObjectOpenHashSet<AEKey>();
        for (var entry : netByKey) {
            if (entry.getLongValue() < 0 && queued.add(entry.getKey())) {
                pending.add(entry.getKey());
            }
        }

        for (int i = 0; i < pending.size(); i++) {
            var key = pending.get(i);
            while (netByKey.get(key) < 0) {
                var patterns = this.getCraftingFor(key);
                IPatternDetails selected = null;
                for (int j = 0, size = patterns.size(); j < size; j++) {
                    var pattern = patterns.get(j);
                    if (includedPatterns.add(pattern)) {
                        selected = pattern;
                        break;
                    }
                }
                if (selected == null) {
                    break;
                }
                accumulatePatternNet(netByKey, inputKeys, selected, 1);
                for (var entry : netByKey) {
                    if (entry.getLongValue() < 0 && queued.add(entry.getKey())) {
                        pending.add(entry.getKey());
                    }
                }
            }
        }
    }

    private boolean expandRecursiveBatchNet(KeyCounter netByKey, AEKey target, RecursiveUse recursiveUse) {
        for (int guard = 0; guard < 128; guard++) {
            AEKey missingKey = null;
            long missingAmount = 0;
            for (var entry : netByKey) {
                if (entry.getLongValue() < 0) {
                    missingKey = entry.getKey();
                    missingAmount = LongMath.saturatedSubtract(0, entry.getLongValue());
                    break;
                }
            }

            if (missingKey == null) {
                return true;
            }

            IPatternDetails selectedPattern = null;
            long outputAmount = 0;
            var candidates = this.getCraftingFor(missingKey);
            for (int i = 0, size = candidates.size(); i < size; i++) {
                var candidate = candidates.get(i);
                outputAmount = getPatternOutputCount(candidate, missingKey);
                if (outputAmount > 0) {
                    selectedPattern = candidate;
                    break;
                }
            }

            if (selectedPattern == null) {
                return recursiveUse.get() && netByKey.get(target) > 0;
            }

            if (getPatternInputCount(selectedPattern, target) > 0) {
                recursiveUse.set();
            }
            long times = divideCeil(missingAmount, outputAmount);
            accumulatePatternNet(netByKey, selectedPattern, times);
        }

        return false;
    }

    public AEKey getOutput() {
        return output;
    }

    public KeyCounter getMissingItems() {
        return missing;
    }

    public @Nullable CraftingTreeNode getTree() {
        return tree;
    }

    public List<ICraftingProvider> getTemporaryProviders() {
        return temporaryProviders;
    }

    public long getIntermediateFinalOutputAmount() {
        return intermediateFinalOutputAmount;
    }

    void addIntermediateFinalOutput(long amount) {
        this.intermediateFinalOutputAmount = LongMath.saturatedAdd(this.intermediateFinalOutputAmount, amount);
    }

    void addRecursiveIntermediateFinalOutput(long amount) {
        this.intermediateFinalOutputAmount = LongMath.saturatedAdd(this.intermediateFinalOutputAmount, amount);
    }

    void addRecursiveFinalOutputInput(AEKey what) {
        this.recursiveFinalOutputInputs.add(what);
    }

    void addRecursiveDisplayRequest(CraftingTreeNode node, long amount) {
        long oldValue = this.recursiveDisplayRequests.addTo(node, amount);
        long newValue = oldValue + amount;
        if (newValue < oldValue) {
            this.recursiveDisplayRequests.put(node, Long.MAX_VALUE);
        }
    }

    long getRecursiveDisplayRequest(CraftingTreeNode node) {
        return this.recursiveDisplayRequests.getOrDefault(node, 0L);
    }

    Reference2LongMap<CraftingTreeNode> getRecursiveDisplayRequestsMarker() {
        return new Reference2LongOpenHashMap<>(this.recursiveDisplayRequests);
    }

    void restoreRecursiveDisplayRequestsMarker(Reference2LongMap<CraftingTreeNode> marker) {
        this.recursiveDisplayRequests.clear();
        this.recursiveDisplayRequests.putAll(marker);
    }

    Reference2LongMap<CraftingTreeNode> getRecursiveDisplayRequestsDelta(
        Reference2LongMap<CraftingTreeNode> marker) {
        var delta = new Reference2LongOpenHashMap<CraftingTreeNode>();
        for (var entry : this.recursiveDisplayRequests.reference2LongEntrySet()) {
            long valueDelta = entry.getLongValue() - marker.getOrDefault(entry.getKey(), 0L);
            if (valueDelta > 0) {
                delta.put(entry.getKey(), valueDelta);
            }
        }
        return delta;
    }

    void addRecursiveDisplayRequests(Reference2LongMap<CraftingTreeNode> delta) {
        for (var entry : delta.reference2LongEntrySet()) {
            addRecursiveDisplayRequest(entry.getKey(), entry.getLongValue());
        }
    }

    boolean isRecursiveFinalOutputInput(AEKey what) {
        return this.recursiveFinalOutputInputs.contains(what);
    }

    void addIntermediateFinalOutputInput(AEKey what, long amount) {
        if (what.equals(this.output)) {
            addIntermediateFinalOutput(amount);
        }
    }

    long getIntermediateFinalOutputMarker() {
        return this.intermediateFinalOutputAmount;
    }

    void restoreIntermediateFinalOutputMarker(long marker) {
        this.intermediateFinalOutputAmount = marker;
    }

    KeyCounter getRecursiveMissingSeedsMarker() {
        var marker = new KeyCounter();
        marker.addAll(this.recursiveMissingSeeds);
        return marker;
    }

    void restoreRecursiveMissingSeedsMarker(KeyCounter marker) {
        this.recursiveMissingSeeds.clear();
        this.recursiveMissingSeeds.addAll(marker);
    }

    Set<AEKey> getRealSeededRecursiveRequestsMarker() {
        return new ObjectOpenHashSet<>(this.realSeededRecursiveRequests);
    }

    void restoreRealSeededRecursiveRequestsMarker(Set<AEKey> marker) {
        this.realSeededRecursiveRequests.clear();
        this.realSeededRecursiveRequests.addAll(marker);
    }

    void addRealSeededRecursiveRequests(Collection<AEKey> requests) {
        this.realSeededRecursiveRequests.addAll(requests);
    }

    Set<AEKey> getRealRecursiveSeedsMarker() {
        return new ObjectOpenHashSet<>(this.realRecursiveSeeds);
    }

    void restoreRealRecursiveSeedsMarker(Set<AEKey> marker) {
        this.realRecursiveSeeds.clear();
        this.realRecursiveSeeds.addAll(marker);
    }

    void addRealRecursiveSeeds(Collection<AEKey> seeds) {
        for (AEKey seed : seeds) {
            if (!isReserveProtectedMissingSeed(seed)) {
                this.realRecursiveSeeds.add(seed);
            }
        }
    }

    Set<AEKey> getRealSeededRecursiveKeysMarker() {
        return new ObjectOpenHashSet<>(this.realSeededRecursiveKeys);
    }

    void restoreRealSeededRecursiveKeysMarker(Set<AEKey> marker) {
        this.realSeededRecursiveKeys.clear();
        this.realSeededRecursiveKeys.addAll(marker);
    }

    void addRealSeededRecursiveKeys(Collection<AEKey> keys) {
        for (AEKey key : keys) {
            if (!isReserveProtectedMissingSeed(key)) {
                this.realSeededRecursiveKeys.add(key);
            }
        }
    }

    void applyRecursiveMissingSeedPreview(KeyCounter clearedSeeds, KeyCounter addedSeeds) {
        for (var entry : clearedSeeds) {
            if (!isReserveProtectedMissingSeed(entry.getKey())) {
                clearRecursiveMissingSeed(entry.getKey());
            }
        }
        this.recursiveMissingSeeds.addAll(addedSeeds);
    }

    KeyCounter getMissingItemsMarker() {
        var marker = new KeyCounter();
        marker.addAll(this.missing);
        return marker;
    }

    void restoreMissingItemsMarker(KeyCounter marker) {
        this.missing.clear();
        this.missing.addAll(marker);
    }

    World getLevel() {
        return this.level;
    }

    /**
     * returns true if this needs more simulation.
     *
     * @param micros microseconds of simulation
     * @return true if this needs more simulation
     */
    public boolean simulateFor(int micros) {
        this.time = micros;

        synchronized (this.monitor) {
            if (this.done) {
                return false;
            }

            this.watch.reset();
            this.watch.start();
            this.running = true;
            invalidateMachineLocationCaches();

            AELog.craftingDebug("main thread is now going to sleep");

            this.monitor.notify();

            while (this.running) {
                try {
                    this.monitor.wait();
                } catch (InterruptedException ignored) {
                }
            }

            AELog.craftingDebug("main thread is now active");
        }

        return true;
    }

    private void logCraftingJob(ICraftingPlan plan) {
        if (AELog.isCraftingLogEnabled()) {
            StringBuilder message = new StringBuilder();
            message.append("CraftingCalculation issued by %s requesting [%dx%s] breakdown:\n".formatted(
                getActionSourceName(), this.requestedAmount, this.output));
            for (int i = 0; i < this.attempts.size(); i++) {
                var attempt = this.attempts.get(i);
                message.append(" - %s in %d ms\n".formatted(
                    attempt.description(), attempt.stopwatch().elapsed(TimeUnit.MILLISECONDS)));
            }
            message.append(" - final plan: %d (%d bytes)".formatted(plan.finalOutput().amount(), plan.bytes()));

            AELog.crafting(message.toString());
        }
    }

    private String getActionSourceName() {
        var actionSource = this.simRequester.getActionSource();
        if (actionSource != null && actionSource.player().isPresent()) {
            var player = actionSource.player().get();
            return player.toString();
        }
        if (actionSource != null && actionSource.machine().isPresent()) {
            var machineSource = actionSource.machine().get();
            var actionableNode = machineSource.getActionableNode();
            return actionableNode != null ? actionableNode.toString() : machineSource.toString();
        }
        return "[unknown source]";
    }

    public boolean hasMultiplePaths() {
        var tree = this.tree;
        if (tree != null) {
            return tree.hasMultiplePaths();
        }
        var graph = this.graph;
        if (graph != null) {
            for (var node : graph.getAllNodes()) {
                if (node.getPatternCandidates().size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    int getTreeDepth() {
        var tree = this.tree;
        return tree == null ? getMaxRequestDepth() : tree.getDepth();
    }

    long getTreeNodeCount() {
        var tree = this.tree;
        if (tree != null) {
            return tree.getNodeCount();
        }
        var graph = this.graph;
        return graph == null ? 0 : graph.getNodeCount();
    }

    long getPatternNodeCount() {
        var tree = this.tree;
        if (tree != null) {
            return tree.getPatternNodeCount();
        }
        long result = 0;
        var graph = this.graph;
        if (graph != null) {
            for (var node : graph.getAllNodes()) {
                if (node.getPattern() != null) {
                    result++;
                }
            }
        }
        return result;
    }

    int getMaxRequestDepth() {
        return this.maxRequestDepth;
    }

    public boolean isPerformanceTrackingEnabled() {
        return this.performanceListener.isEnabled();
    }

    public void recordPerformanceStage(String name, long nanos) {
        if (this.performanceListener.isEnabled()) {
            this.performanceListener.stage(name, nanos);
        }
    }

    void recordPerformanceSelfStage(String name, long nanos) {
        if (this.performanceListener.isEnabled()) {
            this.performanceListener.selfStage(name, nanos);
        }
    }

    public void recordPerformanceCount(String name, long amount) {
        if (this.performanceListener.isEnabled()) {
            this.performanceListener.count(name, amount);
        }
    }

    private void finishPerformanceListener(long nanos) {
        if (this.performanceListener.isEnabled()) {
            this.performanceListener.finish(nanos, this);
        }
    }

    <T> T timed(String name, InterruptibleSupplier<T> supplier) throws InterruptedException {
        if (!this.performanceListener.isEnabled()) {
            return supplier.get();
        }
        long start = System.nanoTime();
        this.timingStack.add(new TimingFrame());
        try {
            return supplier.get();
        } finally {
            long total = System.nanoTime() - start;
            var frame = this.timingStack.removeLast();
            if (!this.timingStack.isEmpty()) {
                this.timingStack.getLast().childNanos += total;
            }
            recordPerformanceStage(name, total);
            recordPerformanceSelfStage(name, Math.max(0, total - frame.childNanos));
        }
    }

    void runTimedCrafting(String name, CraftingRunnable runnable)
        throws InterruptedException, CraftBranchFailure {
        if (!this.performanceListener.isEnabled()) {
            runnable.run();
            return;
        }
        long start = System.nanoTime();
        this.timingStack.add(new TimingFrame());
        try {
            runnable.run();
        } finally {
            long total = System.nanoTime() - start;
            var frame = this.timingStack.removeLast();
            if (!this.timingStack.isEmpty()) {
                this.timingStack.getLast().childNanos += total;
            }
            recordPerformanceStage(name, total);
            recordPerformanceSelfStage(name, Math.max(0, total - frame.childNanos));
        }
    }

    @FunctionalInterface
    interface InterruptibleSupplier<T> {
        T get() throws InterruptedException;
    }

    @FunctionalInterface
    interface CraftingRunnable {
        void run() throws InterruptedException, CraftBranchFailure;
    }

    private record CraftAttempt(String description, Stopwatch stopwatch) {
    }

    private record RecursiveSeed(AEKey what, long amount) {
    }

    private static final class RecursiveNetKey {
        private AEKey what;
        private int requestIndex;
        private ObjectArrayList<CraftingTreeProcess> liveProcesses;
        private CraftingTreeProcess[] frozenProcesses;
        private int processOffset;
        private int processCount;
        private int hashCode;

        private RecursiveNetKey() {
        }

        private static RecursiveNetKey mutableProbe() {
            return new RecursiveNetKey();
        }

        private void reset(AEKey what, int requestIndex, ObjectArrayList<CraftingTreeProcess> processes,
                           int processOffset, int processCount, int processHash) {
            this.what = what;
            this.requestIndex = requestIndex;
            this.liveProcesses = processes;
            this.processOffset = processOffset;
            this.processCount = processCount;
            this.hashCode = computeHashCode(what, requestIndex, processCount, processHash);
        }

        private RecursiveNetKey freeze(ObjectPool<RecursiveNetKey> pool) {
            var processes = new CraftingTreeProcess[this.processCount];
            for (int i = 0; i < this.processCount; i++) {
                processes[i] = processAt(i);
            }
            var key = pool.acquire();
            key.what = this.what;
            key.requestIndex = this.requestIndex;
            key.frozenProcesses = processes;
            key.liveProcesses = null;
            key.processOffset = 0;
            key.processCount = processes.length;
            key.hashCode = computeHashCode(this.what, this.requestIndex, this.processCount,
                getProcessIdentityHash(processes));
            return key;
        }

        private static int getProcessIdentityHash(CraftingTreeProcess[] processes) {
            int result = 0;
            for (CraftingTreeProcess process : processes) {
                result = 31 * result + System.identityHashCode(process);
            }
            return result;
        }

        private static int computeHashCode(AEKey what, int requestIndex, int processCount, int processHash) {
            int result = what.hashCode();
            result = 31 * result + requestIndex;
            result = 31 * result + processCount;
            return 31 * result + processHash;
        }

        private AEKey what() {
            return this.what;
        }

        private int requestIndex() {
            return this.requestIndex;
        }

        private int processCount() {
            return this.processCount;
        }

        private CraftingTreeProcess processAt(int index) {
            if (this.frozenProcesses != null) {
                return this.frozenProcesses[index];
            }
            return this.liveProcesses.get(this.processOffset + index);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof RecursiveNetKey other)
                || this.hashCode != other.hashCode
                || this.requestIndex != other.requestIndex
                || this.processCount != other.processCount
                || !this.what.equals(other.what)) {
                return false;
            }
            for (int i = 0; i < this.processCount; i++) {
                if (this.processAt(i) != other.processAt(i)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }

    private record RecursiveNet(int requestIndex, KeyCounter netByKey, Collection<AEKey> inputKeys,
                                boolean canResolve) {
    }

    private record RecursiveResolution(RecursiveSeed seed, boolean missingSeeds, RecursiveSeed missingSeed) {
    }

    public record CalculationMarker(KeyCounter missingItems,
                                    KeyCounter recursiveMissingSeeds,
                                    Set<AEKey> realSeededRecursiveRequests,
                                    Set<AEKey> realRecursiveSeeds,
                                    Set<AEKey> realSeededRecursiveKeys,
                                    Set<AEKey> recursiveFinalOutputInputs,
                                    KeyCounter recursiveReserveCandidates,
                                    Reference2LongMap<CraftingTreeNode> recursiveDisplayRequests,
                                    long intermediateFinalOutputAmount,
                                    @Nullable KeyCounter reserveProtectedMissingSeeds,
                                    int missingSuppression,
                                    int recursiveMissingSeedSuppression,
                                    boolean applyingRecursiveIngredientReserve,
                                    boolean recursiveReserveBlockedByMissingSeed) {
    }

    public record CalculationDelta(KeyCounter missingItems,
                                   KeyCounter recursiveMissingSeeds,
                                   Set<AEKey> realSeededRecursiveRequests,
                                   Set<AEKey> realRecursiveSeeds,
                                   Set<AEKey> realSeededRecursiveKeys,
                                   Set<AEKey> recursiveFinalOutputInputs,
                                   KeyCounter recursiveReserveCandidates,
                                   Reference2LongMap<CraftingTreeNode> recursiveDisplayRequests,
                                   long intermediateFinalOutputAmount) {
    }

    private record RecursiveReserveBranchMarker(CalculationMarker calculationMarker) {
    }

    public record RecursivePatternBatch(long rootTimes, long netOutput) {
    }

    private static final class RecursiveUse {
        private boolean aBoolean;

        private RecursiveUse(boolean aBoolean) {
            this.aBoolean = aBoolean;
        }

        private boolean get() {
            return this.aBoolean;
        }

        private void set() {
            this.aBoolean = true;
        }
    }

    private static final class TimingFrame {
        private long childNanos;
    }

    static final class MemoKey {
        private final AEKey what;
        private final int fingerprint;
        private final int hashCode;

        private MemoKey(AEKey what, int fingerprint) {
            this.what = what;
            this.fingerprint = fingerprint;
            this.hashCode = Objects.hash(what, fingerprint);
        }

        static MemoKey create(AEKey what, CraftingCalculation job) {
            int fp = computeFingerprint(what, job);
            return new MemoKey(what, fp);
        }

        private static int computeFingerprint(AEKey what, CraftingCalculation job) {
            int fp = 0;

            var patterns = job.getCraftingFor(what);
            fp = hashCombine(fp, patterns.size());

            for (int i = 0, size = patterns.size(); i < size; i++) {
                var pattern = patterns.get(i);
                fp = hashCombine(fp, pattern.hashCode());

                var inputs = pattern.getInputs();
                fp = hashCombine(fp, inputs.length);
                for (var input : inputs) {
                    var possibleInputs = input.possibleInputs();
                    fp = hashCombine(fp, possibleInputs.length);
                    for (GenericStack possible : possibleInputs) {
                        AEKey depKey = possible.what();
                        fp = hashCombine(fp, depKey.hashCode());

                        var subPatterns = job.getCraftingFor(depKey);
                        fp = hashCombine(fp, subPatterns.isEmpty() ? 0 : 1);
                    }
                }
            }
            return fp;
        }

        private static int hashCombine(int hash, int value) {
            return 31 * hash + value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoKey other)) return false;
            return fingerprint == other.fingerprint && what.equals(other.what);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    static final class MemoResult {
        final KeyCounter extracted;
        final KeyCounter containerItems;
        final Reference2LongOpenHashMap<IPatternDetails> patternTimes;
        double bytes;
        long baseTimes;

        final KeyCounter emittedItems;
        final KeyCounter missingItems;
        final KeyCounter insertedItems;
        final KeyCounter pseudoItems;
        long intermediateFinalOutputAmount;

        final KeyCounter recursiveMissingSeeds;
        final KeyCounter clearedRecursiveMissingSeeds;
        final ObjectOpenHashSet<AEKey> realSeededRecursiveRequests;
        final ObjectOpenHashSet<AEKey> realRecursiveSeeds;
        final ObjectOpenHashSet<AEKey> realSeededRecursiveKeys;
        final Reference2LongOpenHashMap<CraftingTreeNode> recursiveDisplayRequestsDelta;

        MemoResult() {
            this.extracted = new KeyCounter();
            this.containerItems = new KeyCounter();
            this.patternTimes = new Reference2LongOpenHashMap<>();
            this.patternTimes.defaultReturnValue(0);
            this.bytes = 0;

            this.emittedItems = new KeyCounter();
            this.missingItems = new KeyCounter();
            this.insertedItems = new KeyCounter();
            this.pseudoItems = new KeyCounter();
            this.intermediateFinalOutputAmount = 0;

            this.recursiveMissingSeeds = new KeyCounter();
            this.clearedRecursiveMissingSeeds = new KeyCounter();
            this.realSeededRecursiveRequests = new ObjectOpenHashSet<>();
            this.realRecursiveSeeds = new ObjectOpenHashSet<>();
            this.realSeededRecursiveKeys = new ObjectOpenHashSet<>();
            this.recursiveDisplayRequestsDelta = new Reference2LongOpenHashMap<>();
            this.recursiveDisplayRequestsDelta.defaultReturnValue(0);
        }

        boolean isApplicable(CraftingSimulationState inv) {
            for (var entry : extracted) {
                if (inv.getAvailableAmount(entry.getKey()) < entry.getLongValue()) {
                    return false;
                }
            }

            return true;
        }

        void recordExtraction(AEKey key, long amount) {
            this.extracted.add(key, amount);
        }

        void recordContainerItem(AEKey key, long amount) {
            this.containerItems.add(key, amount);
        }

        void recordPattern(IPatternDetails pattern, long times) {
            this.patternTimes.addTo(pattern, times);
        }

        void recordEmitted(AEKey key, long amount) {
            this.emittedItems.add(key, amount);
        }

        void recordMissing(AEKey key, long amount) {
            this.missingItems.add(key, amount);
        }

        void recordInserted(AEKey key, long amount) {
            this.insertedItems.add(key, amount);
        }

        void recordPseudo(AEKey key, long amount) {
            this.pseudoItems.add(key, amount);
        }

        void recordIntermediateFinalOutput(long amount) {
            this.intermediateFinalOutputAmount += amount;
        }

        void recordRecursiveMissingSeeds(KeyCounter delta) {
            this.recursiveMissingSeeds.addAll(delta);
        }

        void recordClearedRecursiveMissingSeeds(KeyCounter delta) {
            this.clearedRecursiveMissingSeeds.addAll(delta);
        }

        void recordRealSeededRecursiveRequests(ObjectOpenHashSet<AEKey> keys) {
            this.realSeededRecursiveRequests.addAll(keys);
        }

        void recordRealRecursiveSeeds(ObjectOpenHashSet<AEKey> keys) {
            this.realRecursiveSeeds.addAll(keys);
        }

        void recordRealSeededRecursiveKeys(ObjectOpenHashSet<AEKey> keys) {
            this.realSeededRecursiveKeys.addAll(keys);
        }

        void recordRecursiveDisplayRequests(Reference2LongOpenHashMap<CraftingTreeNode> delta) {
            for (var entry : delta.reference2LongEntrySet()) {
                this.recursiveDisplayRequestsDelta.addTo(entry.getKey(), entry.getLongValue());
            }
        }

        Bundle toBundle(long baseTimes) {
            return new Bundle(this, baseTimes);
        }
    }

    static final class Bundle {
        final KeyCounter extracted;
        final KeyCounter containerItems;
        final Reference2LongOpenHashMap<IPatternDetails> patternTimes;
        final long baseTimes;
        double bytes;

        final KeyCounter emittedItems;
        final KeyCounter missingItems;
        final KeyCounter insertedItems;
        final KeyCounter pseudoItems;
        long intermediateFinalOutputAmount;

        final KeyCounter recursiveMissingSeeds;
        final KeyCounter clearedRecursiveMissingSeeds;
        final ObjectOpenHashSet<AEKey> realSeededRecursiveRequests;
        final ObjectOpenHashSet<AEKey> realRecursiveSeeds;
        final ObjectOpenHashSet<AEKey> realSeededRecursiveKeys;
        final Reference2LongOpenHashMap<CraftingTreeNode> recursiveDisplayRequestsDelta;

        Bundle(MemoResult result, long baseTimes) {
            this.extracted = new KeyCounter();
            this.containerItems = new KeyCounter();
            this.patternTimes = new Reference2LongOpenHashMap<>();
            this.patternTimes.defaultReturnValue(0);

            this.extracted.addAll(result.extracted);
            this.containerItems.addAll(result.containerItems);
            this.patternTimes.putAll(result.patternTimes);
            this.baseTimes = baseTimes;
            this.bytes = result.bytes;

            this.emittedItems = new KeyCounter();
            this.emittedItems.addAll(result.emittedItems);
            this.missingItems = new KeyCounter();
            this.missingItems.addAll(result.missingItems);
            this.insertedItems = new KeyCounter();
            this.insertedItems.addAll(result.insertedItems);
            this.pseudoItems = new KeyCounter();
            this.pseudoItems.addAll(result.pseudoItems);
            this.intermediateFinalOutputAmount = result.intermediateFinalOutputAmount;

            this.recursiveMissingSeeds = new KeyCounter();
            this.recursiveMissingSeeds.addAll(result.recursiveMissingSeeds);
            this.clearedRecursiveMissingSeeds = new KeyCounter();
            this.clearedRecursiveMissingSeeds.addAll(result.clearedRecursiveMissingSeeds);
            this.realSeededRecursiveRequests = new ObjectOpenHashSet<>(result.realSeededRecursiveRequests);
            this.realRecursiveSeeds = new ObjectOpenHashSet<>(result.realRecursiveSeeds);
            this.realSeededRecursiveKeys = new ObjectOpenHashSet<>(result.realSeededRecursiveKeys);
            this.recursiveDisplayRequestsDelta = new Reference2LongOpenHashMap<>(result.recursiveDisplayRequestsDelta);
            this.recursiveDisplayRequestsDelta.defaultReturnValue(0);
        }

        Bundle scale(long targetTimes) {
            if (targetTimes == this.baseTimes) {
                return this;
            }

            double multiplier = (double) targetTimes / this.baseTimes;
            var scaled = new Bundle(targetTimes);

            for (var e : this.extracted) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.extracted.add(e.getKey(), scaledAmount);
            }
            for (var e : this.containerItems) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.containerItems.add(e.getKey(), scaledAmount);
            }
            for (var e : this.patternTimes.reference2LongEntrySet()) {
                long scaledTimes = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.patternTimes.put(e.getKey(), scaledTimes);
            }
            scaled.bytes = this.bytes * multiplier;

            for (var e : this.emittedItems) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.emittedItems.add(e.getKey(), scaledAmount);
            }
            for (var e : this.missingItems) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.missingItems.add(e.getKey(), scaledAmount);
            }
            for (var e : this.insertedItems) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.insertedItems.add(e.getKey(), scaledAmount);
            }
            for (var e : this.pseudoItems) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.pseudoItems.add(e.getKey(), scaledAmount);
            }
            scaled.intermediateFinalOutputAmount = (long) Math.ceil(this.intermediateFinalOutputAmount * multiplier);

            for (var e : this.recursiveMissingSeeds) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.recursiveMissingSeeds.add(e.getKey(), scaledAmount);
            }
            for (var e : this.clearedRecursiveMissingSeeds) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.clearedRecursiveMissingSeeds.add(e.getKey(), scaledAmount);
            }
            scaled.realSeededRecursiveRequests.addAll(this.realSeededRecursiveRequests);
            scaled.realRecursiveSeeds.addAll(this.realRecursiveSeeds);
            scaled.realSeededRecursiveKeys.addAll(this.realSeededRecursiveKeys);
            for (var e : this.recursiveDisplayRequestsDelta.reference2LongEntrySet()) {
                long scaledAmount = (long) Math.ceil(e.getLongValue() * multiplier);
                scaled.recursiveDisplayRequestsDelta.put(e.getKey(), scaledAmount);
            }

            return scaled;
        }

        private Bundle(long baseTimes) {
            this.extracted = new KeyCounter();
            this.containerItems = new KeyCounter();
            this.patternTimes = new Reference2LongOpenHashMap<>();
            this.patternTimes.defaultReturnValue(0);
            this.baseTimes = baseTimes;
            this.bytes = 0;

            this.emittedItems = new KeyCounter();
            this.missingItems = new KeyCounter();
            this.insertedItems = new KeyCounter();
            this.pseudoItems = new KeyCounter();
            this.intermediateFinalOutputAmount = 0;

            this.recursiveMissingSeeds = new KeyCounter();
            this.clearedRecursiveMissingSeeds = new KeyCounter();
            this.realSeededRecursiveRequests = new ObjectOpenHashSet<>();
            this.realRecursiveSeeds = new ObjectOpenHashSet<>();
            this.realSeededRecursiveKeys = new ObjectOpenHashSet<>();
            this.recursiveDisplayRequestsDelta = new Reference2LongOpenHashMap<>();
            this.recursiveDisplayRequestsDelta.defaultReturnValue(0);
        }

        boolean isSatisfiable(CraftingSimulationState inv) {
            var testInv = new ChildCraftingSimulationState(inv);
            for (var e : extracted) {
                long available = testInv.extract(e.getKey(), e.getLongValue(), Actionable.MODULATE);
                if (available < e.getLongValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Nullable
    MemoResult getMemoResult(MemoKey key) {
        return this.memoReplayEnabled ? (MemoResult) this.memoCache.getIfPresent(key) : null;
    }

    void putMemoResult(MemoKey key, MemoResult result) {
        if (this.memoReplayEnabled) {
            this.memoCache.put(key, result);
        }
    }

    static final class BundleKey {
        private final AEKey what;
        private final IPatternDetails pattern;
        private final int hashCode;

        BundleKey(AEKey what, IPatternDetails pattern) {
            this.what = what;
            this.pattern = pattern;
            this.hashCode = what.hashCode() * 31 + pattern.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BundleKey other)) return false;
            return what.equals(other.what) && pattern.equals(other.pattern);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    @Nullable
    Bundle getBundle(BundleKey key) {
        return this.memoReplayEnabled ? (Bundle) this.memoCache.getIfPresent(key) : null;
    }

    void putBundle(BundleKey key, Bundle bundle) {
        if (this.memoReplayEnabled) {
            this.memoCache.put(key, bundle);
        }
    }

    private static final class ObjectPool<T> {
        private final ObjectArrayList<T> pool = new ObjectArrayList<>();
        private final Supplier<T> factory;

        ObjectPool(Supplier<T> factory, int initialCapacity) {
            this.factory = factory;
            for (int i = 0; i < initialCapacity; i++) {
                pool.add(factory.get());
            }
        }

        T acquire() {
            if (!pool.isEmpty()) {
                return pool.removeLast();
            }
            return factory.get();
        }

        void release(T obj) {
            pool.add(obj);
        }
    }

}
