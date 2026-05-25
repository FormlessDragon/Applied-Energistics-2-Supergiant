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
                    this.job.addIntermediateFinalOutput(requestedAmount * this.amount);
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

        var craftedPerPattern = pro.getOutputCount(this.what);
        while (totalRequestedItems > 0) {
            long requestedTimes = pro.limitsQuantity() ? 1 : (totalRequestedItems + craftedPerPattern - 1) / craftedPerPattern;
            long times = this.job.timed("max-craftable " + this.what, () -> pro.getMaximumCraftableTimes(inv, requestedTimes));
            if (times <= 0) {
                pro.possible = false;
                return totalRequestedItems;
            }

            long available;
            if (pro.applyReusablePreview(inv, times)) {
                available = inv.extract(this.what, totalRequestedItems, Actionable.MODULATE);
            } else {
                final ChildCraftingSimulationState child = new ChildCraftingSimulationState(inv);
                try {
                    this.job.pushMissingSuppression();
                    this.job.timedCrafting("request-branch " + this.what, () -> {
                        pro.request(child, times);
                        return null;
                    });
                } catch (CraftBranchFailure failure) {
                    pro.possible = false;
                    return totalRequestedItems;
                } finally {
                    this.job.popMissingSuppression();
                }

                available = child.extract(this.what, totalRequestedItems, Actionable.MODULATE);
                if (available > 0) {
                    child.applyDiff(inv);
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

            var craftedPerPattern = pro.getOutputCount(this.what);

            while (totalRequestedItems > 0) {
                long times;
                if (pro.limitsQuantity()) {
                    times = 1;
                } else {
                    // Craft all at once!
                    times = (totalRequestedItems + craftedPerPattern - 1) / craftedPerPattern;
                }
                this.job.timedCrafting("request-missing-branch " + this.what, () -> {
                    pro.request(inv, times);
                    return null;
                });

                // by now we have succeeded, as request throws an exception in case of failure
                // check how much was actually produced
                var available = inv.extract(this.what, totalRequestedItems, Actionable.MODULATE);
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
            for (var template : getValidItemTemplates(inv)) {
                long extracted = CraftingCpuHelper.extractTemplates(inv, template, maxAmount - available);
                available += extracted;
                if (available >= maxAmount) {
                    return maxAmount;
                }
            }
        }

        if (this.job.isRequesting(this.what)) {
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
            long craftedPerPattern = pro.getOutputCount(this.what);
            long requestedTimes = pro.limitsQuantity() ? 1 : (totalRequestedItems + craftedPerPattern - 1) / craftedPerPattern;
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
            long produced = inv.extract(this.what, totalRequestedItems, Actionable.MODULATE);
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

    AEKey getWhat() {
        return this.what;
    }

    long getTemplateAmount() {
        return this.amount;
    }

    private boolean isTopLevelRequestedOutput() {
        return this.parent == null && this.parentInput == null && this.what.equals(this.job.getOutput());
    }
}
