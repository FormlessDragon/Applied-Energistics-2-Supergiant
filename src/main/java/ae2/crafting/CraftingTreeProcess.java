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
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.crafting.execution.CraftingSupplierLocator;
import ae2.crafting.inv.ChildCraftingSimulationState;
import ae2.crafting.inv.CraftingSimulationState;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.patternprovider.PatternContainer;
import ae2.helpers.patternprovider.PatternProviderLogic;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import ae2.me.service.CraftingService;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A crafting tree process is what represents a pattern in the crafting process. It has a parent node (its output), and
 * a list of child nodes for its inputs.
 */
public class CraftingTreeProcess {

    final IPatternDetails details;
    private static final Comparator<PatternContainerGroup> MACHINE_GROUP_COMPARATOR =
        Comparator.comparing(group -> group.name().getFormattedText().toLowerCase(Locale.ROOT));
    private final List<PatternContainerGroup> machineGroups;
    private final CraftingTreeNode parent;
    private final CraftingCalculation job;
    // Use linked hashmap to ensure deterministic ordering of subcrafts
    private final Object2LongLinkedOpenHashMap<CraftingTreeNode> nodes = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongLinkedOpenHashMap<CraftingTreeNode> treeInputDisplayAmounts =
        new Object2LongLinkedOpenHashMap<>();
    boolean possible = true;
    private boolean containerItems;
    private Preview reusablePreview;
    private final Map<PatternContainerGroup, List<CraftingSupplierLocation>> machineLocations;
    private long treeRequestTimes;
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
        MachineInfo machineInfo = collectMachineInfo(cc, details);
        this.machineGroups = machineInfo.groups();
        this.machineLocations = machineInfo.locations();

        updateLimitQty();

        final IPatternDetails.IInput[] inputs = this.details.getInputs();
        for (int x = 0; x < inputs.length; ++x) {
            var input = inputs[x];
            var firstInput = input.possibleInputs()[0];
            this.nodes.put(new CraftingTreeNode(cc, job, firstInput.what(), firstInput.amount(), this, x),
                input.getMultiplier());
        }
    }

    private static MachineInfo collectMachineInfo(ICraftingService craftingService,
                                                  IPatternDetails details) {
        if (!(craftingService instanceof CraftingService service)) {
            return new MachineInfo(List.of(), Map.of());
        }

        Map<PatternContainerGroup, LinkedHashSet<CraftingSupplierLocation>> locationsByGroup = new LinkedHashMap<>();
        for (ICraftingProvider provider : service.getProvidersSnapshot(details)) {
            PatternContainerGroup group = getMachineGroup(provider);
            if (group != null) {
                locationsByGroup.computeIfAbsent(group, ignored -> new LinkedHashSet<>());
                CraftingSupplierLocation location = getMachineLocation(service.getGrid(), provider);
                if (location != null) {
                    locationsByGroup.get(group).add(location);
                }
            }
        }
        List<PatternContainerGroup> groups = locationsByGroup.keySet().stream()
                                                             .sorted(MACHINE_GROUP_COMPARATOR)
                                                             .toList();
        Map<PatternContainerGroup, List<CraftingSupplierLocation>> locations = new LinkedHashMap<>();
        for (PatternContainerGroup group : groups) {
            locations.put(group, List.copyOf(locationsByGroup.getOrDefault(group, new LinkedHashSet<>())));
        }
        return new MachineInfo(groups, locations);
    }

    @Nullable
    private static PatternContainerGroup getMachineGroup(ICraftingProvider provider) {
        if (provider instanceof TemporaryPseudoCraftingProvider) {
            return null;
        }
        if (provider instanceof PatternProviderLogic logic) {
            return logic.getTerminalGroup();
        }
        if (provider instanceof PatternContainer container) {
            return container.getTerminalGroup();
        }
        return PatternContainerGroup.nothing();
    }

    @Nullable
    private static CraftingSupplierLocation getMachineLocation(IGrid grid, ICraftingProvider provider) {
        if (provider instanceof TemporaryPseudoCraftingProvider) {
            return null;
        }
        return CraftingSupplierLocator.resolveLocation(grid, provider);
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

    private static KeyCounter copyPositiveDelta(KeyCounter after, KeyCounter before) {
        var delta = new KeyCounter();
        for (var entry : after) {
            long valueDelta = entry.getLongValue() - before.get(entry.getKey());
            if (valueDelta > 0) {
                delta.add(entry.getKey(), valueDelta);
            }
        }
        return delta;
    }

    long getMaximumCraftableTimes(CraftingSimulationState inv, long maxTimes)
        throws InterruptedException {
        this.job.handlePausing();

        long start = System.nanoTime();
        long intermediateFinalOutputMarker = this.job.getIntermediateFinalOutputMarker();
        var recursiveMissingSeedsMarker = this.job.getRecursiveMissingSeedsMarker();
        var realSeededRecursiveRequestsMarker = this.job.getRealSeededRecursiveRequestsMarker();
        var realRecursiveSeedsMarker = this.job.getRealRecursiveSeedsMarker();
        var realSeededRecursiveKeysMarker = this.job.getRealSeededRecursiveKeysMarker();
        var recursiveDisplayRequestsMarker = this.job.getRecursiveDisplayRequestsMarker();
        var sharedInputs = new ChildCraftingSimulationState(inv);
        this.job.pushProcess(this);
        try {
            this.reusablePreview = null;
            long craftableTimes = maxTimes;
            if (canReusePreview()) {
                var entry = this.nodes.object2LongEntrySet().getFirst();
                long requiredPerPattern = entry.getLongValue();
                long availableInputs = entry.getKey().extractAvailableForCrafting(sharedInputs,
                    LongMath.saturatedMultiply(requiredPerPattern, craftableTimes));
                craftableTimes = availableInputs / requiredPerPattern;
                if (craftableTimes > 0) {
                    var recursiveMissingSeeds = copyPositiveDelta(this.job.getRecursiveMissingSeedsMarker(),
                        recursiveMissingSeedsMarker);
                    var clearedRecursiveMissingSeeds = copyPositiveDelta(recursiveMissingSeedsMarker,
                        this.job.getRecursiveMissingSeedsMarker());
                    var realSeededRecursiveRequests = this.job.getRealSeededRecursiveRequestsMarker();
                    realSeededRecursiveRequests.removeAll(realSeededRecursiveRequestsMarker);
                    var realRecursiveSeeds = this.job.getRealRecursiveSeedsMarker();
                    realRecursiveSeeds.removeAll(realRecursiveSeedsMarker);
                    var realSeededRecursiveKeys = this.job.getRealSeededRecursiveKeysMarker();
                    realSeededRecursiveKeys.removeAll(realSeededRecursiveKeysMarker);
                    this.reusablePreview = new Preview(inv, sharedInputs, craftableTimes,
                        this.job.getIntermediateFinalOutputMarker() - intermediateFinalOutputMarker,
                        recursiveMissingSeeds, clearedRecursiveMissingSeeds, realSeededRecursiveRequests,
                        realRecursiveSeeds,
                        realSeededRecursiveKeys,
                        this.job.getRecursiveDisplayRequestsDelta(recursiveDisplayRequestsMarker));
                }
                return craftableTimes;
            }

            for (Object2LongMap.Entry<CraftingTreeNode> entry : this.nodes.object2LongEntrySet()) {
                long requiredPerPattern = entry.getLongValue();
                long availableInputs = entry.getKey().extractAvailableForCrafting(sharedInputs,
                    LongMath.saturatedMultiply(requiredPerPattern, craftableTimes));
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
            this.job.restoreRealSeededRecursiveRequestsMarker(realSeededRecursiveRequestsMarker);
            this.job.restoreRealRecursiveSeedsMarker(realRecursiveSeedsMarker);
            this.job.restoreRealSeededRecursiveKeysMarker(realSeededRecursiveKeysMarker);
            this.job.restoreRecursiveDisplayRequestsMarker(recursiveDisplayRequestsMarker);
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
            long outputAmount = LongMath.saturatedMultiply(out.amount(), times);
            if (isFinalOutputPseudoPattern()) {
                preview.state().insertPseudo(out.what(), outputAmount, Actionable.MODULATE);
            } else {
                preview.state().insert(out.what(), outputAmount, Actionable.MODULATE);
            }
        }

        preview.state().addCrafting(details, times);
        preview.state().addBytes(times);
        preview.state().applyDiff(inv);
        if (preview.intermediateFinalOutputAmount() > 0) {
            this.job.addIntermediateFinalOutput(preview.intermediateFinalOutputAmount());
        }
        this.job.applyRecursiveMissingSeedPreview(preview.clearedRecursiveMissingSeeds(),
            preview.recursiveMissingSeeds());
        this.job.addRealSeededRecursiveRequests(preview.realSeededRecursiveRequests());
        this.job.addRealRecursiveSeeds(preview.realRecursiveSeeds());
        this.job.addRealSeededRecursiveKeys(preview.realSeededRecursiveKeys());
        this.job.addRecursiveDisplayRequests(preview.recursiveDisplayRequestsDelta());
        this.reusablePreview = null;
        return true;
    }

    long getReusablePreviewRecursiveMissingSeedAmount(AEKey what) {
        return this.reusablePreview == null ? 0 : this.reusablePreview.recursiveMissingSeeds().get(what);
    }

    void applyReusablePreviewRecursiveMissingSeedsOnly() {
        var preview = this.reusablePreview;
        if (preview == null) {
            return;
        }

        this.job.applyRecursiveMissingSeedPreview(preview.clearedRecursiveMissingSeeds(),
            preview.recursiveMissingSeeds());
        this.reusablePreview = null;
    }

    void request(CraftingSimulationState inv, long times)
        throws CraftBranchFailure, InterruptedException {
        this.job.handlePausing();

        this.job.pushProcess(this);
        try {
            var containerItems = this.containerItems ? new KeyCounter() : null;
            long intermediateFinalOutputMarker = this.job.getIntermediateFinalOutputMarker();

            // request and remove inputs...
            requestInputs(inv, times, containerItems, null);

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
                long outputAmount = LongMath.saturatedMultiply(out.amount(), times);
                if (isFinalOutputPseudoPattern()) {
                    inv.insertPseudo(out.what(), outputAmount, Actionable.MODULATE);
                } else {
                    inv.insert(out.what(), outputAmount, Actionable.MODULATE);
                }
            }

            inv.addCrafting(details, times);
            inv.addBytes(times);
            requestIntermediateFinalOutputReplacements(inv,
                this.job.getIntermediateFinalOutputMarker() - intermediateFinalOutputMarker);
        } finally {
            this.job.popProcess();
        }
    }

    private void requestInputs(CraftingSimulationState inv, long times, @Nullable KeyCounter containerItems,
                               @Nullable AEKey skippedInput)
        throws CraftBranchFailure, InterruptedException {
        for (Object2LongMap.Entry<CraftingTreeNode> entry : this.nodes.object2LongEntrySet()) {
            var node = entry.getKey();
            if (node.getWhat().equals(skippedInput)) {
                continue;
            }
            var requiredExtractMarker = inv.getRequiredExtractMarker();
            var missingItemsMarker = this.job.getMissingItemsMarker();
            node.request(inv, LongMath.saturatedMultiply(entry.getLongValue(), times), containerItems);
            recordTreeInputDisplayAmount(inv, node, requiredExtractMarker, missingItemsMarker);
        }
    }

    private void requestIntermediateFinalOutputReplacements(CraftingSimulationState inv, long intermediateAmount)
        throws CraftBranchFailure, InterruptedException {
        long outputCount = getOutputCount(this.job.getOutput());
        if (intermediateAmount <= 0 || outputCount <= 0 || !this.parent.getWhat().equals(this.job.getOutput())) {
            return;
        }
        var skippedInput = getSkippedRecursiveFinalOutputInput();
        if (skippedInput == null || this.nodes.size() <= 1) {
            return;
        }

        long extraTimes = (intermediateAmount + outputCount - 1) / outputCount;
        long marker = this.job.getIntermediateFinalOutputMarker();
        requestInputs(inv, extraTimes, null, skippedInput);
        this.job.restoreIntermediateFinalOutputMarker(marker);
        inv.addCrafting(details, extraTimes);
        inv.addBytes(extraTimes);
    }

    @Nullable
    private AEKey getSkippedRecursiveFinalOutputInput() {
        for (CraftingTreeNode node : this.nodes.keySet()) {
            if (this.job.isRecursiveFinalOutputInput(node.getWhat())) {
                return node.getWhat();
            }
        }
        return null;
    }

    void addTreeRequestTimes(long times) {
        this.treeRequestTimes += times;
    }

    private void recordTreeInputDisplayAmount(CraftingSimulationState inv, CraftingTreeNode node,
                                              KeyCounter requiredExtractMarker, KeyCounter missingItemsMarker) {
        if (!node.hasSelfReturningRemainderInput()) {
            return;
        }

        var key = node.getWhat();
        long extractedDelta = inv.getRequiredExtractAmount(key) - requiredExtractMarker.get(key);
        long missingDelta = this.job.getMissingItems().get(key) - missingItemsMarker.get(key);
        long delta = Math.max(0, extractedDelta) + Math.max(0, missingDelta);
        long previous = this.treeInputDisplayAmounts.getLong(node);
        this.treeInputDisplayAmounts.put(node, previous + delta);
    }

    public boolean hasTreeInputDisplayAmount(CraftingTreeNode node) {
        return this.treeInputDisplayAmounts.containsKey(node);
    }

    public long getTreeInputDisplayAmount(CraftingTreeNode node) {
        return this.treeInputDisplayAmounts.getLong(node);
    }

    public long getTreeDisplayTimes() {
        return treeRequestTimes;
    }

    public long getTreeRequestTimes() {
        return treeRequestTimes;
    }

    private boolean isFinalOutputPseudoPattern() {
        return PseudoPatternDetails.isPseudo(this.details)
            && PseudoPatternDetails.unwrap(this.details) instanceof AEProcessingPattern
            && getOutputCount(this.job.getOutput()) > 0;
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
                    total = LongMath.saturatedAdd(total,
                        LongMath.saturatedMultiply(possibleInput.amount(), input.getMultiplier()));
                    break;
                }
            }
        }

        return total;
    }

    void accumulateNet(KeyCounter netByKey) {
        for (var output : this.details.getOutputs()) {
            netByKey.add(output.what(), output.amount());
        }
        for (Object2LongMap.Entry<CraftingTreeNode> entry : this.nodes.object2LongEntrySet()) {
            var node = entry.getKey();
            netByKey.add(node.getWhat(),
                LongMath.saturatedSubtract(0, LongMath.saturatedMultiply(node.getTemplateAmount(),
                    entry.getLongValue())));
        }
    }

    void accumulateInputKeys(Set<AEKey> inputKeys) {
        for (CraftingTreeNode node : this.nodes.keySet()) {
            inputKeys.add(node.getWhat());
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
        this.treeRequestTimes = 0;
        this.treeInputDisplayAmounts.clear();
        for (CraftingTreeNode node : this.nodes.keySet()) {
            node.resetPossible();
        }
    }

    public IPatternDetails getDetails() {
        return this.details;
    }

    public List<PatternContainerGroup> getMachineGroups() {
        return machineGroups;
    }

    public Map<PatternContainerGroup, List<CraftingSupplierLocation>> getMachineLocations() {
        return machineLocations;
    }

    private record MachineInfo(List<PatternContainerGroup> groups,
                               Map<PatternContainerGroup, List<CraftingSupplierLocation>> locations) {
    }

    public Object2LongLinkedOpenHashMap<CraftingTreeNode> getNodes() {
        return nodes;
    }
    private record Preview(CraftingSimulationState parent, ChildCraftingSimulationState state, long times,
                           long intermediateFinalOutputAmount, KeyCounter recursiveMissingSeeds,
                           KeyCounter clearedRecursiveMissingSeeds,
                           Set<AEKey> realSeededRecursiveRequests,
                           Set<AEKey> realRecursiveSeeds,
                           Set<AEKey> realSeededRecursiveKeys,
                           Reference2LongMap<CraftingTreeNode> recursiveDisplayRequestsDelta) {
    }
}
