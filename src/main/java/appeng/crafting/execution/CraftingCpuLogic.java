/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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
package appeng.crafting.execution;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.features.IPlayerRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.AELog;
import appeng.core.network.InitNetwork;
import appeng.core.network.clientbound.CraftingJobStatusPacket;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PseudoPatternDetails;
import appeng.hooks.ticking.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Stores the crafting logic of a crafting CPU.
 */
public class CraftingCpuLogic {
    final CraftingCPUCluster cluster;
    /**
     * Used crafting operations over the last 3 ticks.
     */
    private final int[] usedOps = new int[3];
    private final Set<Consumer<AEKey>> listeners = new ReferenceOpenHashSet<>();
    /**
     * Current job.
     */
    private ExecutingCraftingJob job = null;
    /**
     * True if the CPU is currently trying to clear its inventory but is not able to.
     */
    private boolean cantStoreItems = false;
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    /**
     * Inventory.
     */
    private final ListCraftingInventory inventory = new ListCraftingInventory(CraftingCpuLogic.this::postChange);

    public CraftingCpuLogic(CraftingCPUCluster cluster) {
        this.cluster = cluster;
    }

    private static boolean isFinalOutputPseudoPattern(ExecutingCraftingJob job, IPatternDetails details) {
        if (!PseudoPatternDetails.isPseudo(details)) {
            return false;
        }
        if (!(PseudoPatternDetails.unwrap(details) instanceof AEProcessingPattern)) {
            return false;
        }
        for (var output : details.getOutputs()) {
            if (output.what().matches(job.finalOutput)) {
                return true;
            }
        }
        return false;
    }

    private static long getRemainingNormalInputDemand(ExecutingCraftingJob job, AEKey what) {
        long demand = 0;
        for (var task : job.tasks.entrySet()) {
            if (task.getValue().value <= 0 || PseudoPatternDetails.isPseudo(task.getKey())) {
                continue;
            }
            for (var input : task.getKey().getInputs()) {
                for (var possibleInput : input.possibleInputs()) {
                    if (what.matches(possibleInput)) {
                        demand += possibleInput.amount() * input.getMultiplier() * task.getValue().value;
                        break;
                    }
                }
            }
        }
        return demand;
    }

    public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                              @Nullable ICraftingRequester requester) {
        // Already have a job.
        if (this.job != null)
            return CraftingSubmitResult.CPU_BUSY;
        // Check that the node is active.
        if (!cluster.isActive())
            return CraftingSubmitResult.CPU_OFFLINE;
        // Check bytes.
        if (cluster.getAvailableStorage() < plan.bytes())
            return CraftingSubmitResult.CPU_TOO_SMALL;

        if (!inventory.list.isEmpty())
            AELog.warn("Crafting CPU inventory is not empty yet a job was submitted.");

        // Extract everything available now; unresolved missing ingredients will be captured by the CPU later.
        var remainingMissingItems = CraftingCpuHelper.extractInitialItems(plan, grid, inventory, src);

        // Set CPU link and job.
        var playerId = src.player()
                          .map(p -> p instanceof EntityPlayerMP serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                          .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cluster);
        this.job = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId, remainingMissingItems);
        cluster.updateOutput(plan.finalOutput());
        cluster.markDirty();

        // TODO: post monitor difference?

        notifyJobOwner(job, CraftingJobStatusPacket.Status.STARTED);

        // Non-standalone jobs need another link for the requester, and both links need to be submitted to the cache.
        if (requester != null) {
            var linkReq = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);

            var craftingService = (CraftingService) grid.getCraftingService();
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);

            return CraftingSubmitResult.successful(linkReq);
        } else {
            return CraftingSubmitResult.successful(null);
        }
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        // Don't tick if we're not active.
        if (!cluster.isActive())
            return;
        cantStoreItems = false;
        // If we don't have a job, just try to dump our items.
        if (this.job == null) {
            this.storeItems();
            if (!this.inventory.list.isEmpty()) {
                cantStoreItems = true;
            }
            return;
        }
        // Check if the job was cancelled.
        if (job.link.isCanceled()) {
            cancel();
            return;
        }

        // Don't schedule more work while suspended
        if (job.suspended) {
            return;
        }

        var remainingOperations = cluster.getCoProcessors() + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        final var started = remainingOperations;

        if (remainingOperations > 0) {
            do {
                var pushedPatterns = executeCrafting(remainingOperations, cc, eg, cluster.getLevel());

                if (pushedPatterns > 0) {
                    remainingOperations -= pushedPatterns;
                } else {
                    break;
                }
            } while (remainingOperations > 0);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - remainingOperations;
    }

    /**
     * Try to push patterns into available interfaces, i.e. do the actual crafting execution.
     *
     * @return How many patterns were successfully pushed.
     */
    public int executeCrafting(int maxPatterns, CraftingService craftingService, IEnergyService energyService,
                               World level) {
        return executeCraftingWithProviderLookup(maxPatterns, energyService, level,
            details -> getProvidersForPattern(craftingService, details));
    }

    private Iterable<ICraftingProvider> getProvidersForPattern(CraftingService craftingService,
                                                               IPatternDetails details) {
        var job = this.job;
        if (job != null && job.isTemporaryPattern(details)) {
            return job.getProvidersForPattern(details);
        }
        return craftingService.getProviders(details);
    }

    int executeCraftingWithProviderLookup(int maxPatterns, IEnergyService energyService, World level,
                                          java.util.function.Function<IPatternDetails, Iterable<ICraftingProvider>> providersForPattern) {
        var job = this.job;
        if (job == null)
            return 0;

        var pushedPatterns = 0;

        var it = job.tasks.entrySet().iterator();
        taskLoop:
        while (it.hasNext()) {
            var task = it.next();
            if (task.getValue().value <= 0) {
                it.remove();
                continue;
            }

            var details = task.getKey();
            var finalOutputPseudoPattern = isFinalOutputPseudoPattern(job, details);
            var expectedOutputs = new KeyCounter();
            var expectedContainerItems = new KeyCounter();
            ICraftingInventory taskInventory = finalOutputPseudoPattern && job.isTemporaryPattern(details)
                ? new RealInputTrackingInventoryView(inventory)
                : inventory;
            // Contains the inputs for the pattern.
            @Nullable
            var craftingContainer = CraftingCpuHelper.extractPatternInputs(details, taskInventory, level,
                expectedOutputs, expectedContainerItems);

            // Try to push to each provider.
            for (var provider : providersForPattern.apply(details)) {
                if (craftingContainer == null)
                    break;
                if (provider.isBusy())
                    continue;

                var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);

                if (energyService.extractAEPower(patternPower, Actionable.SIMULATE,
                    PowerMultiplier.CONFIG) < patternPower - 0.01)
                    break;

                if (provider.pushPattern(details, craftingContainer)) {
                    energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                    pushedPatterns++;
                    recordPushedPattern(job, details, taskInventory, expectedOutputs, expectedContainerItems);

                    cluster.markDirty();

                    task.getValue().value--;
                    if (finalOutputPseudoPattern) {
                        completePseudoOutputs(job, expectedOutputs);
                        if (job != this.job) {
                            break taskLoop;
                        }
                    }
                    if (task.getValue().value <= 0) {
                        it.remove();
                        if (job != this.job) {
                            break taskLoop;
                        }
                        continue taskLoop;
                    }

                    if (pushedPatterns == maxPatterns) {
                        break taskLoop;
                    }

                    // Prepare next inputs.
                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    taskInventory = finalOutputPseudoPattern && job.isTemporaryPattern(details)
                        ? new RealInputTrackingInventoryView(inventory)
                        : inventory;
                    craftingContainer = CraftingCpuHelper.extractPatternInputs(details, taskInventory, level,
                        expectedOutputs, expectedContainerItems);
                }
            }

            // Failed to push this pattern, reinject the inputs.
            if (craftingContainer != null) {
                CraftingCpuHelper.reinjectPatternInputs(taskInventory, craftingContainer);
            }
        }

        return pushedPatterns;
    }

    private void recordPushedPattern(ExecutingCraftingJob job, IPatternDetails details,
                                     ICraftingInventory taskInventory, KeyCounter expectedOutputs,
                                     KeyCounter expectedContainerItems) {
        if (isFinalOutputPseudoPattern(job, details)) {
            recordPushedPseudoPattern(job, details, taskInventory, expectedOutputs, expectedContainerItems);
        } else {
            recordPushedNormalPattern(job, expectedOutputs, expectedContainerItems);
        }
    }

    private void recordPushedNormalPattern(ExecutingCraftingJob job, KeyCounter expectedOutputs,
                                           KeyCounter expectedContainerItems) {
        for (var expectedOutput : expectedOutputs) {
            job.waitingFor.insert(expectedOutput.getKey(), expectedOutput.getLongValue(), Actionable.MODULATE);
        }
        for (var expectedContainerItem : expectedContainerItems) {
            job.waitingFor.insert(expectedContainerItem.getKey(), expectedContainerItem.getLongValue(),
                Actionable.MODULATE);
            job.timeTracker.addMaxItems(expectedContainerItem.getLongValue(), expectedContainerItem.getKey().getType());
        }
    }

    private void recordPushedPseudoPattern(ExecutingCraftingJob job, IPatternDetails details,
                                           ICraftingInventory taskInventory, KeyCounter expectedOutputs,
                                           KeyCounter expectedContainerItems) {
        var temporaryPattern = job.isTemporaryPattern(details);
        if (temporaryPattern && taskInventory instanceof RealInputTrackingInventory taskInventoryView) {
            returnTemporaryPatternInputs(taskInventoryView);
        }
        for (var expectedOutput : expectedOutputs) {
            long waitingAmount = Math.min(expectedOutput.getLongValue(),
                getRemainingNormalInputDemand(job, expectedOutput.getKey()));
            if (waitingAmount > 0) {
                job.waitingFor.insert(expectedOutput.getKey(), waitingAmount, Actionable.MODULATE);
                job.timeTracker.addMaxItems(waitingAmount, expectedOutput.getKey().getType());
            }
            long pseudoAmount = expectedOutput.getLongValue() - waitingAmount;
            if (pseudoAmount > 0) {
                job.pseudoInventory.insert(expectedOutput.getKey(), pseudoAmount, Actionable.MODULATE);
            }
        }
        if (!temporaryPattern) {
            for (var expectedContainerItem : expectedContainerItems) {
                inventory.insert(expectedContainerItem.getKey(), expectedContainerItem.getLongValue(),
                    Actionable.MODULATE);
            }
        }
    }

    /**
     * Called by the CraftingService with an Integer.MAX_VALUE priority to inject items that are being waited for.
     *
     * @return Consumed amount.
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // also stop accepting items when the job is complete, i.e. to prevent re-insertion when pushing out
        // items during storeItems
        if (what == null || job == null)
            return 0;

        // Only accept items we are waiting for.
        var waitingFor = job.waitingFor.extract(what, amount, Actionable.SIMULATE);
        if (waitingFor <= 0) {
            return 0;
        }

        // Make sure we don't insert more than what we are waiting for.
        if (amount > waitingFor) {
            amount = waitingFor;
        }

        if (type == Actionable.MODULATE) {
            job.timeTracker.decrementItems(amount, what.getType()); // Process Fluid and Items
            job.waitingFor.extract(what, amount, Actionable.MODULATE);
            cluster.markDirty();
        }

        long inserted = amount;
        if (what.matches(job.finalOutput)) {
            long intermediateAmount = Math.min(amount, job.remainingIntermediateFinalOutput);
            if (intermediateAmount > 0) {
                if (type == Actionable.MODULATE) {
                    inventory.insert(what, intermediateAmount, Actionable.MODULATE);
                    job.remainingIntermediateFinalOutput -= intermediateAmount;
                }
                amount -= intermediateAmount;
                if (amount <= 0) {
                    return inserted;
                }
            }

            // Standalone jobs have no requester link, so their requested output must be kept locally and returned to the
            // network when the CPU finishes.
            if (job.link.isStandalone()) {
                if (type == Actionable.MODULATE) {
                    inventory.insert(what, amount, Actionable.MODULATE);
                }
            } else {
                // Final output is special: it goes directly into the requester.
                job.link.insert(what, amount, type);
            }

            // Note: we ignore any remainder (could be the entire input if there is no requester),
            // we already marked the items as done, and we might even finish the job.

            // This means that the job can be marked as finished even if some items were not actually inserted.
            // In some cases, repeated failed inserts of a fraction of the final output might prevent some recipes from
            // being pushed.
            // TODO: Look into fixing this, perhaps we could use the network monitor to check how much was really
            // TODO: inserted into the network.
            // TODO: Another solution is to wait until all recipes have been pushed before cancelling the job.

            if (type == Actionable.MODULATE) {
                // Update count and displayed CPU stack, and finish the job if possible.
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - amount);

                if (job.remainingAmount <= 0) {
                    finishJob(true);
                    cluster.updateOutput(null);
                } else {
                    cluster.updateOutput(new GenericStack(job.finalOutput.what(), job.remainingAmount));
                }
            }
        } else {
            if (type == Actionable.MODULATE) {
                inventory.insert(what, amount, Actionable.MODULATE);
            }
        }

        return inserted;
    }

    /**
     * Finish the current job.
     *
     * @param success True if the job is complete, false if it was cancelled.
     */
    private void finishJob(boolean success) {
        if (success) {
            job.link.markDone();
        } else {
            job.link.cancel();
        }

        // TODO: log

        // Clear waitingFor list and post all the relevant changes.
        job.waitingFor.clear();
        job.pseudoInventory.clear();
        // Notify opened menus of cancelled scheduled tasks.
        for (var entry : job.tasks.entrySet()) {
            for (var output : entry.getKey().getOutputs()) {
                postChange(output.what());
            }
        }

        notifyJobOwner(job,
            success ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED);

        // Finish job.
        this.job = null;

        // Store all remaining items.
        this.storeItems();
    }

    /**
     * Cancel the current job.
     */
    public void cancel() {
        // No job to cancel :P
        if (job == null)
            return;

        // Clear displayed stack.
        cluster.updateOutput(null);

        finishJob(false);
    }

    /**
     * Tries to dump all locally stored items back into the storage network.
     */
    public void storeItems() {
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        // Short-circuit if there is nothing to do.
        if (this.inventory.list.isEmpty())
            return;

        var g = cluster.getGrid();
        if (g == null)
            return;

        var storage = g.getStorageService().getInventory();

        for (var entry : this.inventory.list) {
            this.postChange(entry.getKey());
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(),
                Actionable.MODULATE, cluster.getSrc());

            // The network was unable to receive all of the items, i.e. no or not enough storage space left
            entry.setValue(entry.getLongValue() - inserted);
        }
        this.inventory.list.removeZeros();

        cluster.markDirty();
    }

    private void postChange(AEKey what) {
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        for (var listener : listeners) {
            listener.accept(what);
        }
    }

    public long getLastModifiedOnTick() {
        return lastModifiedOnTick;
    }

    public boolean hasJob() {
        return this.job != null;
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.job != null ? this.job.finalOutput : null;
    }

    public ElapsedTimeTracker getElapsedTimeTracker() {
        if (this.job != null) {
            return this.job.timeTracker;
        } else {
            return new ElapsedTimeTracker();
        }
    }

    public void readFromNBT(NBTTagCompound data) {
        this.inventory.readFromNBT(data.getTagList("inventory", Constants.NBT.TAG_COMPOUND));
        if (data.hasKey("job", Constants.NBT.TAG_COMPOUND)) {
            this.job = new ExecutingCraftingJob(data.getCompoundTag("job"), this::postChange, this);
            if (this.job.finalOutput == null) {
                finishJob(false);
            } else {
                cluster.updateOutput(new GenericStack(job.finalOutput.what(), job.remainingAmount));
            }
        } else {
            cluster.updateOutput(null);
        }
    }

    public void writeToNBT(NBTTagCompound data) {
        data.setTag("inventory", this.inventory.writeToNBT());
        if (this.job != null) {
            data.setTag("job", this.job.writeToNBT());
        }
    }

    @Nullable
    public ICraftingLink getLastLink() {
        if (this.job != null) {
            return this.job.link;
        }
        return null;
    }

    public ListCraftingInventory getInventory() {
        return this.inventory;
    }

    /**
     * Register a listener that will receive stacks when either the stored items, await items or pending outputs change.
     * This is only used by the container. Make sure to remove it by calling {@link #removeListener}.
     */
    public void addListener(Consumer<AEKey> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AEKey> listener) {
        listeners.remove(listener);
    }

    public long getStored(AEKey template) {
        return this.inventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    public long getWaitingFor(AEKey template) {
        if (this.job != null) {
            return this.job.waitingFor.extract(template, Long.MAX_VALUE, Actionable.SIMULATE)
                + this.job.pseudoInventory.extract(template, Long.MAX_VALUE, Actionable.SIMULATE);
        }
        return 0;
    }

    public void getAllWaitingFor(Set<AEKey> waitingFor) {
        if (this.job != null) {
            for (var entry : this.job.waitingFor.list) {
                waitingFor.add(entry.getKey());
            }
            for (var entry : this.job.pseudoInventory.list) {
                waitingFor.add(entry.getKey());
            }
        }
    }

    public long getPendingOutputs(AEKey template) {
        long count = 0;
        if (this.job != null) {
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    if (template.matches(output)) {
                        count += output.amount() * t.getValue().value;
                    }
                }
            }
        }
        return count;
    }

    public List<CraftingSupplierLocation> findSupplierLocations(IGrid grid, AEKey target) {
        if (this.job == null || target == null) {
            return List.of();
        }

        return CraftingSupplierLocator.collectMatchingProviderLocations(
            grid,
            target,
            this.job.tasks.keySet(),
            details -> ((CraftingService) grid.getCraftingService()).getProviders(details));
    }

    /**
     * Used by the container to gather all the kinds of stored items.
     */
    public void getAllItems(KeyCounter out) {
        out.addAll(this.inventory.list);
        if (this.job != null) {
            out.addAll(job.waitingFor.list);
            out.addAll(job.pseudoInventory.list);
            for (var t : job.tasks.entrySet()) {
                for (var output : t.getKey().getOutputs()) {
                    out.add(output.what(), output.amount() * t.getValue().value);
                }
            }
        }
    }

    public boolean isCantStoreItems() {
        return cantStoreItems;
    }

    public boolean isJobSuspended() {
        return job != null && job.suspended;
    }

    public void setJobSuspended(boolean suspended) {
        if (job != null && job.suspended != suspended) {
            job.suspended = suspended;
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status) {
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        var playerId = job.playerId;
        if (playerId == null) {
            return;
        }

        var level = cluster.getLevel();
        if (level == null) {
            return;
        }
        var server = level.getMinecraftServer();
        var finalOutput = java.util.Objects.requireNonNull(job.finalOutput);
        if (server == null) {
            return;
        }
        var finalOutputKey = finalOutput.what();
        if (finalOutputKey == null) {
            return;
        }
        var finalOutputAmount = finalOutput.amount();
        var connectedPlayer = IPlayerRegistry.getConnected(server, playerId);
        if (connectedPlayer != null) {
            var jobId = job.link.getCraftingID();
            InitNetwork.CHANNEL.sendTo(new CraftingJobStatusPacket(
                jobId,
                finalOutputKey,
                finalOutputAmount,
                job.remainingAmount,
                status), connectedPlayer);
        }
    }

    private void completePseudoOutputs(ExecutingCraftingJob job, KeyCounter expectedOutputs) {
        if (job != this.job) {
            return;
        }

        for (var expectedOutput : expectedOutputs) {
            if (!expectedOutput.getKey().matches(job.finalOutput)) {
                continue;
            }

            long completedAmount = Math.min(expectedOutput.getLongValue(), job.remainingAmount);
            if (completedAmount <= 0) {
                continue;
            }

            job.pseudoInventory.extract(expectedOutput.getKey(), completedAmount, Actionable.MODULATE);
            postChange(expectedOutput.getKey());
            job.remainingAmount -= completedAmount;
            if (job.remainingAmount <= 0) {
                finishJob(true);
                cluster.updateOutput(null);
                return;
            }
            cluster.updateOutput(new GenericStack(job.finalOutput.what(), job.remainingAmount));
        }
    }

    private void returnTemporaryPatternInputs(RealInputTrackingInventory taskInventory) {
        var grid = cluster.getGrid();
        if (grid == null) {
            taskInventory.reinjectRealInputs(inventory);
            return;
        }

        var storage = grid.getStorageService().getInventory();
        for (var entry : taskInventory.realExtracted()) {
            var inserted = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, cluster.getSrc());
            if (inserted < entry.getLongValue()) {
                inventory.insert(entry.getKey(), entry.getLongValue() - inserted, Actionable.MODULATE);
            }
        }
    }

    private interface RealInputTrackingInventory extends ICraftingInventory {
        KeyCounter realExtracted();

        void reinjectRealInputs(ListCraftingInventory inventory);
    }

    private static final class RealInputTrackingInventoryView implements RealInputTrackingInventory {
        private final ListCraftingInventory realInventory;
        private final KeyCounter realExtracted = new KeyCounter();

        private RealInputTrackingInventoryView(ListCraftingInventory realInventory) {
            this.realInventory = realInventory;
        }

        @Override
        public void insert(AEKey what, long amount, Actionable mode) {
            long tracked = Math.min(amount, realExtracted.get(what));
            if (tracked > 0) {
                realExtracted.remove(what, tracked);
                realInventory.insert(what, tracked, mode);
                amount -= tracked;
            }
            if (amount > 0) {
                realInventory.insert(what, amount, mode);
            }
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode) {
            long extracted = realInventory.extract(what, amount, mode);
            if (mode == Actionable.MODULATE && extracted > 0) {
                realExtracted.add(what, extracted);
            }
            return extracted;
        }

        @Override
        public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
            return realInventory.findFuzzyTemplates(input);
        }

        @Override
        public KeyCounter realExtracted() {
            return realExtracted;
        }

        @Override
        public void reinjectRealInputs(ListCraftingInventory inventory) {
            for (var entry : realExtracted) {
                inventory.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
    }

}
