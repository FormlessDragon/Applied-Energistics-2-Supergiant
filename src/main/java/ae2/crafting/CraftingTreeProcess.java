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
import ae2.api.crafting.IAssemblerPattern;
import ae2.api.crafting.IPatternDetails;
import ae2.api.implementations.blockentities.PatternContainerGroup;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.crafting.inv.ChildCraftingSimulationState;
import ae2.crafting.inv.CraftingSimulationState;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import ae2.me.service.CraftingService;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;

/**
 * A crafting tree process is what represents a pattern in the crafting process. It has a parent node (its output), and
 * a list of child nodes for its inputs.
 */
public class CraftingTreeProcess {

    final IPatternDetails details;
    private static final Comparator<PatternContainerGroup> MACHINE_GROUP_COMPARATOR =
        Comparator.comparing(group -> group.name().getFormattedText().toLowerCase(Locale.ROOT));
    private @Nullable MachineInfo machineInfo;
    private final CraftingTreeNode parent;
    private final CraftingCalculation job;
    private final IPatternDetails.IInput[] inputs;
    private final List<GenericStack> outputs;
    // Planning uses slotNodes directly; only display consumers need a keyed, ordered view.
    private volatile @Nullable Object2LongMap<CraftingTreeNode> nodes;
    private @Nullable Object2LongOpenHashMap<CraftingTreeNode> treeInputDisplayAmounts;
    boolean possible = true;
    private boolean containerItems;
    private Preview reusablePreview;
    private long treeRequestTimes;
    /**
     * If true, we perform this pattern by 1 at the time. This ensures that container items or outputs get reused when
     * possible.
     */
    private boolean limitQty;
    /*
     * Per input slot: how much of a catalyst one craft gives back, and the container a precise input leaves. Both only
     * have to be there for the first craft of a batch, the later ones take back what the earlier ones returned.
     */
    private final CraftingTreeNode[] slotNodes;
    private long[] catalystReturns;
    private @Nullable AEKey[] slotContainers;
    private boolean batchedSlots;

    public CraftingTreeProcess(ICraftingService cc, CraftingCalculation job,
                               IPatternDetails details,
                               CraftingTreeNode craftingTreeNode) {
        this.parent = craftingTreeNode;
        this.details = details;
        this.job = job;
        this.inputs = details.getInputs();
        var patternOutputs = details.getOutputs();
        this.outputs = patternOutputs instanceof RandomAccess ? patternOutputs : new ObjectArrayList<>(patternOutputs);
        this.slotNodes = new CraftingTreeNode[this.inputs.length];

        for (int x = 0; x < this.inputs.length; ++x) {
            var input = this.inputs[x];
            var firstInput = input.possibleInputs()[0];
            var node = new CraftingTreeNode(cc, job, firstInput.what(), firstInput.amount(), this, x);
            this.slotNodes[x] = node;
        }
        updateLimitQty();
    }

    static MachineInfo collectMachineInfo(CraftingCalculation calculation, ICraftingService craftingService,
                                          IPatternDetails details) {
        if (!(craftingService instanceof CraftingService service)) {
            return new MachineInfo(List.of(), Map.of());
        }

        var providers = service.getProvidersSnapshot(details);
        if (providers.isEmpty()) return new MachineInfo(List.of(), Map.of());
        Map<PatternContainerGroup, ObjectLinkedOpenHashSet<CraftingSupplierLocation>> locationsByGroup =
            new Object2ObjectLinkedOpenHashMap<>(1);
        var grid = service.getGrid();
        for (int i = 0, size = providers.size(); i < size; i++) {
            var provider = providers.get(i);
            PatternContainerGroup group = getMachineGroup(provider);
            if (group != null) {
                locationsByGroup.computeIfAbsent(group, ignored -> new ObjectLinkedOpenHashSet<>(1));
                CraftingSupplierLocation location = getMachineLocation(calculation, grid, provider);
                if (location != null) {
                    locationsByGroup.get(group).add(location);
                }
            }
        }
        var sourceGroups = new ObjectArrayList<>(locationsByGroup.keySet());
        sourceGroups.sort(MACHINE_GROUP_COMPARATOR);
        var groups = new ObjectArrayList<PatternContainerGroup>(sourceGroups.size());
        Map<PatternContainerGroup, List<CraftingSupplierLocation>> locations =
            new Object2ObjectLinkedOpenHashMap<>(sourceGroups.size());
        for (int i = 0, size = sourceGroups.size(); i < size; i++) {
            var sourceGroup = sourceGroups.get(i);
            var tooltip = sourceGroup.tooltip().stream().map(ITextComponent::createCopy)
                                     .collect(ObjectArrayList.toList());
            var group = new PatternContainerGroup(sourceGroup.icon(), sourceGroup.name().createCopy(), tooltip);
            groups.add(group);
            locations.put(group,
                Collections.unmodifiableList(new ObjectArrayList<>(locationsByGroup.get(sourceGroup))));
        }
        return new MachineInfo(Collections.unmodifiableList(groups), Collections.unmodifiableMap(locations));
    }

    @Nullable
    private static PatternContainerGroup getMachineGroup(ICraftingProvider provider) {
        if (provider instanceof TemporaryPseudoCraftingProvider) {
            return null;
        }
        var g = provider.getTerminalGroup();
        if (g != null) {
            return g;
        }
        return PatternContainerGroup.nothing();
    }

    @Nullable
    private static CraftingSupplierLocation getMachineLocation(CraftingCalculation calculation, IGrid grid,
                                                               ICraftingProvider provider) {
        if (provider instanceof TemporaryPseudoCraftingProvider) {
            return null;
        }
        return calculation.resolveMachineLocation(grid, provider);
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
     *
     * <p>The catalyst of a precise input, which the pattern gives back, and the container a precise input leaves are
     * batched instead, see {@link #slotUnits}: one craft at a time, the calculation costs as much as the requested
     * amount. What the request itself is made of, and inputs with several possible stacks, whose choice decides what
     * comes back, are still crafted one at a time.</p>
     */
    private void updateLimitQty() {
        var requested = this.parent == null ? null : this.parent.getWhat();
        // With substitutions, the variant a craft takes decides what it leaves
        boolean substitutes = PseudoPatternDetails.unwrap(this.details) instanceof IAssemblerPattern assembler
            && (assembler.canSubstitute() || assembler.canSubstituteFluids());
        for (int x = 0; x < this.inputs.length; x++) {
            var input = this.inputs[x];
            var primaryInput = input.possibleInputs()[0];
            var key = primaryInput.what();
            boolean precise = input.possibleInputs().length == 1 && !substitutes;
            if (!precise) {
                // A later alternative may return a container even when the encoded/default input does not (for
                // example, fluid versus a filled bucket). Its actual choice determines the next craft's inventory.
                for (var possible : input.possibleInputs()) {
                    if (input.getRemainingKey(possible.what()) != null) {
                        this.containerItems = true;
                        this.limitQty = true;
                    }
                    if (getOutputCount(possible.what()) > 0) {
                        this.limitQty = true;
                    }
                }
            }
            long returned = getOutputCount(key);
            var container = input.getRemainingKey(key);
            if (container != null) {
                this.containerItems = true;
            }

            if (returned > 0) {
                if (precise && container == null && !key.equals(requested) && countSlots(key) == 1) {
                    if (this.catalystReturns == null) this.catalystReturns = new long[this.inputs.length];
                    this.catalystReturns[x] = returned;
                    this.batchedSlots = true;
                } else {
                    this.limitQty = true;
                }
            } else if (container != null) {
                if (precise) {
                    if (this.slotContainers == null) this.slotContainers = new AEKey[this.inputs.length];
                    this.slotContainers[x] = container;
                    this.batchedSlots = true;
                } else {
                    this.limitQty = true;
                }
            }
        }
    }

    private int countSlots(AEKey key) {
        int count = 0;
        for (var input : this.inputs) {
            if (input.possibleInputs()[0].what().equals(key)) {
                count++;
            }
        }
        return count;
    }

    /**
     * How much of the input of a slot a batch of crafts requests, in units of its node.
     */
    private long slotUnits(int slot, long multiplier, long times) {
        if (this.catalystReturns == null || this.catalystReturns[slot] <= 0 || times <= 0) {
            return LongMath.saturatedMultiply(multiplier, times);
        }
        long unit = this.inputs[slot].possibleInputs()[0].amount();
        long perCraft = LongMath.saturatedMultiply(unit, multiplier);
        long lost = Math.max(0, perCraft - this.catalystReturns[slot]);
        long items = LongMath.saturatedAdd(perCraft, LongMath.saturatedMultiply(lost, times - 1));
        return LongMath.saturatedAdd(items / unit, items % unit == 0 ? 0 : 1);
    }

    /**
     * The most crafts of a batch that the given units of the input of a slot allow.
     */
    private long slotTimes(int slot, long multiplier, long units) {
        if (this.catalystReturns == null || this.catalystReturns[slot] <= 0) {
            return units / multiplier;
        }
        long unit = this.inputs[slot].possibleInputs()[0].amount();
        long perCraft = LongMath.saturatedMultiply(unit, multiplier);
        long items = LongMath.saturatedMultiply(units, unit);
        if (items < perCraft) {
            return 0;
        }
        long lost = perCraft - this.catalystReturns[slot];
        return lost <= 0 ? Long.MAX_VALUE : LongMath.saturatedAdd(1, (items - perCraft) / lost);
    }

    /**
     * The catalyst of a slot left after a batch, from what the batch requested and what its crafts gave back.
     */
    private long catalystReturned(int slot, long times) {
        long unit = this.inputs[slot].possibleInputs()[0].amount();
        long multiplier = this.inputs[slot].getMultiplier();
        long perCraft = LongMath.saturatedMultiply(unit, multiplier);
        long requested = LongMath.saturatedMultiply(slotUnits(slot, multiplier, times), unit);
        long net = LongMath.saturatedMultiply(this.catalystReturns[slot] - perCraft, times);
        return Math.max(0, LongMath.saturatedAdd(requested, net));
    }

    private boolean isCatalyst(AEKey key) {
        if (this.catalystReturns == null) return false;
        for (int x = 0; x < this.inputs.length; x++) {
            if (this.catalystReturns[x] > 0 && this.slotNodes[x].getWhat().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the containers the earlier crafts of a batch leave for the later ones into the inventory.
     */
    private @Nullable KeyCounter creditContainers(CraftingSimulationState inv, long times) {
        if (times <= 1 || this.slotContainers == null) {
            return null;
        }
        KeyCounter credited = null;
        for (int x = 0; x < this.inputs.length; x++) {
            var container = this.slotContainers[x];
            if (container == null) continue;
            long amount = LongMath.saturatedMultiply(this.inputs[x].getMultiplier(), times - 1);
            inv.insert(container, amount, Actionable.MODULATE);
            if (credited == null) credited = new KeyCounter();
            credited.add(container, amount);
        }
        return credited;
    }

    boolean limitsQuantity() {
        return this.limitQty;
    }

    boolean canReusePreview() {
        return !this.job.isLocalUnitMode() && !this.limitQty && !this.containerItems && !this.batchedSlots;
    }

    long getMaximumCraftableTimes(CraftingSimulationState inv, long maxTimes)
        throws InterruptedException {
        this.job.handlePausing();

        boolean trackPerformance = this.job.isPerformanceTrackingEnabled();
        long start = trackPerformance ? System.nanoTime() : 0;
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
            if (canReusePreview() && this.slotNodes.length == 1) {
                long requiredPerPattern = this.inputs[0].getMultiplier();
                long availableInputs = this.slotNodes[0].extractAvailableForCrafting(sharedInputs,
                    LongMath.saturatedMultiply(requiredPerPattern, craftableTimes));
                craftableTimes = availableInputs / requiredPerPattern;
                // Availability may include an incomplete final craft. Reusing its diff would consume that
                // remainder as well, hiding usable stock from subsequent crafts and overstating missing inputs.
                if (craftableTimes > 0 && availableInputs % requiredPerPattern == 0) {
                    cacheReusablePreview(inv, sharedInputs, craftableTimes, intermediateFinalOutputMarker,
                        recursiveMissingSeedsMarker, realSeededRecursiveRequestsMarker, realRecursiveSeedsMarker,
                        realSeededRecursiveKeysMarker, recursiveDisplayRequestsMarker);
                }
                return craftableTimes;
            }

            if (this.batchedSlots) {
                return getBatchCraftableTimes(inv, maxTimes, () -> {
                    this.job.restoreIntermediateFinalOutputMarker(intermediateFinalOutputMarker);
                    this.job.restoreRecursiveMissingSeedsMarker(recursiveMissingSeedsMarker);
                    this.job.restoreRealSeededRecursiveRequestsMarker(realSeededRecursiveRequestsMarker);
                    this.job.restoreRealRecursiveSeedsMarker(realRecursiveSeedsMarker);
                    this.job.restoreRealSeededRecursiveKeysMarker(realSeededRecursiveKeysMarker);
                    this.job.restoreRecursiveDisplayRequestsMarker(recursiveDisplayRequestsMarker);
                });
            }
            for (int slot = 0; slot < this.slotNodes.length; slot++) {
                long requiredPerPattern = this.inputs[slot].getMultiplier();
                long availableInputs = this.slotNodes[slot].extractAvailableForCrafting(sharedInputs,
                    LongMath.saturatedMultiply(requiredPerPattern, craftableTimes));
                craftableTimes = Math.min(craftableTimes, availableInputs / requiredPerPattern);
                if (craftableTimes == 0) {
                    return 0;
                }
            }
            // A later input may have reduced the batch after earlier inputs were already taken for maxTimes.
            // That preview would consume too much; request the smaller batch afresh instead of applying its diff.
            if (canReusePreview() && craftableTimes > 0 && craftableTimes == maxTimes) {
                cacheReusablePreview(inv, sharedInputs, craftableTimes, intermediateFinalOutputMarker,
                    recursiveMissingSeedsMarker, realSeededRecursiveRequestsMarker, realRecursiveSeedsMarker,
                    realSeededRecursiveKeysMarker, recursiveDisplayRequestsMarker);
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
            if (trackPerformance) {
                this.job.recordPerformanceStage("process-max-craftable inputs=" + this.slotNodes.length,
                    System.nanoTime() - start);
            }
        }
    }

    /**
     * The credited containers depend on the batch size, so a smaller batch is checked again until it holds.
     */
    private long getBatchCraftableTimes(CraftingSimulationState inv, long maxTimes, Runnable restoreMarkers)
        throws InterruptedException {
        long times = maxTimes;
        for (int attempt = 0; attempt < 8; attempt++) {
            long craftable = batchCraftableTimes(inv, times);
            if (craftable >= times || craftable == 0) {
                return Math.min(craftable, times);
            }
            restoreMarkers.run();
            times = craftable;
        }
        return Math.min(1, batchCraftableTimes(inv, 1));
    }

    private long batchCraftableTimes(CraftingSimulationState inv, long times) throws InterruptedException {
        var sharedInputs = new ChildCraftingSimulationState(inv);
        creditContainers(sharedInputs, times);
        long craftable = times;
        for (int x = 0; x < this.slotNodes.length; x++) {
            long multiplier = this.inputs[x].getMultiplier();
            long available = this.slotNodes[x].extractAvailableForCrafting(sharedInputs,
                slotUnits(x, multiplier, craftable));
            craftable = Math.min(craftable, slotTimes(x, multiplier, available));
            if (craftable == 0) {
                return 0;
            }
        }
        return craftable;
    }

    private void cacheReusablePreview(CraftingSimulationState parent, ChildCraftingSimulationState state, long times,
                                      long intermediateFinalOutputMarker, KeyCounter recursiveMissingSeedsMarker,
                                      Set<AEKey> realSeededRecursiveRequestsMarker, Set<AEKey> realRecursiveSeedsMarker,
                                      Set<AEKey> realSeededRecursiveKeysMarker,
                                      Reference2LongMap<CraftingTreeNode> recursiveDisplayRequestsMarker) {
        var recursiveMissingSeeds = this.job.getAddedRecursiveMissingSeeds(recursiveMissingSeedsMarker);
        var clearedRecursiveMissingSeeds = this.job.getClearedRecursiveMissingSeeds(recursiveMissingSeedsMarker);
        var realSeededRecursiveRequests = this.job.getRealSeededRecursiveRequestsDelta(realSeededRecursiveRequestsMarker);
        var realRecursiveSeeds = this.job.getRealRecursiveSeedsDelta(realRecursiveSeedsMarker);
        var realSeededRecursiveKeys = this.job.getRealSeededRecursiveKeysDelta(realSeededRecursiveKeysMarker);
        this.reusablePreview = new Preview(parent, state, times,
            this.job.getIntermediateFinalOutputMarker() - intermediateFinalOutputMarker,
            recursiveMissingSeeds, clearedRecursiveMissingSeeds, realSeededRecursiveRequests,
            realRecursiveSeeds, realSeededRecursiveKeys,
            this.job.getRecursiveDisplayRequestsDelta(recursiveDisplayRequestsMarker));
    }

    boolean applyReusablePreview(CraftingSimulationState inv, long times) {
        var preview = this.reusablePreview;
        if (preview == null || preview.parent() != inv || preview.times() != times) {
            return false;
        }

        for (int i = 0, size = this.outputs.size(); i < size; i++) {
            var out = this.outputs.get(i);
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
            var credited = creditContainers(inv, times);

            // request and remove inputs...
            requestInputs(inv, times, containerItems, null);

            // by now we must have succeeded, otherwise an exception would have been thrown by request() above

            // add container items, except those already credited to the batch
            if (containerItems != null) {
                containerItems.forEach((entryKey, entryAmount) -> {
                    long amount = entryAmount;
                    if (credited != null) {
                        amount -= credited.get(entryKey);
                    }
                    if (amount > 0) {
                        inv.insert(entryKey, amount, Actionable.MODULATE);
                    }
                    inv.addStackBytes(entryKey, entryAmount, 1);
                });
            }
            if (credited != null) {
                withdrawUnreturnedCredit(inv, credited, containerItems);
            }

            // add crafting results.
            for (int outputIndex = 0; outputIndex < this.outputs.size(); outputIndex++) {
                var out = this.outputs.get(outputIndex);
                if (!isCatalyst(out.what())) {
                    insertOutput(inv, out.what(), LongMath.saturatedMultiply(out.amount(), times));
                }
            }
            for (int x = 0; x < this.inputs.length; x++) {
                if (this.catalystReturns != null && this.catalystReturns[x] > 0) {
                    insertOutput(inv, this.slotNodes[x].getWhat(), catalystReturned(x, times));
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

    /**
     * The crafts may have taken other variants than the credited containers were left for. What they did not leave is
     * taken back, and what the crafts below already used of it is missing.
     */
    private void withdrawUnreturnedCredit(CraftingSimulationState inv, KeyCounter credited,
                                          @Nullable KeyCounter containerItems) {
        credited.forEach((entryKey, entryAmount) -> {
            long returned = containerItems == null ? 0 : containerItems.get(entryKey);
            long excess = entryAmount - returned;
            if (excess <= 0) return;
            long withdrawn = inv.extract(entryKey, excess, Actionable.MODULATE);
            if (withdrawn < excess) {
                this.job.addMissing(entryKey, excess - withdrawn);
            }
        });
    }

    private void insertOutput(CraftingSimulationState inv, AEKey what, long amount) {
        if (isFinalOutputPseudoPattern()) {
            inv.insertPseudo(what, amount, Actionable.MODULATE);
        } else {
            inv.insert(what, amount, Actionable.MODULATE);
        }
    }

    private void requestInputs(CraftingSimulationState inv, long times, @Nullable KeyCounter containerItems,
                               @Nullable AEKey skippedInput)
        throws CraftBranchFailure, InterruptedException {
        for (int x = 0; x < this.slotNodes.length; x++) {
            var node = this.slotNodes[x];
            if (node.getWhat().equals(skippedInput)) {
                continue;
            }
            var key = node.getWhat();
            long multiplier = this.inputs[x].getMultiplier();
            long units = skippedInput == null ? slotUnits(x, multiplier, times)
                : LongMath.saturatedMultiply(multiplier, times);
            long requiredExtractBefore = inv.getRequiredExtractAmount(key);
            long missingBefore = this.job.getMissingItems().get(key);
            node.request(inv, units, containerItems);
            recordTreeInputDisplayAmount(inv, node, requiredExtractBefore, missingBefore);
        }
    }

    private void requestIntermediateFinalOutputReplacements(CraftingSimulationState inv, long intermediateAmount)
        throws CraftBranchFailure, InterruptedException {
        long outputCount = getOutputCount(this.job.getOutput());
        if (intermediateAmount <= 0 || outputCount <= 0 || !this.parent.getWhat().equals(this.job.getOutput())) {
            return;
        }
        var skippedInput = getSkippedRecursiveFinalOutputInput();
        if (skippedInput == null || this.slotNodes.length <= 1) {
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
        for (CraftingTreeNode node : this.slotNodes) {
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
                                              long requiredExtractBefore, long missingBefore) {
        if (!node.hasSelfReturningRemainderInput()) {
            return;
        }

        var key = node.getWhat();
        long extractedDelta = inv.getRequiredExtractAmount(key) - requiredExtractBefore;
        long missingDelta = this.job.getMissingItems().get(key) - missingBefore;
        long delta = Math.max(0, extractedDelta) + Math.max(0, missingDelta);
        if (this.treeInputDisplayAmounts == null) {
            this.treeInputDisplayAmounts = new Object2LongOpenHashMap<>(this.inputs.length);
        }
        this.treeInputDisplayAmounts.addTo(node, delta);
    }

    public boolean hasTreeInputDisplayAmount(CraftingTreeNode node) {
        return this.treeInputDisplayAmounts != null && this.treeInputDisplayAmounts.containsKey(node);
    }

    public long getTreeInputDisplayAmount(CraftingTreeNode node) {
        return this.treeInputDisplayAmounts == null ? 0 : this.treeInputDisplayAmounts.getLong(node);
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

        for (CraftingTreeNode node : this.slotNodes) {
            tot += node.getNodeCount();
        }

        return tot;
    }

    int getDepth() {
        int depth = 1;
        for (CraftingTreeNode node : this.slotNodes) {
            depth = Math.max(depth, 1 + node.getDepth());
        }
        return depth;
    }

    long getPatternNodeCount() {
        long total = 0;
        for (CraftingTreeNode node : this.slotNodes) {
            total += node.getPatternNodeCount();
        }
        return total;
    }

    long getOutputCount(AEKey what) {
        long tot = 0;

        for (int i = 0, size = this.outputs.size(); i < size; i++) {
            var output = this.outputs.get(i);
            if (what.matches(output)) {
                tot += output.amount();
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

        for (var input : this.inputs) {
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
        accumulateNet(netByKey, 1);
    }

    void accumulateNet(KeyCounter netByKey, long times) {
        for (int i = 0, size = this.outputs.size(); i < size; i++) {
            var output = this.outputs.get(i);
            netByKey.add(output.what(), LongMath.saturatedMultiply(output.amount(), times));
        }
        for (int slot = 0; slot < this.slotNodes.length; slot++) {
            var node = this.slotNodes[slot];
            netByKey.add(node.getWhat(),
                LongMath.saturatedSubtract(0, LongMath.saturatedMultiply(node.getTemplateAmount(),
                    LongMath.saturatedMultiply(this.inputs[slot].getMultiplier(), times))));
        }
    }

    void accumulateInputKeys(Set<AEKey> inputKeys) {
        for (CraftingTreeNode node : this.slotNodes) {
            inputKeys.add(node.getWhat());
        }
    }

    boolean hasMultiplePaths() {
        for (var node : this.slotNodes) {
            if (node.hasMultiplePaths()) {
                return true;
            }
        }
        return false;
    }

    void resetPossible() {
        this.possible = true;
        this.reusablePreview = null;
        this.treeRequestTimes = 0;
        if (this.treeInputDisplayAmounts != null) this.treeInputDisplayAmounts.clear();
        for (CraftingTreeNode node : this.slotNodes) {
            node.resetPossible();
        }
    }

    public IPatternDetails getDetails() {
        return this.details;
    }

    /**
     * The key of the node this process crafts for, or null if it has none.
     */
    @Nullable
    AEKey getRequestedKey() {
        return this.parent == null ? null : this.parent.getWhat();
    }

    public List<PatternContainerGroup> getMachineGroups() {
        return getMachineInfo().groups();
    }

    public Map<PatternContainerGroup, List<CraftingSupplierLocation>> getMachineLocations() {
        return getMachineInfo().locations();
    }

    private MachineInfo getMachineInfo() {
        var result = this.machineInfo;
        if (result == null) {
            result = this.job.getMachineInfo(this.details);
            this.machineInfo = result;
        }
        return result;
    }

    public record MachineInfo(List<PatternContainerGroup> groups,
                              Map<PatternContainerGroup, List<CraftingSupplierLocation>> locations) {
    }

    public Object2LongMap<CraftingTreeNode> getNodes() {
        var result = this.nodes;
        if (result == null) {
            synchronized (this) {
                result = this.nodes;
                if (result == null) {
                    var amounts = new long[this.slotNodes.length];
                    for (int slot = 0; slot < this.slotNodes.length; slot++) {
                        amounts[slot] = this.inputs[slot].getMultiplier();
                    }
                    // Slot nodes are distinct and never replaced. Share the array and publish immutable entries;
                    // fastutil's unmodifiable set wrapper alone does not protect mutable map entries.
                    var ordered = new NodeMap(this.slotNodes, amounts);
                    this.nodes = result = Object2LongMaps.unmodifiable(ordered);
                }
            }
        }
        return result;
    }

    private static final class NodeMap extends AbstractObject2LongMap<CraftingTreeNode> {
        private final CraftingTreeNode[] nodes;
        private final long[] amounts;

        NodeMap(CraftingTreeNode[] nodes, long[] amounts) {
            this.nodes = nodes;
            this.amounts = amounts;
        }

        @Override
        public int size() {
            return nodes.length;
        }

        @Override
        public long getLong(Object key) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] == key) return amounts[i];
            }
            return 0;
        }

        @Override
        public ObjectSet<Entry<CraftingTreeNode>> object2LongEntrySet() {
            return new AbstractObjectSet<>() {
                @Override
                public int size() {
                    return nodes.length;
                }

                @Override
                public @NonNull ObjectIterator<Entry<CraftingTreeNode>> iterator() {
                    return new ObjectIterator<>() {
                        private int next;

                        @Override
                        public boolean hasNext() {
                            return next < nodes.length;
                        }

                        @Override
                        public Entry<CraftingTreeNode> next() {
                            if (!hasNext()) throw new NoSuchElementException();
                            int index = next++;
                            return new BasicEntry<>(nodes[index], amounts[index]);
                        }
                    };
                }
            };
        }
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
