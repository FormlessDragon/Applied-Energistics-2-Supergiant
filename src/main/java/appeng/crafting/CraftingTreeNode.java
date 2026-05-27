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
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ICraftingInventory;
import appeng.helpers.patternprovider.PseudoPatternDetails;
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
                    if (!canUsePseudoProducer(details)) {
                        continue;
                    }
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
    boolean notRecursive(IPatternDetails details) {
        return true;
    }

    private boolean canUsePseudoProducer(IPatternDetails details) {
        return !PseudoPatternDetails.isPseudo(details) || isTopLevelRequestedOutput() || canUsePseudoInputs();
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
            if (this.job.resolveRecursiveRequest(this.what, inv, requestedAmount * this.amount)) {
                if (this.what.equals(this.job.getOutput())) {
                    this.job.addRecursiveIntermediateFinalOutput(requestedAmount * this.amount);
                }
                return;
            }
            if (this.job.cycleHasNetOutput(this.what) && this.job.canUseMissingItems()) {
                job.addMissing(this.what, requestedAmount * this.amount);
                return;
            }
            throw new CraftBranchFailure(this.what, requestedAmount * this.amount);
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
                    this.job.addIntermediateFinalOutputInput(template.key(), extracted * template.amount());

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
            inv.emitItems(this.what, this.amount * requestedAmount);
            return;
        }

        /*
         * 3) USE PATTERNS
         */
        buildChildPatterns();
        long totalRequestedItems = requestedAmount * this.amount;
        if (!this.nodes.isEmpty()) {
            for (CraftingTreeProcess pro : this.nodes) {
                totalRequestedItems = requestCraftingBranch(inv, pro, totalRequestedItems);
                if (totalRequestedItems <= 0) {
                    return;
                }
            }
        }

        if (totalRequestedItems > 0 && this.job.canUseMissingItems() && !this.nodes.isEmpty()) {
            requestMissingBranch(inv, this.nodes.getFirst(), totalRequestedItems);
            return;
        }

        if (this.job.canUseMissingItems()) {
            job.addMissing(this.what, totalRequestedItems);
        } else {
            throw new CraftBranchFailure(this.what, totalRequestedItems);
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
            if (pro.applyReusablePreview(inv, times)) {
                available = extractCraftedBranchOutput(inv, totalRequestedItems);
            } else {
                final ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
                long intermediateFinalOutputMarker = this.job.getIntermediateFinalOutputMarker();
                try {
                    this.job.pushMissingSuppression();
                    this.job.timedCrafting("request-branch " + this.what, () -> {
                        pro.request(child, times);
                        return null;
                    });
                } catch (CraftBranchFailure failure) {
                    this.job.restoreIntermediateFinalOutputMarker(intermediateFinalOutputMarker);
                    pro.possible = false;
                    return totalRequestedItems;
                } finally {
                    this.job.popMissingSuppression();
                }

                available = extractCraftedBranchOutput(child, totalRequestedItems);
                if (craftedPerPattern > 0) {
                    available = Math.min(available, craftedPerPattern * times);
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

    private void requestMissingBranch(CraftingSimulationState inv, CraftingTreeProcess pro, long totalRequestedItems)
        throws CraftBranchFailure, InterruptedException {
            if (pro.getInputCount(this.what) >= pro.getOutputCount(this.what)) {
                job.addMissing(this.what, totalRequestedItems);
                return;
            }

            var craftedPerPattern = getEffectiveOutputCount(pro);
            var recursiveBatch = this.job.getRecursivePatternBatch(pro.details, this.what);

            while (totalRequestedItems > 0) {
                long times = getRequestedPatternTimes(pro, totalRequestedItems, craftedPerPattern, recursiveBatch);
                this.job.timedCrafting("request-missing-branch " + this.what, () -> {
                    pro.request(inv, times);
                    return null;
                });

                // by now we have succeeded, as request throws an exception in case of failure
                // check how much was actually produced
                var available = extractCraftedBranchOutput(inv, totalRequestedItems);
                if (available != 0) {
                    totalRequestedItems -= available;

                    if (totalRequestedItems <= 0) {
                        return;
                    }
                } else {
                    job.addMissing(this.what, totalRequestedItems);
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

    private long extractAvailableForCraftingInner(CraftingSimulationState inv, long maxAmount)
        throws InterruptedException {
        long available = 0;

        if (!isTopLevelRequestedOutput()) {
            var intermediateFinalOutputMarker = this.job.getIntermediateFinalOutputMarker();
            for (var template : getValidItemTemplates(inv)) {
                long extracted = CraftingCpuHelper.extractTemplates(inv, template, maxAmount - available);
                available += extracted;
                this.job.addIntermediateFinalOutputInput(template.key(), extracted * template.amount());
                if (available >= maxAmount) {
                    return maxAmount;
                }
            }
            if (available < maxAmount && canUsePseudoInputs()) {
                for (var template : getValidItemTemplates(inv)) {
                    long extracted = extractPseudoTemplates(inv, template, maxAmount - available);
                    available += extracted;
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
            if (this.job.canResolveRecursiveRequest(this.what, inv)) {
                long remainingAmount = maxAmount - available;
                if (remainingAmount > 0) {
                    inv.insert(this.what, remainingAmount * this.amount, Actionable.MODULATE);
                    if (this.what.equals(this.job.getOutput())) {
                        this.job.addRecursiveIntermediateFinalOutput(remainingAmount * this.amount);
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
        long totalRequestedItems = (maxAmount - available) * this.amount;
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
            if (!pro.applyReusablePreview(inv, times)) {
                try {
                    this.job.pushMissingSuppression();
                    this.job.timedCrafting("request-input-branch " + this.what, () -> {
                        pro.request(inv, times);
                        return null;
                    });
                } catch (CraftBranchFailure ignored) {
                    continue;
                } finally {
                    this.job.popMissingSuppression();
                }
            }
            long produced = extractCraftedBranchOutput(inv, totalRequestedItems);
            if (produced > 0) {
                totalRequestedItems -= produced;
                available += produced / this.amount;
            }
        }

        return Math.min(maxAmount, available);
    }

    // Only item stacks are supported.
    private void addContainerItems(AEKey template, long multiplier,
                                   @Nullable KeyCounter outputList) {
        if (outputList != null) {
            var containerItem = parentInput.getRemainingKey(template);
            if (containerItem != null) {
                outputList.add(containerItem, multiplier);
            }
        }
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
        var templates = CraftingCpuHelper.getValidItemTemplates(inv, this.parentInput, level);
        this.job.recordPerformanceStage("fuzzy-templates " + this.what, System.nanoTime() - start);
        return templates;
    }

    private boolean canUsePseudoInputs() {
        return this.parent != null && PseudoPatternDetails.isPseudo(this.parent.details);
    }

    private long extractCraftedBranchOutput(CraftingSimulationState inv, long amount) {
        long extracted = inv.extract(this.what, amount, Actionable.MODULATE);
        if (extracted >= amount || !canUsePseudoBranchOutput()) {
            return extracted;
        }
        return extracted + inv.extractPseudo(this.what, amount - extracted, Actionable.MODULATE);
    }

    private boolean canUsePseudoBranchOutput() {
        return isTopLevelRequestedOutput() || canUsePseudoInputs();
    }

    private long extractPseudoTemplates(CraftingSimulationState inv, InputTemplate template, long multiplier) {
        long maxTotal = template.amount() * multiplier;
        long extracted = inv.extractPseudo(template.key(), maxTotal, Actionable.SIMULATE);
        if (extracted == 0) {
            return 0;
        }

        multiplier = extracted / template.amount();
        maxTotal = template.amount() * multiplier;
        if (maxTotal == 0) {
            return 0;
        }

        extracted = inv.extractPseudo(template.key(), maxTotal, Actionable.MODULATE);
        if (extracted == 0 || extracted != maxTotal) {
            throw new IllegalStateException("Failed to correctly extract pseudo templates. Invalid simulation!");
        }
        return multiplier;
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

    private long getRequestedPatternTimes(CraftingTreeProcess pro, long totalRequestedItems, long craftedPerPattern,
                                          CraftingCalculation.RecursivePatternBatch recursiveBatch) {
        if (pro.limitsQuantity()) {
            return 1;
        }
        if (usesFinalOutputAsIntermediateInput(pro)) {
            return (totalRequestedItems + craftedPerPattern - 1) / craftedPerPattern;
        }
        long netOutput = recursiveBatch.netOutput() > 0 ? recursiveBatch.netOutput() : craftedPerPattern;
        long rootTimes = Math.max(1, recursiveBatch.rootTimes());
        return ((totalRequestedItems + netOutput - 1) / netOutput) * rootTimes;
    }

    long getNodeCount() {
        long tot = 1;
        if (this.nodes != null) {
            for (CraftingTreeProcess pro : this.nodes) {
                tot += pro.getNodeCount();
            }
        }
        return tot;
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

    long getPatternNodeCount() {
        long total = this.nodes == null ? 0 : this.nodes.size();
        if (this.nodes != null) {
            for (CraftingTreeProcess pro : this.nodes) {
                total += pro.getPatternNodeCount();
            }
        }
        return total;
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
        if (this.nodes != null) {
            for (CraftingTreeProcess pro : this.nodes) {
                pro.resetPossible();
            }
        }
    }

    public List<CraftingTreeProcess> getNodes() {
        return this.nodes;
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

    long getTemplateAmount() {
        return this.amount;
    }

    private boolean isTopLevelRequestedOutput() {
        return this.parent == null && this.parentInput == null && this.what.equals(this.job.getOutput());
    }
}
