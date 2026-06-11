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
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.core.localization.PlayerMessages;
import ae2.crafting.execution.CraftingCpuHelper;
import ae2.crafting.execution.InputTemplate;
import ae2.crafting.inv.ChildCraftingSimulationState;
import ae2.crafting.inv.CraftingSimulationState;
import ae2.crafting.inv.ICraftingInventory;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A crafting tree node is what represents a single requested stack in the crafting process. It can either be the
 * top-level requested stack (slot is then -1, parent is null), or a stack used in a pattern (slot is then the position
 * of this stack in the pattern, parent is the parent node).
 */
public class CraftingTreeNode {

    /**
     * what input this node is for. Null for the top-level node.
     */
    @Nullable
    final IPatternDetails.IInput parentInput;
    private final CraftingCalculation job;
    // parent node.
    private final CraftingTreeProcess parent;
    private final World level;
    /**
     * "Template" of the item this node is making. For top-level node: the count is always 1. For child nodes: the count
     * is that of the template of the corresponding input.
     */
    private final AEKey what;
    private final long amount;
    private final boolean canEmit;
    /**
     * The patterns that can make this node. Null if they haven't been computed yet.
     */
    private List<CraftingTreeProcess> nodes = null;
    private Boolean selfReturningRemainderInput;
    private boolean recursiveDisplayNodesInitialized;

    public CraftingTreeNode(ICraftingService cc, CraftingCalculation job, AEKey what, long amount,
                            CraftingTreeProcess par, int slot) {
        this.parent = par;
        this.parentInput = slot == -1 ? null : par.details.getInputs()[slot];
        this.level = job.getLevel();
        this.job = job;
        this.what = findCraftedStack(cc, what);
        this.amount = amount;

        this.canEmit = cc.canEmitFor(what);
    }

    private AEKey findCraftedStack(ICraftingService cc, AEKey wat) {
        if (cc.canEmitFor(wat)) {
            return wat; // if we can emit for something, use that.
        }

        var patterns = this.job.getCraftingFor(wat);

        if (patterns.isEmpty() && parentInput != null) {
            // No pattern for the exact encoded input. Try to find a pattern for a substitute ingredient. ;)
            long acceptableAmount = parentInput.possibleInputs()[0].amount();

            for (var possibleInput : parentInput.possibleInputs()) {
                if (possibleInput.amount() != acceptableAmount) {
                    // Skip if the amounts don't match (don't want to replace 1000 water by 1000 buckets for example).
                    continue;
                }

                var fuzzy = cc.getFuzzyCraftable(possibleInput.what(), fuzzyCandidate -> this.parentInput.isValid(fuzzyCandidate, level));

                if (fuzzy != null) {
                    return fuzzy;
                }
            }
        }

        return wat;
    }

    private void buildChildPatterns() {
        // Sanity check: this should never be called if this is emitable
        if (this.canEmit) {
            throw new IllegalStateException("Internal AE2 error: this node is emitable, it shouldn't use patterns!");
        }

        if (this.nodes == null) {
            long start = System.nanoTime();
            this.nodes = new ObjectArrayList<>();

            var gridNode = this.job.simRequester.getGridNode();
            if (gridNode != null) {
                var craftingService = gridNode.grid().getCraftingService();
                for (var details : this.job.getCraftingFor(this.what)) {
                    if (this.parent == null || this.parent.notRecursive(details)) {
                        this.nodes.add(new CraftingTreeProcess(craftingService, job, details, this));
                    }
                }
            }
            this.job.recordPerformanceCount("patterns-for-" + this.what, this.nodes.size());
            this.job.recordPerformanceStage("build-child-patterns " + this.what, System.nanoTime() - start);
        }
    }

    /**
     * Return true if adding this pattern as a child would not cause recursion.
     */
    @SuppressWarnings("unused")
    boolean notRecursive(IPatternDetails details) {
        return true;
    }

    private static long divideCeil(long dividend, long divisor) {
        long quotient = dividend / divisor;
        if (dividend % divisor == 0) {
            return quotient;
        }
        return quotient + 1;
    }

    /**
     * Request items. Will always succeed or throw an exception.
     *
     * @param inv             Current simulated inventory.
     * @param requestedAmount How many items. The raw amount for top-level requests, or the number of inputs for
     *                        requests that have a parent.
     * @param containerItems  A list where produced container items are written if it's not null.
     * @throws CraftBranchFailure If the request failed.
     */
    void request(CraftingSimulationState inv, long requestedAmount,
                 @Nullable KeyCounter containerItems)
        throws CraftBranchFailure, InterruptedException {
        this.job.handlePausing();

        if (this.job.isRequesting(this.what)) {
            long requestedItems = getTotalRequestedItems(requestedAmount);
            if (this.job.resolveRecursiveRequest(this.what, inv, requestedItems)) {
                this.job.addRecursiveDisplayRequest(this, requestedItems);
                if (this.what.equals(this.job.getOutput())) {
                    var currentRequest = this.job.getCurrentRequestKey();
                    if (currentRequest != null) {
                        this.job.addRecursiveFinalOutputInput(currentRequest);
                    }
                    this.job.addRecursiveIntermediateFinalOutput(requestedItems);
                }
                return;
            }
            if (this.job.cycleHasNetOutput(this.what) && this.job.canUseMissingItems()) {
                job.addMissing(this.what, requestedItems);
                return;
            }
            if (this.job.canUseMissingItems()) {
                throw new CraftBranchFailure(this.what, requestedItems,
                    PlayerMessages.CraftingNoNetOutput);
            }
            throw new CraftBranchFailure(this.what, requestedItems);
        }

        this.job.pushRequest(this.what);
        try {
            requestInner(inv, requestedAmount, containerItems);
        } finally {
            this.job.popRequest();
        }
    }

    private void requestInner(CraftingSimulationState inv, long requestedAmount,
                              @Nullable KeyCounter containerItems)
        throws CraftBranchFailure, InterruptedException {
        inv.addStackBytes(what, amount, requestedAmount);

        /*
         * 1) COLLECT ITEMS FROM THE INVENTORY
         */
        if (!isTopLevelRequestedOutput()) {
            // Templates: must copy before using!
            for (var template : getValidItemTemplates(inv)) {
                long extracted = CraftingCpuHelper.extractTemplates(inv, template, requestedAmount);

                if (extracted > 0) {
                    // TODO: we should keep track of which items we extracted to make sure the CPU uses exactly those when
                    // TODO: it processes the job.
                    requestedAmount -= extracted;
                    addContainerItems(template.key(), extracted, containerItems);
                    this.job.addIntermediateFinalOutputInput(template.key(),
                        LongMath.saturatedMultiply(extracted, template.amount()));

                    if (requestedAmount == 0) {
                        return;
                    }
                }
            }

            if (requestedAmount > 0 && canUsePseudoInputs()) {
                for (var template : getValidItemTemplates(inv)) {
                    long extracted = extractPseudoTemplates(inv, template, requestedAmount);
                    if (extracted <= 0) {
                        continue;
                    }

                    requestedAmount -= extracted;
                    addContainerItems(template.key(), extracted, containerItems);

                    if (requestedAmount == 0) {
                        return;
                    }
                }
            }
        }

        // Already add the container items: if we fail, the process above will fail and they will be discarded anyway.
        addContainerItems(what, requestedAmount, containerItems);

        /*
         * 2) EMITABLE ITEMS
         */
        if (this.canEmit) {
            inv.emitItems(this.what, getTotalRequestedItems(requestedAmount));
            return;
        }

        /*
         * 3) USE PATTERNS
         */
        buildChildPatterns();
        long totalRequestedItems = getTotalRequestedItems(requestedAmount);
        if (!this.nodes.isEmpty()) {
            for (CraftingTreeProcess pro : this.nodes) {
                totalRequestedItems = requestCraftingBranch(inv, pro, totalRequestedItems);
                if (totalRequestedItems <= 0) {
                    break;
                }
            }
            if (totalRequestedItems <= 0) {
                return;
            }
        }

        if (totalRequestedItems > 0 && this.job.canUseMissingItems() && !this.nodes.isEmpty()) {
            requestMissingBranches(inv, totalRequestedItems);
            return;
        }

        if (totalRequestedItems > 0) {
            if (this.job.canUseMissingItems()) {
                job.addMissing(this.what, totalRequestedItems);
            } else {
                throw new CraftBranchFailure(this.what, totalRequestedItems);
            }
        }
    }

    private void requestMissingBranches(CraftingSimulationState inv, long totalRequestedItems)
        throws CraftBranchFailure, InterruptedException {
        boolean requestedAnyBranch = false;
        for (CraftingTreeProcess pro : this.nodes) {
            if (pro.getInputCount(this.what) >= pro.getOutputCount(this.what)) {
                continue;
            }
            requestMissingBranch(inv, pro, totalRequestedItems);
            requestedAnyBranch = true;
            break;
        }

        if (!requestedAnyBranch) {
            job.addMissing(this.what, totalRequestedItems);
        }
    }

    private void requestMissingBranch(CraftingSimulationState inv, CraftingTreeProcess pro, long totalRequestedItems)
        throws CraftBranchFailure, InterruptedException {
        var craftedPerPattern = getEffectiveOutputCount(pro);
        var recursiveBatch = this.job.getRecursivePatternBatch(pro.details, this.what);

        while (totalRequestedItems > 0) {
            long times = getRequestedPatternTimes(pro, totalRequestedItems, craftedPerPattern, recursiveBatch);
            this.job.runTimedCrafting("request-missing-branch " + this.what, () -> pro.request(inv, times));
            pro.addTreeRequestTimes(times);

            // by now we have succeeded, as request throws an exception in case of failure
            // check how much was actually produced
            var available = extractCraftedBranchOutput(inv, totalRequestedItems);
            if (available != 0) {
                totalRequestedItems -= available;

                if (totalRequestedItems <= 0) {
                    return;
                }
            } else {
                return;
            }
        }
    }

    long extractAvailableForCrafting(CraftingSimulationState inv, long maxAmount)
        throws InterruptedException {
        this.job.handlePausing();

        if (this.job.isCheckingAvailability(this.what)) {
            return 0;
        }

        this.job.pushAvailabilityCheck(this.what);
        try {
            return extractAvailableForCraftingInner(inv, maxAmount);
        } finally {
            this.job.popAvailabilityCheck();
        }
    }

    private long requestCraftingBranch(CraftingSimulationState inv, CraftingTreeProcess pro, long totalRequestedItems)
        throws InterruptedException {
        if (!pro.possible || totalRequestedItems <= 0) {
            return totalRequestedItems;
        }

        var craftedPerPattern = getEffectiveOutputCount(pro);
        while (totalRequestedItems > 0) {
            var recursiveBatch = this.job.getRecursivePatternBatch(pro.details, this.what);
            long requestedTimes = getRequestedPatternTimes(pro, totalRequestedItems, craftedPerPattern, recursiveBatch);
            long times = this.job.timed("max-craftable " + this.what, () -> pro.getMaximumCraftableTimes(inv, requestedTimes));
            if (times <= 0) {
                pro.possible = false;
                return totalRequestedItems;
            }

            long available;
            long missingSeedAmount = pro.getReusablePreviewRecursiveMissingSeedAmount(this.what);
            if (missingSeedAmount > 0) {
                long usedMissingSeed = Math.min(totalRequestedItems, missingSeedAmount);
                pro.applyReusablePreviewRecursiveMissingSeedsOnly();
                totalRequestedItems -= usedMissingSeed;
                if (totalRequestedItems <= 0) {
                    return 0;
                }
                continue;
            } else if (pro.applyReusablePreview(inv, times)) {
                pro.addTreeRequestTimes(times);
                available = extractCraftedBranchOutput(inv, totalRequestedItems);
            } else {
                final ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
                long intermediateFinalOutputMarker = this.job.getIntermediateFinalOutputMarker();
                try {
                    this.job.pushMissingSuppression();
                    this.job.runTimedCrafting("request-branch " + this.what, () -> pro.request(child, times));
                    pro.addTreeRequestTimes(times);
                } catch (CraftBranchFailure failure) {
                    this.job.restoreIntermediateFinalOutputMarker(intermediateFinalOutputMarker);
                    pro.possible = false;
                    return totalRequestedItems;
                } finally {
                    this.job.popMissingSuppression();
                }

                available = extractCraftedBranchOutput(child, totalRequestedItems);
                if (craftedPerPattern > 0) {
                    available = Math.min(available, LongMath.saturatedMultiply(craftedPerPattern, times));
                }
                if (available > 0) {
                    child.applyDiff(inv);
                } else {
                    this.job.restoreIntermediateFinalOutputMarker(intermediateFinalOutputMarker);
                }
            }

            if (available <= 0) {
                pro.possible = false;
                return totalRequestedItems;
            }

            totalRequestedItems -= available;

            if (!pro.limitsQuantity()) {
                return totalRequestedItems;
            }
        }
        return totalRequestedItems;
    }

    // Only item stacks are supported.
    private void addContainerItems(AEKey template, long multiplier,
                                   @Nullable KeyCounter outputList) {
        if (outputList != null && this.parentInput != null) {
            var containerItem = parentInput.getRemainingKey(template);
            if (containerItem != null) {
                outputList.add(containerItem, multiplier);
            }
        }
    }

    private long extractAvailableForCraftingInner(CraftingSimulationState inv, long maxAmount)
        throws InterruptedException {
        long available = 0;

        if (!isTopLevelRequestedOutput()) {
            var intermediateFinalOutputMarker = this.job.getIntermediateFinalOutputMarker();
            for (var template : getValidItemTemplates(inv)) {
                long extracted = CraftingCpuHelper.extractTemplates(inv, template, maxAmount - available);
                available = LongMath.saturatedAdd(available, extracted);
                this.job.addIntermediateFinalOutputInput(template.key(),
                    LongMath.saturatedMultiply(extracted, template.amount()));
                if (available >= maxAmount) {
                    return maxAmount;
                }
            }
            if (available < maxAmount && canUsePseudoInputs()) {
                for (var template : getValidItemTemplates(inv)) {
                    long extracted = extractPseudoTemplates(inv, template, maxAmount - available);
                    available = LongMath.saturatedAdd(available, extracted);
                    if (available >= maxAmount) {
                        return maxAmount;
                    }
                }
            }
            if (available == 0) {
                this.job.restoreIntermediateFinalOutputMarker(intermediateFinalOutputMarker);
            }
        }

        if (this.job.isRequesting(this.what)) {
            if (this.job.canResolveRecursiveRequest(this.what, inv, getTotalRequestedItems(available))) {
                long remainingAmount = maxAmount - available;
                if (remainingAmount > 0) {
                    this.job.addRecursiveDisplayRequest(this, getTotalRequestedItems(maxAmount));
                    inv.insert(this.what, getTotalRequestedItems(remainingAmount), Actionable.MODULATE);
                    if (this.what.equals(this.job.getOutput())) {
                        var currentRequest = this.job.getCurrentRequestKey();
                        if (currentRequest != null) {
                            this.job.addRecursiveFinalOutputInput(currentRequest);
                        }
                        this.job.addRecursiveIntermediateFinalOutput(getTotalRequestedItems(remainingAmount));
                    }
                }
                return maxAmount;
            }
            return available;
        }

        if (this.canEmit) {
            return maxAmount;
        }

        buildChildPatterns();
        long totalRequestedItems = getTotalRequestedItems(maxAmount - available);
        for (CraftingTreeProcess pro : this.nodes) {
            if (!pro.possible || totalRequestedItems <= 0) {
                continue;
            }
            long craftedPerPattern = getEffectiveOutputCount(pro);
            var recursiveBatch = this.job.getRecursivePatternBatch(pro.details, this.what);
            long requestedTimes = getRequestedPatternTimes(pro, totalRequestedItems, craftedPerPattern, recursiveBatch);
            long times = this.job.timed("max-craftable-for-input " + this.what,
                () -> pro.getMaximumCraftableTimes(inv, requestedTimes));
            if (times <= 0) {
                continue;
            }
            long missingSeedAmount = pro.getReusablePreviewRecursiveMissingSeedAmount(this.what);
            if (missingSeedAmount > 0) {
                long usedMissingSeed = Math.min(totalRequestedItems, missingSeedAmount);
                pro.applyReusablePreviewRecursiveMissingSeedsOnly();
                totalRequestedItems -= usedMissingSeed;
                available = LongMath.saturatedAdd(available, usedMissingSeed / this.amount);
                if (totalRequestedItems <= 0) {
                    break;
                }
                continue;
            }
            if (pro.applyReusablePreview(inv, times)) {
                pro.addTreeRequestTimes(times);
            } else {
                try {
                    this.job.pushMissingSuppression();
                    this.job.runTimedCrafting("request-input-branch " + this.what, () -> pro.request(inv, times));
                    pro.addTreeRequestTimes(times);
                } catch (CraftBranchFailure ignored) {
                    continue;
                } finally {
                    this.job.popMissingSuppression();
                }
            }
            long produced = extractCraftedBranchOutput(inv, totalRequestedItems);
            if (produced > 0) {
                totalRequestedItems -= produced;
                available = LongMath.saturatedAdd(available, produced / this.amount);
            }
        }

        return Math.min(maxAmount, available);
    }

    private boolean canUsePseudoInputs() {
        return this.parent != null && PseudoPatternDetails.isPseudo(this.parent.details);
    }

    /**
     * Get all stack templates that can be used for this node.
     *
     * @param inv Crafting inventory, used for fuzzy matching.
     */
    private Iterable<InputTemplate> getValidItemTemplates(ICraftingInventory inv) {
        if (this.parentInput == null)
            return List.of(new InputTemplate(what, 1));
        long start = System.nanoTime();
        var templates = this.job.collectValidTemplates(CraftingCpuHelper.getValidItemTemplates(inv, this.parentInput,
            level));
        this.job.recordPerformanceStage("fuzzy-templates " + this.what, System.nanoTime() - start);
        return templates;
    }

    private long extractCraftedBranchOutput(CraftingSimulationState inv, long amount) {
        long extracted = inv.extract(this.what, amount, Actionable.MODULATE);
        if (extracted >= amount) {
            return extracted;
        }
        return LongMath.saturatedAdd(extracted, inv.extractPseudo(this.what, amount - extracted, Actionable.MODULATE));
    }

    private long getEffectiveOutputCount(CraftingTreeProcess pro) {
        if (usesFinalOutputAsIntermediateInput(pro)) {
            return pro.getOutputCount(this.what);
        }
        long recursiveNetOutput = this.job.getCycleNetOutput(this.what);
        if (recursiveNetOutput > 0) {
            return recursiveNetOutput;
        }
        var recursiveBatch = this.job.getRecursivePatternBatch(pro.details, this.what);
        if (recursiveBatch.netOutput() > 0) {
            return recursiveBatch.netOutput();
        }
        long expandedPatternNetOutput = this.job.getExpandedPatternNetOutput(pro.details, this.what);
        if (expandedPatternNetOutput > 0) {
            return expandedPatternNetOutput;
        }
        return pro.getEffectiveOutputCount(this.what);
    }

    private boolean usesFinalOutputAsIntermediateInput(CraftingTreeProcess pro) {
        return !this.what.equals(this.job.getOutput()) && pro.getInputCount(this.job.getOutput()) > 0;
    }

    private long extractPseudoTemplates(CraftingSimulationState inv, InputTemplate template, long multiplier) {
        long maxTotal = LongMath.saturatedMultiply(template.amount(), multiplier);
        long extracted = inv.extractPseudo(template.key(), maxTotal, Actionable.SIMULATE);
        if (extracted == 0) {
            return 0;
        }

        multiplier = extracted / template.amount();
        maxTotal = LongMath.saturatedMultiply(template.amount(), multiplier);
        if (maxTotal == 0) {
            return 0;
        }

        extracted = inv.extractPseudo(template.key(), maxTotal, Actionable.MODULATE);
        if (extracted == 0 || extracted != maxTotal) {
            throw new IllegalStateException("Failed to correctly extract pseudo templates. Invalid simulation!");
        }
        return multiplier;
    }

    private long getRequestedPatternTimes(CraftingTreeProcess pro, long totalRequestedItems, long craftedPerPattern,
                                          CraftingCalculation.RecursivePatternBatch recursiveBatch) {
        if (pro.limitsQuantity()) {
            return 1;
        }
        if (usesFinalOutputAsIntermediateInput(pro)) {
            return divideCeil(totalRequestedItems, craftedPerPattern);
        }
        long netOutput = recursiveBatch.netOutput() > 0 ? recursiveBatch.netOutput() : craftedPerPattern;
        long rootTimes = Math.max(1, recursiveBatch.rootTimes());
        return LongMath.saturatedMultiply(divideCeil(totalRequestedItems, netOutput), rootTimes);
    }

    int getDepth() {
        int depth = 1;
        if (this.nodes != null) {
            for (CraftingTreeProcess pro : this.nodes) {
                depth = Math.max(depth, 1 + pro.getDepth());
            }
        }
        return depth;
    }

    long getNodeCount() {
        long tot = 1;
        if (this.nodes != null) {
            for (CraftingTreeProcess pro : this.nodes) {
                tot = LongMath.saturatedAdd(tot, pro.getNodeCount());
            }
        }
        return tot;
    }

    boolean hasMultiplePaths() {
        if (this.nodes == null) {
            return false;
        }
        if (this.nodes.size() > 1) {
            return true;
        }
        for (var pro : this.nodes) {
            if (pro.hasMultiplePaths()) {
                return true;
            }
        }
        return false;
    }

    void resetPossible() {
        this.recursiveDisplayNodesInitialized = false;
        if (this.nodes != null) {
            for (CraftingTreeProcess pro : this.nodes) {
                pro.resetPossible();
            }
        }
    }

    public List<CraftingTreeProcess> getNodes() {
        return this.nodes;
    }

    long getPatternNodeCount() {
        long total = this.nodes == null ? 0 : this.nodes.size();
        if (this.nodes != null) {
            for (CraftingTreeProcess pro : this.nodes) {
                total = LongMath.saturatedAdd(total, pro.getPatternNodeCount());
            }
        }
        return total;
    }

    CraftingTreeNode findDisplayNodeFor(AEKey key) {
        return findDisplayNodeFor(key, true);
    }

    private CraftingTreeNode findDisplayNodeFor(AEKey key, boolean skipSelf) {
        if (!skipSelf && this.what.equals(key)) {
            return this;
        }
        if (this.nodes == null) {
            return null;
        }
        for (CraftingTreeProcess process : this.nodes) {
            for (CraftingTreeNode node : process.getNodes().keySet()) {
                var found = node.findDisplayNodeFor(key, false);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public long getRecursiveDisplayAmount() {
        return this.job.getRecursiveDisplayRequest(this);
    }

    public AEKey getWhat() {
        return this.what;
    }

    public long getAmount() {
        return this.amount;
    }

    public long getMissing() {
        return job.getMissingItems().get(what);
    }

    public boolean hasSelfReturningRemainderInput() {
        if (this.parentInput == null) {
            return false;
        }

        if (selfReturningRemainderInput != null) {
            return selfReturningRemainderInput;
        }

        for (var possibleInput : this.parentInput.possibleInputs()) {
            if (possibleInput.what().equals(this.parentInput.getRemainingKey(possibleInput.what()))) {
                return selfReturningRemainderInput = true;
            }
        }
        return selfReturningRemainderInput = this.what.equals(this.parentInput.getRemainingKey(this.what));
    }

    long getTemplateAmount() {
        return this.amount;
    }

    public List<CraftingTreeProcess> getDisplayNodes() {
        long recursiveDisplayAmount = getRecursiveDisplayAmount();
        if (this.nodes == null && recursiveDisplayAmount > 0) {
            buildChildPatterns();
        }
        if (this.nodes != null && recursiveDisplayAmount > 0 && !this.recursiveDisplayNodesInitialized) {
            this.recursiveDisplayNodesInitialized = true;
            for (CraftingTreeProcess process : this.nodes) {
                long outputCount = process.getOutputCount(this.what);
                if (outputCount <= 0) {
                    continue;
                }
                process.addTreeRequestTimes(divideCeil(recursiveDisplayAmount, outputCount));
                break;
            }
        }
        return this.nodes;
    }

    private long getTotalRequestedItems(long requestedAmount) {
        return LongMath.saturatedMultiply(requestedAmount, this.amount);
    }

    private boolean isTopLevelRequestedOutput() {
        return this.parent == null && this.parentInput == null && this.what.equals(this.job.getOutput());
    }
}
