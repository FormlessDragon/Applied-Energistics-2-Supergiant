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
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AEConfig;
import ae2.core.AELog;
import ae2.crafting.execution.InputTemplate;
import ae2.crafting.inv.ChildCraftingSimulationState;
import ae2.crafting.inv.CraftingSimulationState;
import ae2.crafting.inv.NetworkCraftingSimulationState;
import ae2.hooks.ticking.TickHandler;
import com.google.common.base.Stopwatch;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CraftingCalculation {
    final ICraftingSimulationRequester simRequester;
    private final ICraftingService craftingService;
    private final NetworkCraftingSimulationState networkInv;
    private final World level;
    private final KeyCounter missing = new KeyCounter();
    private final KeyCounter recursiveMissingSeeds = new KeyCounter();
    private final Object monitor = new Object();
    private final CraftingPerformanceListener performanceListener;
    private final Stopwatch watch = Stopwatch.createUnstarted();
    private final CraftingTreeNode tree;
    private final AEKey output;
    private final List<AEKey> requestStack = new ObjectArrayList<>();
    private final List<AEKey> availabilityStack = new ObjectArrayList<>();
    private final List<CraftingTreeProcess> processStack = new ObjectArrayList<>();
    private final List<TimingFrame> timingStack = new ObjectArrayList<>();
    private final Map<AEKey, Collection<IPatternDetails>> patternCache = new Object2ObjectOpenHashMap<>();
    private final Map<RecursiveNetKey, RecursiveNet> recursiveNetCache = new Object2ObjectOpenHashMap<>();
    private final Set<AEKey> realSeededRecursiveRequests = new ObjectOpenHashSet<>();
    private final Set<AEKey> realRecursiveSeeds = new ObjectOpenHashSet<>();
    private final Set<AEKey> realSeededRecursiveKeys = new ObjectOpenHashSet<>();
    private final Set<AEKey> recursiveFinalOutputInputs = new ObjectOpenHashSet<>();
    private final KeyCounter recursiveReserveCandidates = new KeyCounter();
    private final Reference2LongMap<CraftingTreeNode> recursiveDisplayRequests = new Reference2LongOpenHashMap<>();
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

    public CraftingCalculation(World level, IGrid grid, ICraftingSimulationRequester simRequester,
                               GenericStack output, CalculationStrategy strategy) {
        this(level, grid, simRequester, output, strategy, createPerformanceListener());
    }

    CraftingCalculation(World level, IGrid grid, ICraftingSimulationRequester simRequester,
                        GenericStack output, CalculationStrategy strategy,
                        CraftingPerformanceListener performanceListener) {
        this.level = level;
        this.output = output.what();
        this.requestedAmount = output.amount();
        this.strategy = strategy;
        this.simRequester = simRequester;
        this.performanceListener = performanceListener;
        this.temporaryProviders = List.copyOf(simRequester.getAdditionalProviders());

        var storage = grid.getStorageService();
        var craftingService = grid.getCraftingService();
        this.craftingService = craftingService;
        this.networkInv = new NetworkCraftingSimulationState(storage, simRequester.getActionSource());
        this.recursiveIngredientReserveAmount = Math.max(0, craftingService.getRecursiveIngredientReserveAmount());

        long treeStart = System.nanoTime();
        this.tree = new CraftingTreeNode(craftingService, this, this.output, 1, null, -1);
        recordPerformanceStage("construct-tree", System.nanoTime() - treeStart);
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

    private static long getPatternOutputCount(IPatternDetails pattern, AEKey what) {
        long total = 0;
        for (var output : pattern.getOutputs()) {
            if (what.matches(output)) {
                total = LongMath.saturatedAdd(total, output.amount());
            }
        }
        return total;
    }

    private static long getPatternInputCount(IPatternDetails pattern, AEKey what) {
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
        for (var output : pattern.getOutputs()) {
            netByKey.add(output.what(), LongMath.saturatedMultiply(output.amount(), times));
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

    void addMissing(AEKey what, long amount) {
        if (this.realSeededRecursiveKeys.contains(what) || this.realRecursiveSeeds.contains(what)
            || hasRealSeededRecursiveRequestFor(what)) {
            return;
        }
        missing.add(what, amount);
    }

    Collection<IPatternDetails> getCraftingFor(AEKey what) {
        return this.patternCache.computeIfAbsent(what, key -> {
            var gridNode = this.simRequester.getGridNode();
            if (gridNode == null) {
                return List.of();
            }
            var patterns = new ObjectArrayList<IPatternDetails>();
            patterns.addAll(gridNode.grid().getCraftingService().getCraftingFor(key));
            for (var pattern : this.simRequester.getAdditionalPatterns()) {
                if (pattern.getPrimaryOutput().what().equals(key)) {
                    patterns.add(pattern);
                }
            }
            return List.copyOf(patterns);
        });
    }

    List<InputTemplate> collectValidTemplates(Iterable<InputTemplate> templates) {
        var collected = new ObjectArrayList<InputTemplate>();
        for (var template : templates) {
            collected.add(template);
        }
        return List.copyOf(collected);
    }

    public ICraftingPlan run() {
        try {
            startPerformanceListener();
            TickHandler.instance().registerCraftingSimulation(this.level, this);
            this.handlePausing();

            var plan = timed("compute-plan", this::computePlan);
            this.logCraftingJob(plan);
            return plan;
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
        var calculationStart = System.nanoTime();
        try {
            if (strategy == CalculationStrategy.CRAFT_LESS) {
                var craftLessPlan = runCraftLessAttempt(requestedAmount);
                if (craftLessPlan != null) {
                    return craftLessPlan;
                }

                return runCraftAttempt(requestedAmount, requestedAmount);
            }

            // Missing items no longer prevent submitting a player-requested plan.
            return runCraftAttempt(requestedAmount, requestedAmount);
        } finally {
            finishPerformanceListener(System.nanoTime() - calculationStart);
        }
    }

    private CraftingPlan runCraftAttempt(long productionAmount, long finalAmount)
        throws InterruptedException {
        this.simulate = false;
        this.allowMissing = true;
        this.missing.clear();
        this.recursiveMissingSeeds.clear();
        this.recursiveNetCache.clear();
        this.intermediateFinalOutputAmount = 0;
        this.recursiveMissingSeedSuppression = 0;
        this.realSeededRecursiveRequests.clear();
        this.realRecursiveSeeds.clear();
        this.realSeededRecursiveKeys.clear();
        this.recursiveFinalOutputInputs.clear();
        this.recursiveReserveCandidates.clear();
        this.recursiveDisplayRequests.clear();
        this.tree.resetPossible();

        final Stopwatch timer = Stopwatch.createStarted();
        final String attemptName = "attempt amount=%d final=%d".formatted(productionAmount, finalAmount);
        final long attemptStart = System.nanoTime();

        ChildCraftingSimulationState craftingInventory = new ChildCraftingSimulationState(networkInv);

        // Do the crafting. Throws in case of failure.
        try {
            runTimedCrafting("tree-request " + attemptName,
                () -> this.tree.request(craftingInventory, productionAmount, null));
        } catch (CraftBranchFailure failure) {
            if (failure.hasExplicitMessageKey()) {
                throw new CraftingCalculationFailure(failure.getLocalizedMessageKey());
            }
            if (AELog.isCraftingLogEnabled()) {
                this.attempts.add(new CraftAttempt(productionAmount + " failed", timer));
            }
            recordPerformanceStage(attemptName + " failed", System.nanoTime() - attemptStart);
            return null;
        }
        applyRecursiveIngredientReserve(craftingInventory);
        clearResolvedRecursiveMissingItems(craftingInventory);
        addRecursiveMissingSeedsToPlan();
        // Add bytes for the tree size.
        craftingInventory.addBytes((double) this.tree.getNodeCount() * 8);

        var plan = timed("build-plan " + attemptName,
            () -> CraftingSimulationState.buildCraftingPlan(craftingInventory, this, finalAmount));
        if (AELog.isCraftingLogEnabled()) {
            this.attempts.add(new CraftAttempt("%d succeeded (%d bytes)".formatted(productionAmount, plan.bytes()),
                timer));
        }
        recordPerformanceStage(attemptName + " completed", System.nanoTime() - attemptStart);
        return plan;
    }

    @Nullable
    private CraftingPlan runCraftLessAttempt(long amount) throws InterruptedException {
        this.simulate = false;
        this.allowMissing = false;
        this.missing.clear();
        this.recursiveMissingSeeds.clear();
        this.recursiveNetCache.clear();
        this.intermediateFinalOutputAmount = 0;
        this.realSeededRecursiveRequests.clear();
        this.realRecursiveSeeds.clear();
        this.realSeededRecursiveKeys.clear();
        this.recursiveFinalOutputInputs.clear();
        this.recursiveReserveCandidates.clear();
        this.recursiveDisplayRequests.clear();
        this.tree.resetPossible();

        final Stopwatch timer = Stopwatch.createStarted();
        final String attemptName = "craft-less amount=%d".formatted(amount);
        final long attemptStart = System.nanoTime();

        ChildCraftingSimulationState craftingInventory = new ChildCraftingSimulationState(networkInv);

        long craftableAmount = timed("craft-less-available " + attemptName,
            () -> this.tree.extractAvailableForCrafting(craftingInventory, amount));
        if (craftableAmount <= 0) {
            if (AELog.isCraftingLogEnabled()) {
                this.attempts.add(new CraftAttempt(amount + " craft-less failed", timer));
            }
            recordPerformanceStage(attemptName + " failed", System.nanoTime() - attemptStart);
            return null;
        }

        craftingInventory.addBytes((double) this.tree.getNodeCount() * 8);

        var plan = timed("build-plan " + attemptName,
            () -> CraftingSimulationState.buildCraftingPlan(craftingInventory, this, craftableAmount));
        if (AELog.isCraftingLogEnabled()) {
            this.attempts.add(new CraftAttempt("%d craft-less (%d bytes)".formatted(craftableAmount, plan.bytes()),
                timer));
        }
        recordPerformanceStage(attemptName + " completed", System.nanoTime() - attemptStart);
        return plan;
    }

    void handlePausing() throws InterruptedException {
        if (this.incTime > 100) {
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
        for (AEKey requested : this.requestStack) {
            if (requested.equals(what)) {
                return true;
            }
        }
        return false;
    }

    boolean isCheckingAvailability(AEKey what) {
        for (AEKey requested : this.availabilityStack) {
            if (requested.equals(what)) {
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
    }

    void popProcess() {
        this.processStack.removeLast();
    }

    boolean cycleHasNetOutput(AEKey what) {
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

        var cacheKey = RecursiveNetKey.create(what, requestIndex, this.processStack);
        return this.recursiveNetCache.computeIfAbsent(cacheKey, ignored -> computeRecursiveNet(what, requestIndex));
    }

    private int findRecursiveRequestIndex(AEKey what) {
        for (int i = this.requestStack.size() - 1; i >= 0; i--) {
            if (this.requestStack.get(i).equals(what)) {
                return i;
            }
        }
        return -1;
    }

    private RecursiveNet computeRecursiveNet(AEKey what, int requestIndex) {
        recordPerformanceCount("recursive-net-analysis", 1);
        var netByKey = new KeyCounter();
        var inputKeys = new ObjectOpenHashSet<AEKey>();
        var includedPatterns = new ObjectOpenHashSet<IPatternDetails>();
        for (int i = requestIndex; i < this.processStack.size(); i++) {
            var process = this.processStack.get(i);
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

        return new RecursiveNet(requestIndex, netByKey, inputKeys, hasPositiveNet && netByKey.get(what) >= 0);
    }

    long getExpandedPatternNetOutput(IPatternDetails pattern, AEKey what) {
        long directOutput = getPatternOutputCount(pattern, what);
        if (directOutput <= 0) {
            return 0;
        }

        var netByKey = new KeyCounter();
        var includedPatterns = new ObjectOpenHashSet<IPatternDetails>();
        includedPatterns.add(pattern);
        accumulatePatternNet(netByKey, pattern);
        expandRecursiveNetClosure(netByKey, null, includedPatterns);

        long netOutput = netByKey.get(what);
        if (netOutput > 0 && netOutput < directOutput) {
            return netOutput;
        }
        return directOutput;
    }

    RecursivePatternBatch getRecursivePatternBatch(IPatternDetails pattern, AEKey what) {
        long directOutput = getPatternOutputCount(pattern, what);
        if (directOutput <= 0) {
            return new RecursivePatternBatch(1, 0);
        }

        boolean rootConsumesTarget = getPatternInputCount(pattern, what) > 0;
        for (long rootTimes = 1; rootTimes <= 64; rootTimes++) {
            var netByKey = new KeyCounter();
            var recursiveUse = new RecursiveUse(rootConsumesTarget);
            accumulatePatternNet(netByKey, pattern, rootTimes);
            if (!expandRecursiveBatchNet(netByKey, what, recursiveUse)) {
                return new RecursivePatternBatch(1, directOutput);
            }

            if (!recursiveUse.get()) {
                return new RecursivePatternBatch(1, directOutput);
            }

            long netOutput = netByKey.get(what);
            if (netOutput > 0) {
                return new RecursivePatternBatch(rootTimes, netOutput);
            }
        }

        return new RecursivePatternBatch(1, directOutput);
    }

    private RecursiveSeed getRecursiveSeed(CraftingSimulationState inv, RecursiveNet recursiveNet, AEKey what,
                                           long locallyExtractedAmount) {
        for (AEKey seed : recursiveNet.inputKeys()) {
            if (recursiveNet.netByKey().get(seed) < 0 || isReserveProtectedMissingSeed(seed)) {
                continue;
            }
            long amount = getRecursiveSeedAmount(seed, recursiveNet.requestIndex());
            long available = inv.getAvailableNonProducedAmount(seed);
            if (seed.equals(what)) {
                available += locallyExtractedAmount;
            }
            if (available >= amount) {
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
            for (AEKey what : reserveCandidates) {
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

                var reserveNode = new CraftingTreeNode(this.craftingService, this, what, 1, null, -1);
                var branchInv = new ChildCraftingSimulationState(inv);
                var branchMarker = createRecursiveReserveBranchMarker();
                this.recursiveReserveBlockedByMissingSeed = false;
                try {
                    runTimedCrafting("recursive-reserve " + what, () -> reserveNode.request(branchInv, deficit, null));
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

    private RecursiveReserveBranchMarker createRecursiveReserveBranchMarker() {
        return new RecursiveReserveBranchMarker(
            getMissingItemsMarker(),
            getRecursiveMissingSeedsMarker(),
            getRealSeededRecursiveRequestsMarker(),
            getRealRecursiveSeedsMarker(),
            getRealSeededRecursiveKeysMarker(),
            getRecursiveDisplayRequestsMarker(),
            getIntermediateFinalOutputMarker());
    }

    private void restoreRecursiveReserveBranchMarker(RecursiveReserveBranchMarker marker) {
        restoreMissingItemsMarker(marker.missingItems());
        restoreRecursiveMissingSeedsMarker(marker.recursiveMissingSeeds());
        restoreRealSeededRecursiveRequestsMarker(marker.realSeededRecursiveRequests());
        restoreRealRecursiveSeedsMarker(marker.realRecursiveSeeds());
        restoreRealSeededRecursiveKeysMarker(marker.realSeededRecursiveKeys());
        restoreRecursiveDisplayRequestsMarker(marker.recursiveDisplayRequests());
        restoreIntermediateFinalOutputMarker(marker.intermediateFinalOutputAmount());
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
        var displayNode = this.tree.findDisplayNodeFor(what);
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
        for (AEKey key : keys) {
            this.missing.remove(key);
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
        boolean changed;
        do {
            changed = false;
            var negativeKeys = new ObjectArrayList<AEKey>();
            for (var entry : netByKey) {
                if (entry.getLongValue() < 0) {
                    negativeKeys.add(entry.getKey());
                }
            }

            for (var key : negativeKeys) {
                for (var pattern : this.getCraftingFor(key)) {
                    if (includedPatterns.add(pattern)) {
                        accumulatePatternNet(netByKey, inputKeys, pattern, 1);
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);
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
            for (var candidate : this.getCraftingFor(missingKey)) {
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

    public CraftingTreeNode getTree() {
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
        this.recursiveDisplayRequests.merge(node, amount, LongMath::saturatedAdd);
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
            for (CraftAttempt attempt : this.attempts) {
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
        return this.tree.hasMultiplePaths();
    }

    int getTreeDepth() {
        return this.tree.getDepth();
    }

    long getTreeNodeCount() {
        return this.tree.getNodeCount();
    }

    long getPatternNodeCount() {
        return this.tree.getPatternNodeCount();
    }

    int getMaxRequestDepth() {
        return this.maxRequestDepth;
    }

    void recordPerformanceStage(String name, long nanos) {
        if (this.performanceListener.isEnabled()) {
            this.performanceListener.stage(name, nanos);
        }
    }

    void recordPerformanceSelfStage(String name, long nanos) {
        if (this.performanceListener.isEnabled()) {
            this.performanceListener.selfStage(name, nanos);
        }
    }

    void recordPerformanceCount(String name, long amount) {
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

    private record RecursiveNetKey(AEKey what, int requestIndex, List<CraftingTreeProcess> processes) {
        private static RecursiveNetKey create(AEKey what, int requestIndex, List<CraftingTreeProcess> processStack) {
            return new RecursiveNetKey(what, requestIndex,
                List.copyOf(processStack.subList(requestIndex, processStack.size())));
        }
    }

    private record RecursiveNet(int requestIndex, KeyCounter netByKey, Collection<AEKey> inputKeys,
                                boolean canResolve) {
    }

    private record RecursiveResolution(RecursiveSeed seed, boolean missingSeeds, RecursiveSeed missingSeed) {
    }

    private record RecursiveReserveBranchMarker(KeyCounter missingItems,
                                                KeyCounter recursiveMissingSeeds,
                                                Set<AEKey> realSeededRecursiveRequests,
                                                Set<AEKey> realRecursiveSeeds,
                                                Set<AEKey> realSeededRecursiveKeys,
                                                Reference2LongMap<CraftingTreeNode> recursiveDisplayRequests,
                                                long intermediateFinalOutputAmount) {
    }

    record RecursivePatternBatch(long rootTimes, long netOutput) {
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
}
