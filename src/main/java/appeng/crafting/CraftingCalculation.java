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

package appeng.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import appeng.hooks.ticking.TickHandler;
import com.google.common.base.Stopwatch;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CraftingCalculation {
    final ICraftingSimulationRequester simRequester;
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
    private final Map<AEKey, Collection<IPatternDetails>> patternCache = new HashMap<>();
    private final List<ICraftingProvider> temporaryProviders;
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
    private boolean synchronousCalculation = false;
    private int maxRequestDepth = 0;
    private long intermediateFinalOutputAmount = 0;
    private int recursiveMissingSeedSuppression = 0;

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
        this.networkInv = new NetworkCraftingSimulationState(storage, simRequester.getActionSource());

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
                total += output.amount();
            }
        }
        return total;
    }

    private static long getPatternInputCount(IPatternDetails pattern, AEKey what) {
        long total = 0;
        for (var input : pattern.getInputs()) {
            for (var possibleInput : input.possibleInputs()) {
                if (what.matches(possibleInput)) {
                    total += possibleInput.amount() * input.getMultiplier();
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
        for (var output : pattern.getOutputs()) {
            netByKey.add(output.what(), output.amount() * times);
        }

        for (var input : pattern.getInputs()) {
            var primaryInput = input.possibleInputs()[0];
            netByKey.add(primaryInput.what(), -primaryInput.amount() * input.getMultiplier() * times);
        }
    }

    void addMissing(AEKey what, long amount) {
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

    ICraftingPlan runSynchronouslyForTest() {
        try {
            this.synchronousCalculation = true;
            startPerformanceListener();
            var plan = timed("compute-plan", this::computePlan);
            this.logCraftingJob(plan);
            return plan;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        } finally {
            this.finish();
            this.synchronousCalculation = false;
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

                return runCraftAttempt(false, requestedAmount, requestedAmount, true);
            }

            // Missing items no longer prevent submitting a player-requested plan.
            return runCraftAttempt(false, requestedAmount, requestedAmount, true);
        } finally {
            finishPerformanceListener(System.nanoTime() - calculationStart);
        }
    }

    private CraftingPlan runCraftAttempt(boolean simulate, long productionAmount, long finalAmount, boolean allowMissing)
        throws InterruptedException {
        this.simulate = simulate;
        this.allowMissing = allowMissing;
        this.missing.clear();
        this.intermediateFinalOutputAmount = 0;
        this.recursiveMissingSeedSuppression = 0;
        this.tree.resetPossible();

        final Stopwatch timer = Stopwatch.createStarted();
        final String attemptName = "attempt amount=%d final=%d simulate=%s allowMissing=%s".formatted(productionAmount,
            finalAmount, simulate, allowMissing);
        final long attemptStart = System.nanoTime();

        ChildCraftingSimulationState craftingInventory = new ChildCraftingSimulationState(networkInv);
        injectRecursiveMissingSeeds(craftingInventory);

        // Do the crafting. Throws in case of failure.
        try {
            timedCrafting("tree-request " + attemptName, () -> {
                this.tree.request(craftingInventory, productionAmount, null);
                return null;
            });
        } catch (CraftBranchFailure failure) {
            if (AELog.isCraftingLogEnabled()) {
                this.attempts.add(new CraftAttempt(productionAmount + " failed", timer));
            }
            recordPerformanceStage(attemptName + " failed", System.nanoTime() - attemptStart);
            return null;
        }
        // Add bytes for the tree size.
        craftingInventory.addBytes(this.tree.getNodeCount() * 8);
        addRecursiveMissingSeedsToPlan();

        var plan = timed("build-plan " + attemptName,
            () -> CraftingSimulationState.buildCraftingPlan(craftingInventory, this, finalAmount));
        if (AELog.isCraftingLogEnabled()) {
            String type = simulate ? "simulated" : "succeeded";
            this.attempts.add(new CraftAttempt("%d %s (%d bytes)".formatted(productionAmount, type, plan.bytes()),
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
        this.intermediateFinalOutputAmount = 0;
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

        craftingInventory.addBytes(this.tree.getNodeCount() * 8);

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
        if (this.synchronousCalculation) {
            return;
        }

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
            netOutput += process.getOutputCount(what);
            netOutput -= process.getInputCount(what);
        }
        return netOutput;
    }

    boolean resolveRecursiveRequest(AEKey what, CraftingSimulationState inv, long amount) {
        var resolution = getRecursiveResolution(what, inv);
        if (resolution == null) {
            return false;
        }

        if (resolution.seed() != null) {
            clearRecursiveMissingSeeds(resolution.netByKey());
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

    boolean canResolveRecursiveRequest(AEKey what, CraftingSimulationState inv) {
        return getRecursiveResolution(what, inv) != null;
    }

    private RecursiveResolution getRecursiveResolution(AEKey what, CraftingSimulationState inv) {
        int requestIndex = -1;
        for (int i = this.requestStack.size() - 1; i >= 0; i--) {
            if (this.requestStack.get(i).equals(what)) {
                requestIndex = i;
                break;
            }
        }
        if (requestIndex < 0) {
            return null;
        }

        var netByKey = new KeyCounter();
        var includedPatterns = new HashSet<IPatternDetails>();
        for (int i = requestIndex; i < this.processStack.size(); i++) {
            var process = this.processStack.get(i);
            process.accumulateNet(netByKey);
            includedPatterns.add(process.details);
        }

        expandRecursiveNetClosure(netByKey, includedPatterns);

        boolean hasPositiveNet = false;
        for (var entry : netByKey) {
            long net = entry.getLongValue();
            if (net < 0) {
                return null;
            }
            if (net > 0) {
                hasPositiveNet = true;
            }
        }

        if (!hasPositiveNet || netByKey.get(what) < 0) {
            return null;
        }

        var seed = getRecursiveSeed(netByKey, requestIndex);
        if (seed != null) {
            return new RecursiveResolution(seed, false, netByKey);
        }

        if (canUseMissingItems() && this.recursiveMissingSeedSuppression == 0
            && addMissingRecursiveSeeds(netByKey, requestIndex)) {
            return new RecursiveResolution(null, true, netByKey);
        }

        return null;
    }

    long getExpandedPatternNetOutput(IPatternDetails pattern, AEKey what) {
        long directOutput = getPatternOutputCount(pattern, what);
        if (directOutput <= 0) {
            return 0;
        }

        var netByKey = new KeyCounter();
        var includedPatterns = new HashSet<IPatternDetails>();
        includedPatterns.add(pattern);
        accumulatePatternNet(netByKey, pattern);
        expandRecursiveNetClosure(netByKey, includedPatterns);

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

            if (!recursiveUse.value()) {
                return new RecursivePatternBatch(1, directOutput);
            }

            long netOutput = netByKey.get(what);
            if (netOutput > 0) {
                return new RecursivePatternBatch(rootTimes, netOutput);
            }
        }

        return new RecursivePatternBatch(1, directOutput);
    }

    private RecursiveSeed getRecursiveSeed(KeyCounter netByKey, int requestIndex) {
        for (var entry : netByKey) {
            if (entry.getLongValue() >= 0 && this.networkInv.getOriginalAmount(entry.getKey()) > 0) {
                return new RecursiveSeed(entry.getKey(), getRecursiveSeedAmount(entry.getKey(), requestIndex));
            }
        }
        return null;
    }

    private long getRecursiveSeedAmount(AEKey seed, int requestIndex) {
        if (requestIndex >= 0 && requestIndex < this.processStack.size()) {
            for (int i = requestIndex; i < this.processStack.size(); i++) {
                var process = this.processStack.get(i);
                if (process.getFirstInputKey() != null && process.getFirstInputKey().equals(seed)) {
                    long amount = process.getFirstInputAmount();
                    if (amount > 0) {
                        return amount;
                    }
                }
            }
        }
        return 1;
    }

    private boolean addMissingRecursiveSeeds(KeyCounter netByKey, int requestIndex) {
        boolean addedSeed = false;
        for (var entry : netByKey) {
            if (entry.getLongValue() < 0) {
                addRecursiveMissingSeed(entry.getKey(), -entry.getLongValue());
                addedSeed = true;
            }
        }
        if (addedSeed) {
            return true;
        }

        if (requestIndex >= 0 && requestIndex < this.processStack.size()) {
            var seed = this.processStack.get(requestIndex).getFirstInputKey();
            if (seed != null) {
                addRecursiveMissingSeed(seed, 1);
                return true;
            }
        }
        return false;
    }

    private void addRecursiveMissingSeed(AEKey what, long amount) {
        long existing = this.recursiveMissingSeeds.get(what);
        if (existing >= amount) {
            return;
        }

        long delta = amount - existing;
        this.recursiveMissingSeeds.add(what, delta);
    }

    private void clearRecursiveMissingSeeds(KeyCounter netByKey) {
        for (var entry : netByKey) {
            this.recursiveMissingSeeds.remove(entry.getKey());
            this.missing.remove(entry.getKey());
        }
    }

    private void injectRecursiveMissingSeeds(CraftingSimulationState inv) {
        for (var entry : this.recursiveMissingSeeds) {
            inv.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        }
    }

    private void addRecursiveMissingSeedsToPlan() {
        for (var entry : this.recursiveMissingSeeds) {
            this.missing.add(entry.getKey(), entry.getLongValue());
        }
    }

    private void expandRecursiveNetClosure(KeyCounter netByKey, HashSet<IPatternDetails> includedPatterns) {
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
                        accumulatePatternNet(netByKey, pattern);
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
                    missingAmount = -entry.getLongValue();
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
                return false;
            }

            if (getPatternInputCount(selectedPattern, target) > 0) {
                recursiveUse.set();
            }
            long times = (missingAmount + outputAmount - 1) / outputAmount;
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
        this.intermediateFinalOutputAmount += amount;
    }

    void addRecursiveIntermediateFinalOutput(long amount) {
        this.intermediateFinalOutputAmount += amount;
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

    void addRecursiveMissingSeeds(KeyCounter seeds) {
        this.recursiveMissingSeeds.addAll(seeds);
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

    <T> T timedCrafting(String name, CraftingSupplier<T> supplier)
        throws InterruptedException, CraftBranchFailure {
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

    @FunctionalInterface
    interface InterruptibleSupplier<T> {
        T get() throws InterruptedException;
    }

    @FunctionalInterface
    interface CraftingSupplier<T> {
        T get() throws InterruptedException, CraftBranchFailure;
    }

    private record CraftAttempt(String description, Stopwatch stopwatch) {
    }

    private record RecursiveSeed(AEKey what, long amount) {
    }

    private record RecursiveResolution(RecursiveSeed seed, boolean missingSeeds, KeyCounter netByKey) {
    }

    record RecursivePatternBatch(long rootTimes, long netOutput) {
    }

    private static final class RecursiveUse {
        private boolean value;

        private RecursiveUse(boolean value) {
            this.value = value;
        }

        private boolean value() {
            return this.value;
        }

        private void set() {
            this.value = true;
        }
    }

    private static final class TimingFrame {
        private long childNanos;
    }
}
