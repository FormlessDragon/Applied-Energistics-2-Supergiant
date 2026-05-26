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
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.ChildCraftingSimulationState;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * A crafting tree process is what represents a pattern in the crafting process. It has a parent node (its output), and
 * a list of child nodes for its inputs.
 */
public class CraftingTreeProcess {

    final IPatternDetails details;
    private final CraftingTreeNode parent;
    private final CraftingCalculation job;
    // Use linked hashmap to ensure deterministic ordering of subcrafts
    private final Object2LongLinkedOpenHashMap<CraftingTreeNode> nodes = new Object2LongLinkedOpenHashMap<>();
    boolean possible = true;
    private boolean containerItems;
    private Preview reusablePreview;
    /**
     * If true, we perform this pattern by 1 at the time. This ensures that container items or outputs get reused when
     * possible.
     */
    private boolean limitQty;

    public CraftingTreeProcess(ICraftingService cc, CraftingCalculation job,
                               IPatternDetails details,
                               CraftingTreeNode craftingTreeNode) {
        this.parent = craftingTreeNode;
        this.details = details;
        this.job = job;

        updateLimitQty();

        final IPatternDetails.IInput[] inputs = this.details.getInputs();
        for (int x = 0; x < inputs.length; ++x) {
            var input = inputs[x];
            var firstInput = input.possibleInputs()[0];
            this.nodes.put(new CraftingTreeNode(cc, job, firstInput.what(), firstInput.amount(), this, x),
                input.getMultiplier());
        }
    }

    /**
     * @see CraftingTreeNode#notRecursive
     */
    boolean notRecursive(IPatternDetails details) {
        return this.parent == null || this.parent.notRecursive(details);
    }

    /**
     * Check if this pattern has one of its outputs as input. If that's the case, update {@code limitQty} to make sure
     * we simulate this pattern one by one. Also check for container items.
     */
    private void updateLimitQty() {
        // TODO: consider checking substitute inputs as well?
        for (IPatternDetails.IInput input : details.getInputs()) {
            var primaryInput = input.possibleInputs()[0];
            boolean isAnInput = false;

            for (var output : details.getOutputs()) {
                if (output.what().matches(primaryInput)) {
                    isAnInput = true;
                    break;
                }
            }

            if (isAnInput) {
                this.limitQty = true;
            }

            if (input.getRemainingKey(primaryInput.what()) != null) {
                this.limitQty = this.containerItems = true;
            }
        }
    }

    boolean limitsQuantity() {
        return this.limitQty;
    }

    boolean canReusePreview() {
        return this.nodes.size() == 1 && !this.limitQty && !this.containerItems;
    }

    long getMaximumCraftableTimes(CraftingSimulationState inv, long maxTimes)
        throws InterruptedException {
        this.job.handlePausing();

        long start = System.nanoTime();
        long intermediateFinalOutputMarker = this.job.getIntermediateFinalOutputMarker();
        var recursiveMissingSeedsMarker = this.job.getRecursiveMissingSeedsMarker();
        var sharedInputs = new ChildCraftingSimulationState(inv);
        this.job.pushProcess(this);
        try {
            this.reusablePreview = null;
            long craftableTimes = maxTimes;
            if (canReusePreview()) {
                var entry = this.nodes.object2LongEntrySet().getFirst();
                long requiredPerPattern = entry.getLongValue();
                long availableInputs = entry.getKey().extractAvailableForCrafting(sharedInputs,
                    requiredPerPattern * craftableTimes);
                craftableTimes = availableInputs / requiredPerPattern;
                if (craftableTimes > 0) {
                    var recursiveMissingSeeds = this.job.getRecursiveMissingSeedsMarker();
                    recursiveMissingSeeds.removeAll(recursiveMissingSeedsMarker);
                    this.reusablePreview = new Preview(inv, sharedInputs, craftableTimes,
                        this.job.getIntermediateFinalOutputMarker() - intermediateFinalOutputMarker,
                        recursiveMissingSeeds);
                }
                return craftableTimes;
            }

            for (Object2LongMap.Entry<CraftingTreeNode> entry : this.nodes.object2LongEntrySet()) {
                long requiredPerPattern = entry.getLongValue();
                long availableInputs = entry.getKey().extractAvailableForCrafting(sharedInputs,
                    requiredPerPattern * craftableTimes);
                craftableTimes = Math.min(craftableTimes, availableInputs / requiredPerPattern);
                if (craftableTimes == 0) {
                    return 0;
                }
            }
            return craftableTimes;
        } finally {
            this.job.popProcess();
            this.job.restoreIntermediateFinalOutputMarker(intermediateFinalOutputMarker);
            this.job.restoreRecursiveMissingSeedsMarker(recursiveMissingSeedsMarker);
            this.job.recordPerformanceStage("process-max-craftable inputs=" + this.nodes.size(),
                System.nanoTime() - start);
        }
    }

    boolean applyReusablePreview(CraftingSimulationState inv, long times) {
        var preview = this.reusablePreview;
        if (preview == null || preview.parent() != inv || preview.times() != times) {
            return false;
        }

        for (var out : this.details.getOutputs()) {
            preview.state().insert(out.what(), out.amount() * times, Actionable.MODULATE);
        }

        preview.state().addCrafting(details, times);
        preview.state().addBytes(times);
        preview.state().applyDiff(inv);
        if (preview.intermediateFinalOutputAmount() > 0) {
            this.job.addIntermediateFinalOutput(preview.intermediateFinalOutputAmount());
        }
        this.job.addRecursiveMissingSeeds(preview.recursiveMissingSeeds());
        this.reusablePreview = null;
        return true;
    }

    void request(CraftingSimulationState inv, long times)
        throws CraftBranchFailure, InterruptedException {
        this.job.handlePausing();

        this.job.pushProcess(this);
        try {
            var containerItems = this.containerItems ? new KeyCounter() : null;

            // request and remove inputs...
            for (Object2LongMap.Entry<CraftingTreeNode> entry : this.nodes.object2LongEntrySet()) {
                entry.getKey().request(inv, entry.getLongValue() * times, containerItems);
            }

            // by now we must have succeeded, otherwise an exception would have been thrown by request() above

            // add container items
            if (containerItems != null) {
                for (var stack : containerItems) {
                    inv.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
                    inv.addStackBytes(stack.getKey(), stack.getLongValue(), 1);
                }
            }

            // add crafting results.
            for (var out : this.details.getOutputs()) {
                inv.insert(out.what(), out.amount() * times, Actionable.MODULATE);
            }

            inv.addCrafting(details, times);
            inv.addBytes(times);
        } finally {
            this.job.popProcess();
        }
    }

    long getNodeCount() {
        long tot = 0;

        for (CraftingTreeNode node : this.nodes.keySet()) {
            tot += node.getNodeCount();
        }

        return tot;
    }

    int getDepth() {
        int depth = 1;
        for (CraftingTreeNode node : this.nodes.keySet()) {
            depth = Math.max(depth, 1 + node.getDepth());
        }
        return depth;
    }

    long getPatternNodeCount() {
        long total = 0;
        for (CraftingTreeNode node : this.nodes.keySet()) {
            total += node.getPatternNodeCount();
        }
        return total;
    }

    long getOutputCount(AEKey what) {
        long tot = 0;

        for (var is : this.details.getOutputs()) {
            if (what.matches(is)) {
                tot += is.amount();
            }
        }

        return tot;
    }

    long getEffectiveOutputCount(AEKey what) {
        long output = getOutputCount(what);
        long input = getInputCount(what);
        if (input <= 0 || output <= input) {
            return output;
        }
        return output - input;
    }

    long getInputCount(AEKey what) {
        long total = 0;

        for (var input : this.details.getInputs()) {
            for (var possibleInput : input.possibleInputs()) {
                if (what.matches(possibleInput)) {
                    total += possibleInput.amount() * input.getMultiplier();
                    break;
                }
            }
        }

        return total;
    }

    AEKey getFirstInputKey() {
        var inputs = this.details.getInputs();
        if (inputs.length == 0) {
            return null;
        }
        var possibleInputs = inputs[0].possibleInputs();
        if (possibleInputs.length == 0) {
            return null;
        }
        return possibleInputs[0].what();
    }

    long getFirstInputAmount() {
        var inputs = this.details.getInputs();
        if (inputs.length == 0) {
            return 0;
        }
        var possibleInputs = inputs[0].possibleInputs();
        if (possibleInputs.length == 0) {
            return 0;
        }
        return possibleInputs[0].amount() * inputs[0].getMultiplier();
    }

    void accumulateNet(KeyCounter netByKey) {
        for (var output : this.details.getOutputs()) {
            netByKey.add(output.what(), output.amount());
        }
        for (Object2LongMap.Entry<CraftingTreeNode> entry : this.nodes.object2LongEntrySet()) {
            var node = entry.getKey();
            netByKey.add(node.getWhat(), -node.getTemplateAmount() * entry.getLongValue());
        }
    }

    boolean hasMultiplePaths() {
        for (Object2LongMap.Entry<CraftingTreeNode> entry : nodes.object2LongEntrySet()) {
            if (entry.getKey().hasMultiplePaths()) {
                return true;
            }
        }
        return false;
    }

    void resetPossible() {
        this.possible = true;
        this.reusablePreview = null;
        for (CraftingTreeNode node : this.nodes.keySet()) {
            node.resetPossible();
        }
    }

    private record Preview(CraftingSimulationState parent, ChildCraftingSimulationState state, long times,
                           long intermediateFinalOutputAmount, KeyCounter recursiveMissingSeeds) {
    }

    public IPatternDetails getDetails() {
        return this.details;
    }

    public Object2LongLinkedOpenHashMap<CraftingTreeNode> getNodes() {
        return nodes;
    }
}
