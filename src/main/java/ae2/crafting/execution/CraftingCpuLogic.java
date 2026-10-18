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
package ae2.crafting.execution;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.crafting.IPatternDetails;
import ae2.api.features.IPlayerRegistry;
import ae2.api.networking.IGrid;
import ae2.api.networking.crafting.CraftingJobOptions;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.core.AELog;
import ae2.core.definitions.AEItems;
import ae2.core.network.InitNetwork;
import ae2.core.network.clientbound.CraftingJobStatusPacket;
import ae2.crafting.BatchCraftingPlan;
import ae2.crafting.CraftingLink;
import ae2.crafting.TemporaryProviderCarrier;
import ae2.crafting.inv.ICraftingInventory;
import ae2.crafting.inv.ListCraftingInventory;
import ae2.crafting.pattern.AEProcessingPattern;
import ae2.helpers.patternprovider.PseudoPatternDetails;
import ae2.hooks.ticking.TickHandler;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.me.service.CraftingService;
import com.google.common.base.Preconditions;
import com.google.common.math.LongMath;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Stores the crafting logic of a crafting CPU.
 */
public class CraftingCpuLogic {
    private static final String NBT_PENDING_STANDALONE_OUTPUT = "pendingStandaloneOutput";
    private static final String NBT_UNKNOWN_STANDALONE_OUTPUT = "unknownStandaloneOutput";
    private static final String NBT_UNKNOWN_REQUESTER_OUTPUT = "unknownRequesterOutput";
    private static final String NBT_INVENTORY_RETURN = "inventoryReturn";
    private static final String NBT_READ_FAILURE = "cpuReadFailure";
    private static final String[] SAVED_KEYS = {"inventory", "job", NBT_PENDING_STANDALONE_OUTPUT,
        NBT_UNKNOWN_STANDALONE_OUTPUT, NBT_UNKNOWN_REQUESTER_OUTPUT, NBT_INVENTORY_RETURN, NBT_READ_FAILURE};

    final CraftingCPUCluster cluster;
    /**
     * Used crafting operations over the last 3 ticks.
     */
    private final int[] usedOps = new int[3];
    private final Reference2ObjectOpenHashMap<Consumer<AEKey>, ChangeListenerRegistration> listeners =
        new Reference2ObjectOpenHashMap<>();
    /**
     * Inventory.
     */
    private final ListCraftingInventory inventory = new ListCraftingInventory(what -> {
        this.inventoryRevision++;
        this.postChange(what);
    });
    /**
     * Current job.
     */
    private ExecutingCraftingJob job = null;
    /**
     * True if the CPU is currently trying to clear its inventory but is not able to.
     */
    private boolean cantStoreItems = false;
    private long pendingStandaloneOutput = 0;
    @Nullable
    private ChangeListenerRegistration[] listenerSnapshot;
    /**
     * Output whose external insertion threw after an unknown amount may have been accepted.
     */
    @Nullable
    private AEKey unknownStandaloneOutputKey;
    private boolean returningStandaloneOutput = false;
    @Nullable
    private AEKey unknownRequesterOutputKey;
    private boolean executingCrafting;
    private boolean tickingCrafting;
    private boolean submitting;
    private boolean finishingJob;
    private boolean returningItems;
    private boolean readingState;
    private boolean restoreLinkPending;
    private long inventoryRevision;
    @Nullable
    private NBTBase savedReadFailure;
    @Nullable
    private NBTTagCompound quarantinedImage;
    private long inventoryGeneration;
    @Nullable
    private InventoryReturn inventoryReturn;
    @Nullable
    private NBTBase savedInventoryReturn;
    private boolean submissionSavePending;
    private boolean submissionSaveFailureLogged;
    private int confirmedDispatchPatterns;
    private long taskRevision;
    @Nullable
    private ExecutingCraftingJob pendingOutputSnapshotJob;
    private long pendingOutputSnapshotRevision = Long.MIN_VALUE;
    private long lastModifiedOnTick = TickHandler.instance().getCurrentTick();
    @Nullable
    private KeyCounter pendingOutputSnapshot;

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
                        demand = LongMath.saturatedAdd(demand,
                            LongMath.saturatedMultiply(possibleInput.amount(),
                                LongMath.saturatedMultiply(input.getMultiplier(), task.getValue().value)));
                        break;
                    }
                }
            }
        }
        return demand;
    }

    public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                              @Nullable ICraftingRequester requester) {
        return trySubmitJob(grid, plan, src, requester, CraftingJobOptions.DEFAULT);
    }

    private static void collectInventoryKeys(Set<AEKey> keys, KeyCounter inventory) {
        for (var entry : inventory) {
            keys.add(entry.getKey());
        }
    }

    private static void collectJobKeys(Set<AEKey> keys, @Nullable ExecutingCraftingJob job) {
        if (job == null) return;
        collectInventoryKeys(keys, job.waitingFor.list);
        collectInventoryKeys(keys, job.pseudoInventory.list);
        for (var details : job.tasks.keySet()) {
            try {
                for (var output : details.getOutputs()) keys.add(output.what());
            } catch (RuntimeException failure) {
                AELog.error(failure, "Failed to resolve a restored CPU notification key");
            }
        }
    }

    private static boolean sameResources(KeyCounter first, KeyCounter second) {
        for (var entry : first) if (second.get(entry.getKey()) != entry.getLongValue()) return false;
        for (var entry : second) if (first.get(entry.getKey()) != entry.getLongValue()) return false;
        return true;
    }

    static NBTTagList savedList(NBTTagCompound data, String key) {
        requireTag(data, key, Constants.NBT.TAG_LIST);
        var list = data.getTagList(key, Constants.NBT.TAG_COMPOUND);
        if (data.getTag(key) instanceof NBTTagList original && original.tagCount() > 0
            && original.getTagType() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException("Saved CPU list must contain compounds: " + key);
        }
        return list;
    }

    /**
     * True if the plan fakes its final output through a temporary pattern instead of producing it.
     */
    private static boolean hasPlaceholderFinalOutput(ICraftingPlan plan) {
        if (plan instanceof TemporaryProviderCarrier carrier) {
            for (var provider : carrier.temporaryProviders()) {
                for (var pattern : provider.getAvailablePatterns()) {
                    if (PseudoPatternDetails.isPseudo(pattern)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static KeyCounter savedResources(NBTTagCompound data, String key) {
        var prepared = new KeyCounter();
        for (var stack : GenericStack.readList(savedList(data, key))) {
            if (stack != null) {
                if (stack.amount() < 0 || stack.amount() > Long.MAX_VALUE - prepared.get(stack.what())) {
                    throw new IllegalArgumentException("Unrepresentable saved CPU resource count: " + key);
                }
                if (stack.amount() > 0) prepared.add(stack.what(), stack.amount());
            }
        }
        return prepared;
    }

    private static void requireTag(NBTTagCompound data, String key, int type) {
        if (data.hasKey(key) && !data.hasKey(key, type)) {
            throw new IllegalArgumentException("Wrong saved CPU tag type: " + key);
        }
    }

    private static boolean sameSavedState(NBTTagCompound first, NBTTagCompound second) {
        for (var key : SAVED_KEYS) if (!Objects.equals(first.getTag(key), second.getTag(key))) return false;
        return true;
    }

    /**
     * The items a started job makes the terminal pin in its own row on top of the item list and show as in progress.
     * <p>
     * A batch order of the crafting terminal orders the materials the transferred recipe was missing, so those are the
     * items it crafts. Its reported output is only a placeholder, nothing is crafted into it, so tracking that instead
     * would leave the materials looking finished and drop them from the terminal.
     */
    private static List<AEKey> trackedKeys(ICraftingPlan plan) {
        if (plan instanceof BatchCraftingPlan batchPlan) {
            var targets = batchPlan.batchTargets();
            var keys = new ObjectArrayList<AEKey>(targets.size());
            for (var target : targets) {
                keys.add(target.what());
            }
            return keys;
        }
        return List.of();
    }

    public ICraftingSubmitResult tryMergeJob(IGrid grid, ICraftingPlan plan, IActionSource src) {
        return tryMergeJob(grid, plan, src, 0);
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

    public ICraftingSubmitResult trySubmitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                              @Nullable ICraftingRequester requester, CraftingJobOptions options) {
        if (this.submitting || this.finishingJob || this.returningItems || this.readingState
            || this.hasUnconfirmedInventory()) {
            return CraftingSubmitResult.CPU_BUSY;
        }
        this.submitting = true;
        try {
            return submitOwnedJob(grid, plan, src, requester, options);
        } finally {
            this.submitting = false;
        }
    }

    private ICraftingSubmitResult submitOwnedJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                                 @Nullable ICraftingRequester requester, CraftingJobOptions options) {
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

        var playerId = src.player()
                          .map(p -> p instanceof EntityPlayerMP serverPlayer ? IPlayerRegistry.getPlayerId(serverPlayer) : null)
                          .orElse(null);
        var craftId = UUID.randomUUID();
        var linkCpu = new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, requester == null, false), cluster);
        var linkReq = requester == null ? null
            : new CraftingLink(CraftingCpuHelper.generateLinkData(craftId, false, true), requester);
        var craftingService = requester == null ? null : (CraftingService) grid.getCraftingService();
        var tracked = playerId == null ? List.<AEKey>of() : trackedKeys(plan);

        // Resolve plan metadata and providers before taking ownership of any network inputs.
        var committed = new ExecutingCraftingJob(plan, this::postChange, linkCpu, playerId, options);

        // Extract everything available now; unresolved missing ingredients will be captured by the CPU later.
        var remainingMissingItems = CraftingCpuHelper.extractInitialItems(plan, grid, inventory, src);
        committed.addMissingItems(remainingMissingItems);
        this.job = committed;
        invalidatePendingOutputSnapshot();
        this.submissionSavePending = true;

        // Establish both ends before display, save, or player notifications can call back into the requester.
        if (craftingService != null) {
            craftingService.addLink(linkCpu);
            craftingService.addLink(linkReq);
        }
        var receipt = CraftingSubmitResult.successful(linkReq);

        try {
            cluster.updateOutput(committed.finalOutput);
        } catch (RuntimeException failure) {
            AELog.error(failure, "Failed to update the display of a committed crafting submission");
        }
        this.saveCommittedSubmission();

        // TODO: post monitor difference?

        if (this.job == committed) {
            try {
                notifyJobOwner(committed, CraftingJobStatusPacket.Status.STARTED, false, tracked);
            } catch (RuntimeException failure) {
                AELog.error(failure, "Failed to notify the owner of a committed crafting submission");
            }
        }
        return receipt;
    }

    private void saveCommittedSubmission() {
        if (!this.submissionSavePending) {
            return;
        }
        this.submissionSavePending = false;
        try {
            this.cluster.markDirty();
            this.submissionSaveFailureLogged = false;
        } catch (RuntimeException failure) {
            this.submissionSavePending = true;
            if (!this.submissionSaveFailureLogged) {
                this.submissionSaveFailureLogged = true;
                AELog.error(failure, "Failed to save a committed crafting submission; retrying the save notification");
            }
        }
    }

    public boolean canMergeJob(ICraftingPlan plan) {
        if (this.readingState || this.job == null || this.job.suspended || this.job.link.isDone()
            || this.job.link.isCanceled() || this.hasUnconfirmedInventory() || plan.simulation()) {
            return false;
        }
        if (!this.job.finalOutput.what().equals(plan.finalOutput().what())) {
            return false;
        }
        if (this.job.hasPlaceholderFinalOutput() != hasPlaceholderFinalOutput(plan)) {
            // A job whose output is only pretended must never merge with a real job for the same item, or the other way
            // around: the pretended production would satisfy a real demand without anything being crafted.
            return false;
        }

        long mergedBytes = LongMath.saturatedAdd(this.job.estimateRemainingPlanBytes(), plan.bytes());
        return cluster.getAvailableStorage() >= mergedBytes;
    }

    private boolean canAttemptMultiplier(double singlePatternPower, IEnergyService energyService, int multiplier) {
        if (multiplier <= 0) {
            return false;
        }

        double estimatedPower = singlePatternPower * multiplier;
        return energyService.extractAEPower(estimatedPower, Actionable.SIMULATE,
            PowerMultiplier.CONFIG) >= estimatedPower - 0.01;
    }

    private int getMaxEnergyAffordableMultiplier(double singlePatternPower, IEnergyService energyService,
                                                 int maxMultiplier) {
        if (maxMultiplier <= 0) {
            return 0;
        }
        if (singlePatternPower <= 0) {
            return maxMultiplier;
        }

        double requestedPower = singlePatternPower * maxMultiplier;
        double availablePower = energyService.extractAEPower(requestedPower, Actionable.SIMULATE,
            PowerMultiplier.CONFIG);
        if (availablePower < singlePatternPower - 0.01) {
            return 0;
        }
        if (availablePower >= requestedPower - 0.01) {
            return maxMultiplier;
        }
        return Math.clamp((int) Math.floor((availablePower + 0.01) / singlePatternPower), 0, maxMultiplier);
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

    public ICraftingSubmitResult tryMergeJob(IGrid grid, ICraftingPlan plan, IActionSource src, int priority) {
        if (this.submitting || this.finishingJob || this.returningItems || this.readingState || !canMergeJob(plan)) {
            return CraftingSubmitResult.CPU_BUSY;
        }

        var remainingMissingItems = CraftingCpuHelper.extractInitialItems(plan, grid, inventory, src);
        this.job.merge(plan, remainingMissingItems, priority);
        invalidatePendingOutputSnapshot();
        cluster.updateOutput(new GenericStack(this.job.finalOutput.what(), this.job.remainingAmount));
        cluster.markDirty();
        postChange(this.job.finalOutput.what());
        return CraftingSubmitResult.successful(null);
    }

    public void tickCraftingLogic(IEnergyService eg, CraftingService cc) {
        if (this.executingCrafting || this.tickingCrafting || this.submitting || this.finishingJob || this.returningItems
            || this.readingState) {
            return;
        }
        this.tickingCrafting = true;
        try {
            tickOwnedCraftingLogic(eg, cc);
        } finally {
            this.tickingCrafting = false;
        }
    }

    private void tickOwnedCraftingLogic(IEnergyService eg, CraftingService cc) {
        this.saveCommittedSubmission();
        if (this.hasUnconfirmedInventory()) {
            this.cantStoreItems = true;
            return;
        }
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
        boolean completed = job.link.isDone();
        if (completed || job.link.isCanceled()) {
            finishJob(completed);
            return;
        }

        var tickJob = this.job;
        returnStandaloneOutputToNetwork();

        // A callback can replace, cancel, or complete the job while returning output.
        if (!isDispatchJobCurrent(tickJob)) {
            return;
        }
        retryPendingRequesterOutput(tickJob);
        if (!isDispatchJobCurrent(tickJob) || tickJob.pendingRequesterOutput > 0 || tickJob.requesterOutputUnknown) {
            return;
        }

        var remainingOperations = cluster.getCoProcessors() + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        int completedThisTick = 0;
        try {
            if (remainingOperations > 0) {
                do {
                    int pushedPatterns = 0;
                    this.confirmedDispatchPatterns = 0;
                    try {
                        pushedPatterns = executeCrafting(remainingOperations, cc, eg, cluster.getLevel());
                    } finally {
                        // Also retain confirmed work when a later callback throws before executeCrafting returns.
                        completedThisTick += Math.max(pushedPatterns, this.confirmedDispatchPatterns);
                    }
                    if (pushedPatterns <= 0 || !isDispatchJobCurrent(tickJob)) {
                        break;
                    }
                    remainingOperations -= pushedPatterns;
                } while (remainingOperations > 0);
            }
        } finally {
            this.usedOps[2] = this.usedOps[1];
            this.usedOps[1] = this.usedOps[0];
            this.usedOps[0] = completedThisTick;
        }
    }

    int executeCraftingWithProviderLookup(int maxPatterns, IEnergyService energyService, World level,
                                          Function<IPatternDetails, Iterable<ICraftingProvider>> providersForPattern) {
        var job = this.job;
        if (this.executingCrafting || this.readingState || this.hasUnconfirmedInventory())
            return 0;
        this.confirmedDispatchPatterns = 0;
        if (job == null || maxPatterns <= 0 || !isDispatchJobCurrent(job))
            return 0;

        this.executingCrafting = true;
        try {
            return dispatchCrafting(job, maxPatterns, energyService, level, providersForPattern);
        } finally {
            this.executingCrafting = false;
        }
    }

    private int dispatchCrafting(ExecutingCraftingJob job, int maxPatterns, IEnergyService energyService, World level,
                                 Function<IPatternDetails, Iterable<ICraftingProvider>> providersForPattern) {
        var pushedPatterns = 0;
        int taskCount = job.tasks.size();
        if (taskCount != job.schedulingTaskCount) {
            job.schedulingCursor = 0;
            job.schedulingTaskCount = taskCount;
        }
        if (taskCount == 0) {
            job.schedulingCursor = 0;
            return 0;
        }
        int taskVisitBudget = (int) Math.clamp((long) maxPatterns * 4, 1L, taskCount);
        int providerAttemptBudget = (int) Math.clamp((long) maxPatterns * 8, 1L, Integer.MAX_VALUE);
        int providerAttempts = 0;
        var it = job.tasks.entrySet().iterator();
        for (int skipped = 0; skipped < job.schedulingCursor && it.hasNext(); skipped++) {
            it.next();
        }
        taskLoop:
        for (int visited = 0; visited < taskVisitBudget && pushedPatterns < maxPatterns; visited++) {
            if (!it.hasNext()) {
                it = job.tasks.entrySet().iterator();
            }
            var task = it.next();
            job.schedulingCursor = (job.schedulingCursor + 1) % taskCount;
            var progress = task.getValue();
            if (progress.value <= 0) {
                // Keep dispatched patterns in the job after their remaining push count reaches zero. Their outputs may
                // still be crafting in a provider, and the crafting-status supplier trace needs the pattern details to
                // resolve that provider. Zero-valued tasks are ignored by input-demand and pending-output accounting.
                continue;
            }

            var details = task.getKey();
            var finalOutputPseudoPattern = isFinalOutputPseudoPattern(job, details);
            // Try to push to each provider.
            for (var provider : providersForPattern.apply(details)) {
                if (providerAttempts >= providerAttemptBudget) {
                    break taskLoop;
                }
                providerAttempts++;
                if (!isDispatchJobCurrent(job) || job.tasks.size() != taskCount) {
                    break taskLoop;
                }
                boolean busy = provider.isBusy();
                if (!isDispatchJobCurrent(job) || job.tasks.size() != taskCount) {
                    break taskLoop;
                }
                if (busy) {
                    continue;
                }

                boolean mergePush = provider.canMergePatternPush(details);
                if (!isDispatchJobCurrent(job) || job.tasks.size() != taskCount) {
                    break taskLoop;
                }
                int pushedMultiplier = tryPushPatternToProvider(job, provider, details, finalOutputPseudoPattern,
                    energyService, level, progress, maxPatterns - pushedPatterns, mergePush);
                // Account for confirmed work before observing cancellation or a replacement job.
                pushedPatterns += pushedMultiplier;
                if (!isDispatchJobCurrent(job) || job.tasks.size() != taskCount) {
                    break taskLoop;
                }
                if (pushedMultiplier <= 0) {
                    continue;
                }
                if (pushedPatterns >= maxPatterns) {
                    break taskLoop;
                }
                continue taskLoop;
            }
        }

        return pushedPatterns;
    }

    private int tryPushPatternToProvider(ExecutingCraftingJob job, ICraftingProvider provider, IPatternDetails details,
                                         boolean finalOutputPseudoPattern, IEnergyService energyService, World level,
                                         ExecutingCraftingJob.TaskProgress progress,
                                         int remainingPatternBudget, boolean mergePush) {
        if (remainingPatternBudget <= 0) {
            return 0;
        }
        if (!mergePush) {
            return tryPushPattern(job, provider, details, finalOutputPseudoPattern, energyService, level, progress, 1);
        }

        int maxMultiplier = (int) Math.min(progress.value, remainingPatternBudget);
        int providerLimit = provider.getMaxPatternPushMultiplier(details, maxMultiplier);
        if (!isDispatchJobCurrent(job)) {
            return 0;
        }
        maxMultiplier = Math.clamp(providerLimit, 0, maxMultiplier);
        ICraftingInventory taskInventory = finalOutputPseudoPattern && job.isTemporaryPattern(details)
            ? new RealInputTrackingInventoryView(inventory)
            : inventory;
        maxMultiplier = CraftingCpuHelper.getMaxExtractablePatternMultiplier(details, taskInventory, level,
            maxMultiplier);
        if (maxMultiplier <= 0) {
            return 0;
        }

        maxMultiplier = getMaxEnergyAffordableMultiplier(
            CraftingCpuHelper.calculatePatternPower(details, 1),
            energyService,
            maxMultiplier);
        if (maxMultiplier <= 0) {
            return 0;
        }

        return tryPushPattern(job, provider, details, finalOutputPseudoPattern, energyService, level, progress,
            maxMultiplier);
    }

    private int tryPushPattern(ExecutingCraftingJob job, ICraftingProvider provider, IPatternDetails details,
                               boolean finalOutputPseudoPattern, IEnergyService energyService, World level,
                               ExecutingCraftingJob.TaskProgress progress,
                               int multiplier) {
        if (multiplier <= 0 || !isDispatchJobCurrent(job)) {
            return 0;
        }

        if (!canAttemptMultiplier(CraftingCpuHelper.calculatePatternPower(details, 1), energyService, multiplier)
            || !isDispatchJobCurrent(job)) {
            return 0;
        }
        var expectedOutputs = new KeyCounter();
        var expectedContainerItems = new KeyCounter();
        ICraftingInventory taskInventory = finalOutputPseudoPattern && job.isTemporaryPattern(details)
            ? new RealInputTrackingInventoryView(inventory)
            : inventory;
        @Nullable
        var craftingContainer = CraftingCpuHelper.extractPatternInputs(details, taskInventory, level,
            expectedOutputs, expectedContainerItems, multiplier);
        if (craftingContainer == null) {
            return 0;
        }

        var patternPower = CraftingCpuHelper.calculatePatternPower(craftingContainer);
        if (!isDispatchJobCurrent(job)
            || energyService.extractAEPower(patternPower, Actionable.SIMULATE,
            PowerMultiplier.CONFIG) < patternPower - 0.01
            || !isDispatchJobCurrent(job)) {
            CraftingCpuHelper.reinjectPatternInputs(taskInventory, craftingContainer);
            return 0;
        }

        if (!provider.pushPattern(details, craftingContainer, multiplier)) {
            CraftingCpuHelper.reinjectPatternInputs(taskInventory, craftingContainer);
            return 0;
        }

        this.confirmedDispatchPatterns += multiplier;
        energyService.extractAEPower(patternPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
        recordPushedPattern(job, details, taskInventory, expectedOutputs, expectedContainerItems);

        cluster.markDirty();

        progress.value -= multiplier;
        invalidatePendingOutputSnapshot();
        if (finalOutputPseudoPattern) {
            completePseudoOutputs(job, expectedOutputs);
        }
        return multiplier;
    }

    private boolean isDispatchJobCurrent(ExecutingCraftingJob job) {
        return this.job == job && !job.link.isCanceled() && !job.link.isDone() && !job.suspended;
    }

    /**
     * Called by the CraftingService with an Integer.MAX_VALUE priority to inject items that are being waited for.
     *
     * @return Consumed amount.
     */
    public long insert(AEKey what, long amount, Actionable type) {
        // also stop accepting items when the job is complete, i.e. to prevent re-insertion when pushing out
        // items during storeItems
        if (returningStandaloneOutput || this.readingState || what == null || job == null || this.hasUnconfirmedInventory())
            return 0;
        if (job.link.isDone() || job.link.isCanceled())
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
            long confirmedDelivery = 0;
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

            // A nested network insert is rejected while the network is currently delivering this output to the CPU.
            // Keep it locally until the next CPU tick, then return it without letting this CPU intercept it again.
            if (job.link.isStandalone()) {
                if (type == Actionable.MODULATE) {
                    inventory.insert(what, amount, Actionable.MODULATE);
                    pendingStandaloneOutput = LongMath.saturatedAdd(pendingStandaloneOutput, amount);
                }
                confirmedDelivery = amount;
            } else {
                // Final output is special: the requester receipt is authoritative. Unaccepted output remains CPU-owned.
                if (type == Actionable.SIMULATE) {
                    job.link.insert(what, amount, type);
                } else {
                    confirmedDelivery = deliverRequesterOutput(job, what, amount, true);
                }
            }

            if (type == Actionable.MODULATE) {
                // Update count and displayed CPU stack, and finish the job if possible.
                postChange(what);
                job.remainingAmount = Math.max(0, job.remainingAmount - confirmedDelivery);

                if (job.remainingAmount <= 0 && job.pendingRequesterOutput == 0 && !job.requesterOutputUnknown) {
                    finishJob(true);
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

    private long deliverRequesterOutput(ExecutingCraftingJob target, AEKey what, long amount,
                                        boolean addRemainderToPending) {
        long accepted;
        try {
            accepted = target.link.insert(what, amount, Actionable.MODULATE);
            if (accepted < 0 || accepted > amount) {
                throw new IllegalStateException("Invalid requester receipt: " + accepted);
            }
        } catch (RuntimeException failure) {
            target.requesterOutputUnknown = true;
            target.suspended = true;
            if (addRemainderToPending) {
                inventory.insert(what, amount, Actionable.MODULATE);
            }
            AELog.warn(failure, "Crafting requester output receipt is unknown; retry is disabled");
            return 0;
        }

        long remainder = amount - accepted;
        if (remainder > 0 && addRemainderToPending) {
            inventory.insert(what, remainder, Actionable.MODULATE);
            target.pendingRequesterOutput = LongMath.saturatedAdd(target.pendingRequesterOutput, remainder);
        }
        return accepted;
    }

    private void retryPendingRequesterOutput(ExecutingCraftingJob target) {
        if (target.link.isStandalone() || target.pendingRequesterOutput <= 0 || target.requesterOutputUnknown) {
            return;
        }
        AEKey what = target.finalOutput.what();
        long amount = Math.min(target.pendingRequesterOutput,
            inventory.extract(what, target.pendingRequesterOutput, Actionable.SIMULATE));
        if (amount <= 0) return;
        long accepted = deliverRequesterOutput(target, what, amount, false);
        if (accepted > 0) {
            inventory.extract(what, accepted, Actionable.MODULATE);
            target.pendingRequesterOutput = Math.max(0, target.pendingRequesterOutput - accepted);
            target.remainingAmount = Math.max(0, target.remainingAmount - accepted);
        }
        if (target.remainingAmount <= 0 && target.pendingRequesterOutput == 0 && !target.requesterOutputUnknown
            && this.job == target) {
            finishJob(true);
        }
    }

    /**
     * Finish the current job.
     *
     * @param success True if the job is complete, false if it was cancelled.
     */
    private void finishJob(boolean success) {
        var finished = this.job;
        if (finished == null || this.finishingJob) {
            return;
        }
        boolean completed = finished.link.isDone() || (success && !finished.link.isCanceled());
        this.finishingJob = true;
        this.job = null;
        invalidatePendingOutputSnapshot();
        this.submissionSavePending = true;
        if (finished.requesterOutputUnknown) {
            this.unknownRequesterOutputKey = finished.finalOutput.what();
        }
        if (this.unknownStandaloneOutputKey == null) {
            this.pendingStandaloneOutput = 0;
        }

        try {
            if (completed) {
                finished.link.markDone();
            } else {
                finished.link.cancel();
            }

            // Menus already observe no job. Notify its keys without clearing detached, non-resource counters.
            for (var entry : finished.waitingFor.list) {
                postChange(entry.getKey());
            }
            for (var entry : finished.pseudoInventory.list) {
                postChange(entry.getKey());
            }
            for (var details : finished.tasks.keySet()) {
                try {
                    for (var output : details.getOutputs()) {
                        postChange(output.what());
                    }
                } catch (RuntimeException failure) {
                    AELog.error(failure, "Failed to notify scheduled outputs of a terminal crafting job");
                }
            }

            var status = completed ? CraftingJobStatusPacket.Status.FINISHED : CraftingJobStatusPacket.Status.CANCELLED;
            try {
                notifyJobOwner(finished, status, completed && finished.subscribed);
            } catch (RuntimeException failure) {
                AELog.error(failure, "Failed to notify the owner of a terminal crafting job");
            }
            if (this.job == null) {
                try {
                    cluster.updateOutput(null);
                } catch (RuntimeException failure) {
                    AELog.error(failure, "Failed to clear the display of a terminal crafting job");
                }
            }
            this.saveCommittedSubmission();
            if (this.job == null) {
                this.storeItems();
            }
        } finally {
            this.finishingJob = false;
        }
    }

    private void returnStandaloneOutputToNetwork() {
        var currentJob = this.job;
        if (currentJob == null || !currentJob.link.isStandalone() || pendingStandaloneOutput <= 0
            || unknownStandaloneOutputKey != null) {
            return;
        }

        var grid = cluster.getGrid();
        if (grid == null) {
            return;
        }

        var what = currentJob.finalOutput.what();
        long amount = Math.min(pendingStandaloneOutput,
            inventory.extract(what, pendingStandaloneOutput, Actionable.SIMULATE));
        if (amount <= 0) {
            pendingStandaloneOutput = 0;
            cluster.markDirty();
            return;
        }

        long inserted;
        returningStandaloneOutput = true;
        try {
            inserted = grid.getStorageService().getInventory().insert(
                what, amount, Actionable.MODULATE, cluster.getSrc());
            if (inserted < 0 || inserted > amount) {
                throw new IllegalStateException("Invalid standalone output receipt: " + inserted);
            }
        } catch (RuntimeException failure) {
            // The external inventory may have committed before throwing. Keep the local escrow and stop retries.
            unknownStandaloneOutputKey = what;
            cluster.markDirty();
            AELog.warn(failure, "Standalone crafting output has an unknown external receipt; retry is disabled");
            return;
        } finally {
            returningStandaloneOutput = false;
        }

        if (inserted > 0) {
            inventory.extract(what, inserted, Actionable.MODULATE);
            pendingStandaloneOutput = Math.max(0, pendingStandaloneOutput - inserted);
        }
        cluster.markDirty();
    }

    /**
     * Cancel the current job.
     */
    public void cancel() {
        if (job == null || this.finishingJob || this.readingState)
            return;
        finishJob(false);
    }

    /**
     * Tries to dump all locally stored items back into the storage network.
     */
    public void storeItems() {
        if (this.returningItems || this.readingState || this.hasUnconfirmedInventory()) {
            return;
        }
        Preconditions.checkState(job == null, "CPU should not have a job to prevent re-insertion when dumping items");
        if (this.inventory.list.isEmpty() || this.cluster.isDestroyed())
            return;
        this.returningItems = true;
        try {
            var grid = cluster.getGrid();
            if (grid == null) {
                return;
            }
            var storage = grid.getStorageService().getInventory();
            var source = cluster.getSrc();
            var keys = new ObjectArrayList<AEKey>();
            for (var entry : this.inventory.list) {
                keys.add(entry.getKey());
            }
            InventoryReturn transfer = null;
            for (var what : keys) {
                if (this.job != null || this.cluster.isDestroyed() || cluster.getGrid() != grid) {
                    break;
                }
                long amount = this.inventory.list.get(what);
                if (amount == 0) {
                    this.inventory.list.remove(what);
                    this.submissionSavePending = true;
                    continue;
                }
                if (amount <= 0 || this.isInventoryUnconfirmed(what)) {
                    continue;
                }
                if (transfer == null) {
                    transfer = new InventoryReturn(grid, this.inventoryGeneration,
                        cluster.getBoundsMin().toLong(), cluster.getBoundsMax().toLong());
                }
                transfer.sequence++;
                transfer.what = what;
                transfer.amount = amount;
                this.inventoryReturn = transfer;
                this.submissionSavePending = true;
                try {
                    long inserted = storage.insert(what, amount, Actionable.MODULATE, source);
                    if (inserted < 0 || inserted > amount) {
                        throw new IllegalStateException("Invalid CPU inventory return receipt: " + inserted);
                    }
                    long current = this.inventory.list.get(what);
                    if (this.cluster.isDestroyed() || transfer.generation != this.inventoryGeneration
                        || transfer.grid.get() != grid
                        || current < inserted) {
                        throw new IllegalStateException("CPU inventory ownership changed during return");
                    }
                    if (inserted > 0) {
                        if (current == inserted) {
                            this.inventory.list.remove(what);
                        } else {
                            this.inventory.list.remove(what, inserted);
                        }
                    }
                    this.inventoryReturn = null;
                } catch (RuntimeException failure) {
                    this.cantStoreItems = true;
                    AELog.error(failure, "CPU inventory return is unconfirmed; automatic retry stopped: "
                        + transfer.batchId + "/" + transfer.sequence);
                    this.saveCommittedSubmission();
                    return;
                }
                this.postChange(what);
            }
            this.saveCommittedSubmission();
        } finally {
            this.returningItems = false;
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

    /**
     * An in-flight or unresolved receipt aliases inventory; it never authorizes another resource transfer.
     */
    public boolean hasUnconfirmedInventoryReturn() {
        return this.inventoryReturn != null || this.savedInventoryReturn != null;
    }

    public boolean hasUnconfirmedInventory() {
        return this.hasUnconfirmedInventoryReturn() || this.unknownStandaloneOutputKey != null
            || this.unknownRequesterOutputKey != null || (this.job != null && this.job.requesterOutputUnknown)
            || this.savedReadFailure != null || this.quarantinedImage != null || this.restoreLinkPending;
    }

    public boolean isInventoryUnconfirmed(AEKey what) {
        return this.savedInventoryReturn != null || this.savedReadFailure != null || this.quarantinedImage != null
            || this.restoreLinkPending
            || (this.inventoryReturn != null && what.equals(this.inventoryReturn.what))
            || (this.job != null && this.job.requesterOutputUnknown && what.equals(this.job.finalOutput.what()))
            || what.equals(this.unknownStandaloneOutputKey) || what.equals(this.unknownRequesterOutputKey);
    }

    /**
     * Diagnostic only, using the existing non-redeemable missing-content item.
     */
    public ItemStack getInventoryRecoveryItem() {
        if (!this.hasUnconfirmedInventory()) {
            return ItemStack.EMPTY;
        }
        var state = new NBTTagCompound();
        this.writeToNBT(state);
        var tag = new NBTTagCompound();
        tag.setInteger("cpu_inventory_recovery_schema", 1);
        tag.setTag("cpu_inventory_recovery", state);
        tag.setString("missing_content_error", "Crafting CPU inventory receipt is unconfirmed; diagnostic only");
        var recovery = AEItems.MISSING_CONTENT.stack();
        recovery.setTagCompound(tag);
        return recovery;
    }

    private void postChange(AEKey what) {
        if (!this.readingState) {
            this.notifyChange(what);
        }
    }

    private void notifyChange(AEKey what) {
        lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        if (this.listeners.isEmpty()) {
            return;
        }
        var snapshot = this.listenerSnapshot;
        if (snapshot == null) {
            snapshot = this.listeners.values().toArray(ChangeListenerRegistration[]::new);
            this.listenerSnapshot = snapshot;
        }
        for (var registration : snapshot) {
            if (!registration.active) {
                continue;
            }
            try {
                registration.listener.accept(what);
                registration.failureLogged = false;
            } catch (RuntimeException failure) {
                if (!registration.failureLogged) {
                    registration.failureLogged = true;
                    AELog.error(failure, "Failed to notify a crafting CPU change listener");
                }
            }
        }
    }

    public void readFromNBT(NBTTagCompound data) {
        if (this.readingState || this.returningItems || this.submitting || this.finishingJob
            || this.executingCrafting || this.tickingCrafting) {
            throw new IllegalStateException("Cannot replace CPU state during an owned operation");
        }
        this.readingState = true;
        try {
            this.restoreOwnedState(data);
        } catch (RuntimeException failure) {
            if (this.savedReadFailure == null && this.quarantinedImage == null) {
                var original = new NBTTagCompound();
                original.setInteger("schema", 1);
                original.setString("phase", this.restoreLinkPending ? "link-publication" : "decode");
                original.setString("error", failure.toString());
                original.setTag("source", data.copy());
                this.savedReadFailure = original;
            }
            this.submissionSavePending = true;
            AELog.error(failure, "CPU state restoration failed; source retained and automatic work stopped");
            throw failure;
        } finally {
            this.readingState = false;
        }
    }

    private void restoreOwnedState(NBTTagCompound data) {
        if (data.hasKey(NBT_READ_FAILURE)) {
            if (this.job != null || !this.inventory.list.isEmpty() || this.hasUnconfirmedInventory()) {
                var current = new NBTTagCompound();
                this.writeToNBT(current);
                if (!sameSavedState(current, data)) {
                    throw new IllegalStateException("Cannot replace quarantined CPU state");
                }
            } else {
                // Quarantined images are opaque; do not repeatedly decode and nest a failed source on every restart.
                this.quarantinedImage = data.copy();
                this.savedReadFailure = data.getTag(NBT_READ_FAILURE).copy();
                this.inventoryGeneration++;
                this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();
            }
            return;
        }
        if (this.savedReadFailure != null || this.quarantinedImage != null || this.restoreLinkPending) {
            throw new IllegalStateException("Cannot discard a failed CPU restore image");
        }

        long revision = this.inventoryRevision;
        var grid = this.cluster.getGrid();
        var preparedInventory = savedResources(data, "inventory");
        requireTag(data, "job", Constants.NBT.TAG_COMPOUND);
        requireTag(data, NBT_UNKNOWN_STANDALONE_OUTPUT, Constants.NBT.TAG_COMPOUND);
        requireTag(data, NBT_UNKNOWN_REQUESTER_OUTPUT, Constants.NBT.TAG_COMPOUND);
        requireTag(data, NBT_PENDING_STANDALONE_OUTPUT, Constants.NBT.TAG_ANY_NUMERIC);
        long pending = Math.max(0, data.getLong(NBT_PENDING_STANDALONE_OUTPUT));
        var standalone = GenericStack.readTag(data.getCompoundTag(NBT_UNKNOWN_STANDALONE_OUTPUT));
        var requester = GenericStack.readTag(data.getCompoundTag(NBT_UNKNOWN_REQUESTER_OUTPUT));
        NBTBase incomingReturn = data.hasKey(NBT_INVENTORY_RETURN) ? data.getTag(NBT_INVENTORY_RETURN).copy() : null;
        NBTBase currentReturn = this.inventoryReturn != null ? this.inventoryReturn.writeToNBT() : this.savedInventoryReturn;
        boolean sameInventory = sameResources(this.inventory.list, preparedInventory);
        if (currentReturn != null && !currentReturn.equals(incomingReturn)) {
            throw new IllegalStateException("Cannot replace an unconfirmed CPU inventory return");
        }
        if ((!this.inventory.list.isEmpty() && !sameInventory)
            || (this.hasUnconfirmedInventory() && (!sameInventory || pending != this.pendingStandaloneOutput
            || !Objects.equals(this.unknownStandaloneOutputKey, standalone == null ? null : standalone.what())
            || !Objects.equals(this.unknownRequesterOutputKey, requester == null ? null : requester.what())))) {
            throw new IllegalStateException("Cannot overwrite CPU-owned inventory during restoration");
        }
        if (this.job != null && !this.job.link.isDone() && !this.job.link.isCanceled()) {
            if (!data.hasKey("job") || !this.job.writeToNBT().equals(data.getCompoundTag("job")) || !sameInventory) {
                throw new IllegalStateException("Cannot replace a live CPU crafting job");
            }
            if (!Objects.equals(currentReturn, incomingReturn) || pending != this.pendingStandaloneOutput
                || !Objects.equals(this.unknownStandaloneOutputKey, standalone == null ? null : standalone.what())
                || !Objects.equals(this.unknownRequesterOutputKey, requester == null ? null : requester.what())) {
                throw new IllegalStateException("Cannot replace live CPU custody metadata");
            }
            return;
        }

        var preparedJob = data.hasKey("job")
            ? new ExecutingCraftingJob(data.getCompoundTag("job"), this::postChange, this) : null;
        var service = preparedJob == null || grid == null ? null : (CraftingService) grid.getCraftingService();
        Set<AEKey> changes = null;
        if (!this.listeners.isEmpty()) {
            changes = new ObjectOpenHashSet<>();
            collectInventoryKeys(changes, this.inventory.list);
            collectInventoryKeys(changes, preparedInventory);
            collectJobKeys(changes, this.job);
            collectJobKeys(changes, preparedJob);
        }
        if (this.inventoryRevision != revision || this.cluster.getGrid() != grid || this.cluster.isDestroyed()) {
            throw new IllegalStateException("CPU ownership changed while decoding saved state");
        }

        // All parsing and callbacks above precede this callback-free publication of the complete owned state.
        this.inventory.list.moveFrom(preparedInventory);
        this.inventoryRevision++;
        this.inventoryGeneration++;
        this.job = preparedJob;
        invalidatePendingOutputSnapshot();
        this.pendingStandaloneOutput = pending;
        this.unknownStandaloneOutputKey = standalone == null ? null : standalone.what();
        this.unknownRequesterOutputKey = requester == null ? null : requester.what();
        this.savedInventoryReturn = incomingReturn;
        this.inventoryReturn = null;
        this.lastModifiedOnTick = TickHandler.instance().getCurrentTick();

        if (preparedJob != null && service != null) {
            this.restoreLinkPending = true;
            service.addLink(preparedJob.link);
            this.restoreLinkPending = false;
        }
        if (changes != null) {
            for (var key : changes) {
                this.notifyChange(key);
            }
        }
        try {
            cluster.updateOutput(preparedJob == null ? null
                : new GenericStack(preparedJob.finalOutput.what(), preparedJob.remainingAmount));
        } catch (RuntimeException failure) {
            AELog.error(failure, "Failed to display restored CPU state");
        }
    }

    public void writeToNBT(NBTTagCompound data) {
        if (this.quarantinedImage != null) {
            for (var key : SAVED_KEYS) {
                if (this.quarantinedImage.hasKey(key)) data.setTag(key, this.quarantinedImage.getTag(key).copy());
                else data.removeTag(key);
            }
            return;
        }
        data.setTag("inventory", this.inventory.writeToNBT());
        if (this.inventoryReturn != null) {
            data.setTag(NBT_INVENTORY_RETURN, this.inventoryReturn.writeToNBT());
        } else if (this.savedInventoryReturn != null) {
            data.setTag(NBT_INVENTORY_RETURN, this.savedInventoryReturn.copy());
        } else {
            data.removeTag(NBT_INVENTORY_RETURN);
        }
        data.setLong(NBT_PENDING_STANDALONE_OUTPUT, pendingStandaloneOutput);
        if (unknownStandaloneOutputKey != null) {
            data.setTag(NBT_UNKNOWN_STANDALONE_OUTPUT,
                GenericStack.writeTag(new GenericStack(unknownStandaloneOutputKey,
                    inventory.list.get(unknownStandaloneOutputKey))));
        } else {
            data.removeTag(NBT_UNKNOWN_STANDALONE_OUTPUT);
        }
        if (unknownRequesterOutputKey != null) {
            data.setTag(NBT_UNKNOWN_REQUESTER_OUTPUT,
                GenericStack.writeTag(new GenericStack(unknownRequesterOutputKey,
                    inventory.list.get(unknownRequesterOutputKey))));
        } else {
            data.removeTag(NBT_UNKNOWN_REQUESTER_OUTPUT);
        }
        if (this.job != null) {
            data.setTag("job", this.job.writeToNBT());
        } else {
            data.removeTag("job");
        }
        if (this.savedReadFailure != null) {
            data.setTag(NBT_READ_FAILURE, this.savedReadFailure.copy());
        } else if (this.restoreLinkPending) {
            var pending = new NBTTagCompound();
            pending.setInteger("schema", 1);
            pending.setString("phase", "link-publication");
            data.setTag(NBT_READ_FAILURE, pending);
        } else {
            data.removeTag(NBT_READ_FAILURE);
        }
    }

    /**
     * Register a listener that will receive stacks when either the stored items, await items or pending outputs change.
     * This is only used by the container. Make sure to remove it by calling {@link #removeListener}.
     * Registrations use listener identity. Removal revokes callbacks that have not started, and a new registration
     * starts with the next notification. Ordinary listener failures do not interrupt inventory changes or other listeners.
     */
    public void addListener(Consumer<AEKey> listener) {
        this.listeners.computeIfAbsent(Objects.requireNonNull(listener), _ -> {
            this.listenerSnapshot = null;
            return new ChangeListenerRegistration(listener);
        });
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

    public void removeListener(Consumer<AEKey> listener) {
        var registration = this.listeners.remove(listener);
        if (registration != null) {
            registration.active = false;
            this.listenerSnapshot = null;
        }
    }

    public long getPendingOutputs(AEKey template) {
        var currentJob = this.job;
        if (currentJob == null) {
            return 0;
        }
        if (this.pendingOutputSnapshotJob != currentJob
            || this.pendingOutputSnapshotRevision != this.taskRevision
            || this.pendingOutputSnapshot == null) {
            var snapshot = KeyCounter.saturating(currentJob.tasks.size());
            for (var task : currentJob.tasks.entrySet()) {
                long value = task.getValue().value;
                if (value <= 0) {
                    continue;
                }
                for (var output : task.getKey().getOutputs()) {
                    snapshot.add(output.what(), LongMath.saturatedMultiply(output.amount(), value));
                }
            }
            this.pendingOutputSnapshotJob = currentJob;
            this.pendingOutputSnapshotRevision = this.taskRevision;
            this.pendingOutputSnapshot = snapshot;
        }
        return this.pendingOutputSnapshot.get(template);
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

    private void invalidatePendingOutputSnapshot() {
        this.taskRevision++;
        this.pendingOutputSnapshot = null;
        this.pendingOutputSnapshotJob = null;
    }

    private static final class InventoryReturn {
        private final UUID batchId = UUID.randomUUID();
        private final WeakReference<IGrid> grid;
        private final long generation;
        private final long sourceMin;
        private final long sourceMax;
        private long sequence;
        private AEKey what;
        private long amount;

        private InventoryReturn(IGrid grid, long generation, long sourceMin, long sourceMax) {
            this.grid = new WeakReference<>(grid);
            this.generation = generation;
            this.sourceMin = sourceMin;
            this.sourceMax = sourceMax;
        }

        private NBTTagCompound writeToNBT() {
            var tag = new NBTTagCompound();
            tag.setInteger("schema", 1);
            tag.setUniqueId("batchId", this.batchId);
            tag.setLong("sequence", this.sequence);
            tag.setLong("sourceGeneration", this.generation);
            tag.setLong("sourceMin", this.sourceMin);
            tag.setLong("sourceMax", this.sourceMax);
            tag.setTag("requested", GenericStack.writeTag(new GenericStack(this.what, this.amount)));
            return tag;
        }
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
                    out.add(output.what(), LongMath.saturatedMultiply(output.amount(), t.getValue().value));
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

    public int getJobPriority() {
        return job != null ? job.priority : 0;
    }

    public void setJobPriority(int priority) {
        if (job != null && job.priority != priority) {
            job.priority = priority;
            cluster.markDirty();
            lastModifiedOnTick = TickHandler.instance().getCurrentTick();
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status,
                                boolean showFinishedToast) {
        notifyJobOwner(job, status, showFinishedToast, List.of());
    }

    private static final class ChangeListenerRegistration {
        private final Consumer<AEKey> listener;
        private boolean active = true;
        private boolean failureLogged;

        private ChangeListenerRegistration(Consumer<AEKey> listener) {
            this.listener = listener;
        }
    }

    private void notifyJobOwner(ExecutingCraftingJob job, CraftingJobStatusPacket.Status status,
                                boolean showFinishedToast, List<AEKey> trackedKeys) {
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
        var finalOutput = Objects.requireNonNull(job.finalOutput);
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
                status, showFinishedToast, trackedKeys), connectedPlayer);
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
