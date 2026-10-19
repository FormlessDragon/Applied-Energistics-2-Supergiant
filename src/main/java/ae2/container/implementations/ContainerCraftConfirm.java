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

package ae2.container.implementations;

import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.CraftingJobOptions;
import ae2.api.networking.crafting.CraftingSubmitErrorCode;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.crafting.UnsuitableCpus;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ISubGuiHost;
import ae2.container.AEBaseContainer;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.container.PendingGridFills;
import ae2.container.guisync.GuiSync;
import ae2.container.guisync.PacketWritable;
import ae2.container.interfaces.ICraftingGridContainer;
import ae2.container.me.crafting.CraftConfirmCpuList;
import ae2.container.me.crafting.CraftingCPUCycler;
import ae2.container.me.crafting.CraftingCPURecord;
import ae2.container.me.crafting.CraftingPlanSummary;
import ae2.core.AELog;
import ae2.core.gui.locator.GuiHostLocator;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.NetworkPacketHelper;
import ae2.core.network.clientbound.CraftConfirmPlanPacket;
import ae2.core.network.serverbound.SwitchGuisPacket;
import ae2.crafting.BatchCraftingPlan;
import ae2.crafting.CraftingCalculationFailure;
import ae2.crafting.TemporaryPseudoCraftingProvider;
import ae2.crafting.execution.CraftingSubmitResult;
import ae2.items.tools.powered.WirelessTerminals;
import ae2.me.helpers.PlayerSource;
import ae2.util.SearchInventoryEvent;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

public class ContainerCraftConfirm extends AEBaseContainer implements ISubGui {
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CYCLE_CPU = "cycleCpu";
    private static final String ACTION_SELECT_CPU_FROM_LIST = "selectCpuFromList";
    private static final String ACTION_START_JOB = "startJob";
    private static final String ACTION_SET_TASK_PRIORITY = "setTaskPriority";
    private static final String ACTION_REPLAN = "replan";
    private static final String ACTION_REQUEST_PLAN = "requestPlan";

    private static final SyncableSubmitResult NO_ERROR = new SyncableSubmitResult((ICraftingSubmitResult) null);

    private final CraftingCPUCycler cpuCycler;
    private final ISubGuiHost host;
    @GuiSync(3)
    public boolean autoStart;
    @GuiSync(6)
    public boolean noCPU = true;
    @GuiSync(1)
    public long cpuBytesAvail;
    @GuiSync(2)
    public int cpuCoProcessors;
    @GuiSync(7)
    @Nullable
    public ITextComponent cpuName;
    @GuiSync(8)
    public SyncableSubmitResult submitError = NO_ERROR;
    @GuiSync(9)
    public boolean mergeAvailable;
    private final Reference2BooleanMap<ICraftingCPU> cachedMergeableCpus = new Reference2BooleanOpenHashMap<>();
    @Nullable
    private ICraftingCPU selectedCpu;
    @Nullable
    private AEKey whatToCraft;
    private long amount;
    @GuiSync(13)
    public String planSession = UUID.randomUUID().toString();
    @Nullable
    private UUID clientPlanRequest = UUID.randomUUID();
    @Nullable
    private UUID serverPlanRequest;
    @Nullable
    private String boundPlanSession;
    private long planRevision;
    private long lastPlanRevision = -1;
    private CalculationStrategy strategy = CalculationStrategy.REPORT_MISSING_ITEMS;
    @Nullable
    private Future<ICraftingPlan> job;
    private Object calculationGeneration = new Object();
    private boolean submitting;
    @Nullable
    private Object submittedGeneration;
    @Nullable
    private IGrid calculationGrid;
    @Nullable
    private ICraftingPlan result;
    @Nullable
    private PendingGridFills.Order pendingFillsOrder;
    @Nullable
    private CraftingPlanSummary plan;
    @Nullable
    private ICraftingPlan cachedMergeResult;
    @Nullable
    private ICraftingCPU cachedMergeCpu;
    private long cachedMergeCpuStateChangeTick = Long.MIN_VALUE;
    @GuiSync(10)
    public CraftConfirmCpuList cpuList = CraftConfirmCpuList.EMPTY;
    @GuiSync(11)
    public int taskPriority;
    @GuiSync(12)
    public boolean canSubscribe;
    @Nullable
    private ICraftingPlan cachedMergeableCpuResult;
    private long cachedMergeableCpuStateChangeTick = Long.MIN_VALUE;
    @Nullable
    private List<ICraftingGridContainer.AutoCraftEntry> autoCraftingQueue;
    /**
     * One calculation per distinct material a batch order is missing. Their results are merged into a single plan.
     */
    @Nullable
    private List<Future<ICraftingPlan>> batchJobs;
    @Nullable
    private List<GenericStack> batchTargets;
    @Nullable
    private TemporaryPseudoCraftingProvider batchMainProvider;

    public ContainerCraftConfirm(InventoryPlayer ip, ISubGuiHost host) {
        super(ip, host);
        this.host = host;
        if (isServerSide()) {
            this.pendingFillsOrder = PendingGridFills.getOrderForHost(ip.player, host, getGrid());
        }
        this.cpuCycler = new CraftingCPUCycler(this::cpuMatches, this::onCPUSelectionChanged);
        this.cpuCycler.setAllowNoSelection(true);

        registerClientAction(ACTION_BACK, this::goBack);
        registerClientAction(ACTION_CYCLE_CPU, Boolean.class, this::cycleSelectedCPU);
        registerClientAction(ACTION_SELECT_CPU_FROM_LIST, Integer.class, this::selectCpu);
        registerClientAction(ACTION_START_JOB, StartJobRequest.class, this::startJob);
        registerClientAction(ACTION_SET_TASK_PRIORITY, Integer.class, this::setTaskPriority);
        registerClientAction(ACTION_REQUEST_PLAN, PlanRequest.class, 256, this::requestPlan);
        registerClientAction(ACTION_REPLAN, PlanRequest.class, 256, this::replan);
    }

    public static void openWithCraftingList(@Nullable IActionHost terminal, EntityPlayerMP player,
                                            @Nullable GuiHostLocator locator, List<ICraftingGridContainer.AutoCraftEntry> stacksToCraft) {
        openWithCraftingList(terminal, player, locator, stacksToCraft, null);
    }

    public static void openWithCraftingList(@Nullable IActionHost terminal, EntityPlayerMP player,
                                            @Nullable GuiHostLocator locator, List<ICraftingGridContainer.AutoCraftEntry> stacksToCraft,
                                            @Nullable Container returnToContainerOverride) {
        if (terminal == null || locator == null || stacksToCraft.isEmpty()) {
            return;
        }

        ICraftingGridContainer.AutoCraftEntry firstToCraft = stacksToCraft.getFirst();
        List<ICraftingGridContainer.AutoCraftEntry> subsequentCrafts = stacksToCraft.subList(1, stacksToCraft.size());

        try {
            SwitchGuisPacket.openSubGui(player, locator, GuiIds.GuiKey.CRAFT_CONFIRM, returnToContainerOverride);

            if (player.openContainer instanceof ContainerCraftConfirm container) {
                if (!container.planJob(firstToCraft.what(), firstToCraft.amount(),
                    CalculationStrategy.REPORT_MISSING_ITEMS)) {
                    container.setValidContainer(false);
                    return;
                }

                container.autoCraftingQueue = subsequentCrafts;
                container.detectAndSendChanges();
            }
        } catch (Throwable e) {
            AELog.info(e);
        }
    }

    /**
     * Opens the confirmation for a batch order of the crafting terminal.
     *
     * <p>Instead of pretending that one pattern is the whole recipe, every distinct material the transfer could not
     * supply is calculated as its own plan against the current network, and the confirmation shows the merged result:
     * one job that crafts all the missing materials at once. The output of the transferred recipe stays the final
     * output of that job, but it is only a placeholder, because the player crafts it by hand.
     */
    public static void openWithBatchCrafting(@Nullable IActionHost terminal, EntityPlayerMP player,
                                             @Nullable GuiHostLocator locator, List<GenericStack> missingMaterials,
                                             TemporaryPseudoCraftingProvider mainProvider) {
        if (terminal == null || locator == null || missingMaterials.isEmpty()) {
            return;
        }

        try {
            SwitchGuisPacket.openSubGui(player, locator, GuiIds.GuiKey.CRAFT_CONFIRM, null);

            if (player.openContainer instanceof ContainerCraftConfirm container) {
                if (!container.planBatchJob(missingMaterials, mainProvider)) {
                    container.setValidContainer(false);
                    return;
                }

                container.detectAndSendChanges();
            }
        } catch (Throwable e) {
            AELog.info(e);
        }
    }

    static boolean canUseCpuForRequest(ICraftingCPU cpu, boolean playerRequest) {
        return switch (cpu.getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> playerRequest;
            case MACHINE_ONLY -> !playerRequest;
        };
    }

    static boolean shouldAutoStart(ICraftingPlan result, boolean autoStart) {
        return autoStart && !result.simulation() && result.missingItems().isEmpty();
    }

    private static ITextComponent getCraftingErrorText(Throwable error) {
        Throwable cause = error instanceof ExecutionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof CraftingCalculationFailure calculationFailure) {
            return calculationFailure.getLocalizedMessageComponent();
        }
        return PlayerMessages.CraftingNoPlan.text();
    }

    private static boolean hasUsableWirelessTerminal(EntityPlayer player) {
        for (var stack : SearchInventoryEvent.getItems(player)) {
            NBTTagCompound tag = stack.getTagCompound();
            if (!stack.isEmpty()
                && stack.getItem() instanceof IAEItemPowerStorage storage
                && storage.getAECurrentPower(stack) > 0
                && tag != null
                && tag.hasKey(WirelessTerminals.TAG_LINK, Constants.NBT.TAG_COMPOUND)) {
                return true;
            }
        }
        return false;
    }

    private static void cancelCalculations(List<Future<ICraftingPlan>> jobs) {
        var detached = List.copyOf(jobs);
        jobs.clear();
        for (var future : detached) {
            cancelCalculation(future);
        }
    }

    private static void cancelCalculation(Future<ICraftingPlan> future) {
        try {
            future.cancel(true);
        } catch (RuntimeException failure) {
            AELog.error(failure, "Failed to cancel crafting confirmation calculation");
        }
    }

    public boolean planJob(AEKey what, long amount, CalculationStrategy strategy) {
        var generation = beginPlan();
        if (generation == null) {
            return false;
        }
        this.whatToCraft = what;
        this.amount = amount;
        this.strategy = strategy;

        IGrid grid = getGrid();
        if (grid == null || this.calculationGeneration != generation) {
            return false;
        }
        this.calculationGrid = grid;

        return startCalculation(() -> grid.getCraftingService().beginCraftingCalculation(getPlayer().world,
            this::getActionSrc, what, amount, strategy));
    }

    @Nullable
    private Object beginPlan() {
        var generation = this.cancelPendingCalculations();
        if (this.calculationGeneration != generation) {
            return null;
        }
        this.result = null;
        this.plan = null;
        this.planRevision++;
        this.mergeAvailable = false;
        invalidateMergeCache();
        invalidateMergeableCpuCache();
        this.clearError();
        sendPlanState();
        return this.calculationGeneration == generation ? generation : null;
    }

    /**
     * Starts one calculation per distinct missing material. Their results are merged into a single plan once they are
     * all done, so one CPU job crafts every missing material at once.
     */
    private boolean planBatchJob(List<GenericStack> missingMaterials, TemporaryPseudoCraftingProvider mainProvider) {
        var generation = beginPlan();
        if (generation == null) {
            return false;
        }
        var mainOutput = mainProvider.pattern().getPrimaryOutput();
        if (this.calculationGeneration != generation) {
            return false;
        }
        this.whatToCraft = mainOutput.what();
        this.amount = mainOutput.amount();
        this.strategy = CalculationStrategy.REPORT_MISSING_ITEMS;

        IGrid grid = getGrid();
        if (grid == null) {
            return false;
        }

        ICraftingService craftingService = grid.getCraftingService();
        var targets = List.copyOf(missingMaterials);
        if (this.calculationGeneration != generation) {
            return false;
        }
        this.calculationGrid = grid;
        this.batchTargets = targets;
        this.batchMainProvider = mainProvider;
        return startBatchCalculations(targets, material -> craftingService.beginCraftingCalculation(
            getPlayer().world, this::getActionSrc, material.what(), material.amount(),
            CalculationStrategy.REPORT_MISSING_ITEMS));
    }

    private boolean startCalculation(Supplier<Future<ICraftingPlan>> starter) {
        var generation = this.calculationGeneration;
        var future = Objects.requireNonNull(starter.get(), "Calculation returned no future");
        if (this.calculationGeneration != generation) {
            cancelCalculation(future);
            return false;
        }
        this.job = future;
        return true;
    }

    private boolean startBatchCalculations(List<GenericStack> targets,
                                           Function<GenericStack, Future<ICraftingPlan>> starter) {
        var generation = this.calculationGeneration;
        var jobs = new ObjectArrayList<Future<ICraftingPlan>>(targets.size());
        this.batchJobs = jobs;
        boolean complete = false;
        try {
            for (var material : targets) {
                if (this.calculationGeneration != generation || this.batchJobs != jobs) {
                    return false;
                }
                var future = Objects.requireNonNull(starter.apply(material), "Calculation returned no future");
                if (this.calculationGeneration != generation || this.batchJobs != jobs) {
                    cancelCalculation(future);
                    return false;
                }
                jobs.add(future);
            }
            complete = true;
            return true;
        } finally {
            if (!complete) {
                if (this.batchJobs == jobs) {
                    this.batchJobs = null;
                    this.batchTargets = null;
                    this.batchMainProvider = null;
                }
                cancelCalculations(jobs);
            }
        }
    }

    private Object cancelPendingCalculations() {
        var generation = new Object();
        this.calculationGeneration = generation;
        this.calculationGrid = null;
        var job = this.job;
        var jobs = this.batchJobs;
        this.job = null;
        this.batchJobs = null;
        this.batchTargets = null;
        this.batchMainProvider = null;
        // Detach all ownership before any extension cancellation callback can install new work.
        if (job != null) {
            cancelCalculation(job);
        }
        if (jobs != null) {
            cancelCalculations(jobs);
        }
        return generation;
    }

    public void cycleSelectedCPU(boolean next) {
        if (isClientSide()) {
            sendClientAction(ACTION_CYCLE_CPU, next);
        } else {
            this.cpuCycler.cycleCpu(next);
            IGrid grid = getGrid();
            if (grid != null) {
                syncCpuList(grid);
            }
        }
    }

    public void selectCpu(int serial) {
        if (isClientSide()) {
            updateSelectedCpuClientSide(serial);
            this.cpuList = this.cpuList.withSelectedCpu(serial);
            sendClientAction(ACTION_SELECT_CPU_FROM_LIST, serial);
        } else {
            this.cpuCycler.selectCpu(serial);
            IGrid grid = getGrid();
            if (grid != null) {
                this.mergeAvailable = canMergeCurrentResult(grid);
                syncCpuList(grid);
            }
        }
    }

    private void updateSelectedCpuClientSide(int serial) {
        if (serial == -1) {
            this.cpuBytesAvail = 0;
            this.cpuCoProcessors = 0;
            this.cpuName = null;
            return;
        }

        for (CraftConfirmCpuList.Entry entry : this.cpuList.cpus()) {
            if (entry.serial() == serial) {
                this.noCPU = false;
                this.cpuBytesAvail = entry.storage();
                this.cpuCoProcessors = entry.coProcessors();
                this.cpuName = entry.name();
                this.mergeAvailable = entry.mergeable();
                return;
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (isClientSide()) {
            return;
        }

        var generation = this.calculationGeneration;
        IGrid grid = this.getGrid();
        if (this.calculationGeneration != generation) {
            return;
        }
        if (grid == null || this.calculationGrid != null && this.calculationGrid != grid) {
            this.invalidateCalculationSource();
            return;
        }

        var batchJobs = this.batchJobs;
        if (batchJobs != null) {
            try {
                var subPlans = readCompletedCalculations(generation, batchJobs);
                if (subPlans != null) {
                    var mainProvider = this.batchMainProvider;
                    if (mainProvider == null) {
                        throw new IllegalStateException("Batch order is missing its placeholder output provider");
                    }
                    var completed = BatchCraftingPlan.combine(mainProvider.pattern().getPrimaryOutput(), subPlans,
                        List.of(mainProvider));
                    if (!isCalculationCurrent(generation, grid) || this.batchJobs != batchJobs) {
                        return;
                    }
                    this.batchJobs = null;
                    if (!publishCalculation(completed, generation, grid, true)) {
                        return;
                    }
                }
            } catch (Exception failure) {
                if (!isCalculationCurrent(generation, grid)) {
                    return;
                }
                failCalculation(failure);
                return;
            }
            if (!isCalculationCurrent(generation, grid)) {
                return;
            }
        }

        var job = this.job;
        if (job != null) {
            try {
                var completed = readCompletedCalculations(generation, List.of(job));
                if (completed != null) {
                    if (!isCalculationCurrent(generation, grid) || this.job != job) {
                        return;
                    }
                    this.job = null;
                    if (!publishCalculation(completed.getFirst(), generation, grid, false)) {
                        return;
                    }
                }
            } catch (Exception failure) {
                if (!isCalculationCurrent(generation, grid)) {
                    return;
                }
                if (this.job == job) {
                    this.job = null;
                }
                failCalculation(failure);
                return;
            }
            if (!isCalculationCurrent(generation, grid)) {
                return;
            }
        }
        ensureMergeableCpuCache(grid);
        this.cpuCycler.detectAndSendChanges(grid);
        this.mergeAvailable = canMergeCurrentResult(grid);
        this.canSubscribe = hasUsableWirelessTerminal(this.getPlayer());
        syncCpuList(grid);
        super.broadcastChanges();
    }

    @Nullable
    private List<ICraftingPlan> readCompletedCalculations(Object generation, List<Future<ICraftingPlan>> jobs)
        throws ExecutionException, InterruptedException {
        // Cancellation can clear the owned mutable batch while a Future invokes an owner callback.
        var snapshot = List.copyOf(jobs);
        for (var future : snapshot) {
            if (this.calculationGeneration != generation) {
                return null;
            }
            boolean done = future.isDone();
            if (this.calculationGeneration != generation || !done) {
                return null;
            }
        }
        var plans = new ObjectArrayList<ICraftingPlan>(snapshot.size());
        for (var future : snapshot) {
            if (this.calculationGeneration != generation) {
                return null;
            }
            var completed = future.get();
            if (this.calculationGeneration != generation) {
                return null;
            }
            plans.add(Objects.requireNonNull(completed, "Calculation returned no plan"));
        }
        return plans;
    }

    private boolean isCalculationCurrent(Object generation, IGrid grid) {
        if (this.calculationGeneration != generation || this.calculationGrid != grid || !isValidContainer()) {
            return false;
        }
        var currentGrid = getGrid();
        return this.calculationGeneration == generation && this.calculationGrid == grid
            && currentGrid == grid && isValidContainer();
    }

    private boolean publishCalculation(ICraftingPlan completed, Object generation, IGrid grid, boolean batch) {
        boolean autoStart = shouldAutoStart(completed, this.isAutoStart());
        var missing = completed.missingItems();
        if (!isCalculationCurrent(generation, grid)) {
            return false;
        }
        this.result = completed;
        if (autoStart) {
            this.startJob(false, true);
            return false;
        }
        if (!missing.isEmpty()) {
            this.setAutoStart(false);
            if (batch) {
                PendingGridFills.dropUnavailable(getPlayer(), this.pendingFillsOrder, missing);
            }
        }
        if (!isCalculationCurrent(generation, grid) || this.result != completed) {
            return false;
        }
        var summary = CraftingPlanSummary.fromJob(grid, completed);
        if (!isCalculationCurrent(generation, grid) || this.result != completed) {
            return false;
        }
        boolean mergeable = canMergeCurrentResult(grid);
        if (!isCalculationCurrent(generation, grid) || this.result != completed) {
            return false;
        }
        this.plan = summary;
        this.mergeAvailable = mergeable;
        sendPlanState();
        return isCalculationCurrent(generation, grid);
    }

    private void invalidateCalculationSource() {
        this.result = null;
        this.plan = null;
        this.mergeAvailable = false;
        this.setValidContainer(false);
        this.cancelPendingCalculations();
    }

    private void failCalculation(Exception failure) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        this.result = null;
        this.plan = null;
        invalidateMergeCache();
        this.setValidContainer(false);
        this.cancelPendingCalculations();
        ITextComponent error = getCraftingErrorText(failure);
        this.getPlayerInventory().player.sendMessage(PlayerMessages.CraftingJobError.text(error));
        AELog.error(failure, "Failed to finish crafting calculation");
    }

    private void syncCpuList(IGrid grid) {
        ensureMergeableCpuCache(grid);
        this.cpuList = CraftConfirmCpuList.fromRecords(
            this.cpuCycler.cpus(),
            this.cpuCycler.getSelectedCpuSerial(),
            this::isCachedMergeableCpu);
    }

    @Nullable
    private IGrid getGrid() {
        IActionHost actionHost = getActionHost();
        if (actionHost == null) {
            return null;
        }
        IGridNode node = actionHost.getActionableNode();
        return node != null ? node.grid() : null;
    }

    private boolean cpuMatches(ICraftingCPU cpu) {
        if (!canUseCpuForRequest(cpu, getPlayer() != null)) {
            return false;
        }
        if (this.plan == null) {
            return true;
        }
        return cpu.getAvailableStorage() >= this.plan.usedBytes() && !cpu.isBusy()
            || isCachedMergeableCpu(cpu);
    }

    static boolean cpuMatchesPlan(ICraftingCPU cpu, CraftingPlanSummary plan, @Nullable ICraftingPlan result) {
        return (cpu.getAvailableStorage() >= plan.usedBytes() && !cpu.isBusy())
            || (result != null && cpu.canMergeJob(result));
    }

    private void ensureMergeableCpuCache(IGrid grid) {
        ICraftingPlan currentResult = this.result;
        long cpuStateChangeTick = grid.getCraftingService().getCraftingCpuStateChangeTick();
        if (this.cachedMergeableCpuResult == currentResult
            && this.cachedMergeableCpuStateChangeTick == cpuStateChangeTick) {
            return;
        }

        this.cachedMergeableCpuResult = currentResult;
        this.cachedMergeableCpuStateChangeTick = cpuStateChangeTick;
        this.cachedMergeableCpus.clear();
    }

    private boolean isCachedMergeableCpu(ICraftingCPU cpu) {
        ICraftingPlan currentResult = this.cachedMergeableCpuResult;
        if (currentResult == null || currentResult.simulation()) {
            return false;
        }
        if (!this.cachedMergeableCpus.containsKey(cpu)) {
            this.cachedMergeableCpus.put(cpu, cpu.canMergeJob(currentResult));
        }
        return this.cachedMergeableCpus.getBoolean(cpu);
    }

    private boolean canMergeCurrentResult(IGrid grid) {
        if (this.result == null || this.result.simulation()) {
            return false;
        }
        ICraftingService craftingService = grid.getCraftingService();
        long cpuStateChangeTick = craftingService.getCraftingCpuStateChangeTick();
        if (this.cachedMergeResult == this.result
            && this.cachedMergeCpu == this.selectedCpu
            && this.cachedMergeCpuStateChangeTick == cpuStateChangeTick) {
            return this.mergeAvailable;
        }

        this.cachedMergeResult = this.result;
        this.cachedMergeCpu = this.selectedCpu;
        this.cachedMergeCpuStateChangeTick = cpuStateChangeTick;
        if (this.selectedCpu != null) {
            return this.selectedCpu.canMergeJob(this.result);
        }
        return craftingService.canMergeJob(this.result, this.getActionSrc());
    }

    private void invalidateMergeCache() {
        this.cachedMergeResult = null;
        this.cachedMergeCpu = null;
        this.cachedMergeCpuStateChangeTick = Long.MIN_VALUE;
    }

    private void invalidateMergeableCpuCache() {
        this.cachedMergeableCpuResult = null;
        this.cachedMergeableCpuStateChangeTick = Long.MIN_VALUE;
        this.cachedMergeableCpus.clear();
    }

    public void setTaskPriority(int priority) {
        if (isClientSide()) {
            this.taskPriority = priority;
            sendClientAction(ACTION_SET_TASK_PRIORITY, priority);
        } else {
            this.taskPriority = priority;
        }
    }

    public void startJob() {
        startJob(false, false, false);
    }

    public void startJob(boolean forceStart) {
        startJob(forceStart, false, false);
    }

    public void startJob(boolean forceStart, boolean skipMerge) {
        startJob(forceStart, skipMerge, false);
    }

    public void startJob(boolean forceStart, boolean skipMerge, boolean subscribed) {
        if (isClientSide()) {
            clearError();
            sendClientAction(ACTION_START_JOB, new StartJobRequest(forceStart, skipMerge, this.taskPriority, subscribed));
            return;
        }

        if (this.submitting || this.submittedGeneration != null
            && this.submittedGeneration == this.calculationGeneration) {
            return;
        }
        this.submitting = true;
        try {
            clearError();
            startServerJob(forceStart, skipMerge, subscribed);
        } finally {
            this.submitting = false;
        }
    }

    private void startServerJob(boolean forceStart, boolean skipMerge, boolean subscribed) {
        var generation = this.calculationGeneration;
        var completed = this.result;
        if (completed != null && !completed.simulation()) {
            if (!forceStart && !completed.missingItems().isEmpty()) {
                return;
            }
            IGrid grid = getGrid();
            if (grid == null || !isCalculationCurrent(generation, grid) || this.result != completed) {
                if (this.calculationGeneration == generation) {
                    this.invalidateCalculationSource();
                }
                return;
            }
            ICraftingService craftingService = grid.getCraftingService();
            var source = this.getActionSrc();
            var options = new CraftingJobOptions(this.taskPriority, subscribed);
            if (!isCalculationCurrent(generation, grid) || this.result != completed) {
                return;
            }
            this.submittedGeneration = generation;
            ICraftingSubmitResult submitResult;
            boolean successful;
            try (var fillsSubmission = PendingGridFills.beginSubmission(this.pendingFillsOrder)) {
                submitResult = craftingService.submitJob(completed, null, this.selectedCpu, true,
                    source, forceStart, skipMerge, options);
                successful = submitResult.successful();
                if (fillsSubmission != null) {
                    fillsSubmission.complete(successful);
                }
            } catch (RuntimeException failure) {
                failSubmission(generation, completed, failure);
                return;
            }
            if (!isSubmissionCurrent(generation, grid, completed)) {
                return;
            }
            if (successful) {
                this.setAutoStart(false);
                boolean hasQueuedJobs = this.autoCraftingQueue != null && !this.autoCraftingQueue.isEmpty();
                EntityPlayer player = getPlayer();
                if (!isSubmissionCurrent(generation, grid, completed)) {
                    return;
                }
                if (hasQueuedJobs) {
                    if (player instanceof EntityPlayerMP serverPlayer) {
                        var actionHost = getActionHost();
                        var locator = getLocator();
                        var returnTo = getReturnToContainerOverride();
                        if (isSubmissionCurrent(generation, grid, completed)) {
                            ContainerCraftConfirm.openWithCraftingList(actionHost, serverPlayer, locator,
                                this.autoCraftingQueue, returnTo);
                        }
                    }
                } else if (!(player instanceof EntityPlayerMP serverPlayer)
                    || !SwitchGuisPacket.restoreExternalGui(serverPlayer)) {
                    if (isSubmissionCurrent(generation, grid, completed)) {
                        this.host.returnToMainContainer(player, this);
                    }
                }
            } else {
                GenericStack output;
                CraftingSubmitErrorCode errorCode;
                Object errorDetail;
                try {
                    output = completed.finalOutput();
                    if (!isSubmissionCurrent(generation, grid, completed)) {
                        return;
                    }
                    errorCode = Objects.requireNonNull(submitResult.errorCode());
                    if (!isSubmissionCurrent(generation, grid, completed)) {
                        return;
                    }
                    errorDetail = submitResult.errorDetail();
                } catch (RuntimeException failure) {
                    failSubmission(generation, completed, failure);
                    return;
                }
                if (!isSubmissionCurrent(generation, grid, completed)) {
                    return;
                }
                this.submitError = new SyncableSubmitResult(new CraftingSubmitResult(null, errorCode, errorDetail));
                this.setAutoStart(false);
                this.submittedGeneration = null;
                AELog.info("Couldn't submit crafting job for %dx%s: %s [Detail: %s]",
                    output.amount(), output.what(), errorCode, errorDetail);
            }
        }
    }

    private boolean isSubmissionCurrent(Object generation, IGrid grid, ICraftingPlan completed) {
        return isCalculationCurrent(generation, grid) && this.result == completed;
    }

    private void failSubmission(Object generation, ICraftingPlan completed, RuntimeException failure) {
        if (this.calculationGeneration == generation && this.result == completed) {
            this.setAutoStart(false);
            this.setValidContainer(false);
        }
        AELog.error(failure, "Crafting confirmation submission is unconfirmed; automatic retry stopped");
    }

    private void startJob(StartJobRequest request) {
        this.taskPriority = request.priority;
        startJob(request.forceStart, request.skipMerge, request.subscribed && hasUsableWirelessTerminal(this.getPlayer()));
    }

    private IActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInventory().player, getActionHost());
    }

    @Override
    public void onContainerClosed(@NotNull EntityPlayer player) {
        var order = this.pendingFillsOrder;
        super.onContainerClosed(player);
        this.cancelPendingCalculations();
        PendingGridFills.scheduleAbandonCheck(player, order);
    }

    private void onCPUSelectionChanged(@Nullable CraftingCPURecord cpuRecord, boolean cpusAvailable) {
        this.noCPU = !cpusAvailable;

        if (cpuRecord == null) {
            this.cpuBytesAvail = 0;
            this.cpuCoProcessors = 0;
            this.cpuName = null;
            this.selectedCpu = null;
        } else {
            this.cpuBytesAvail = cpuRecord.getSize();
            this.cpuCoProcessors = cpuRecord.getProcessors();
            this.cpuName = cpuRecord.getName();
            this.selectedCpu = cpuRecord.getCpu();
        }
        invalidateMergeCache();
    }

    public World getLevel() {
        return this.getPlayerInventory().player.world;
    }

    public boolean isAutoStart() {
        return this.autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public long getCpuAvailableBytes() {
        return this.cpuBytesAvail;
    }

    public int getCpuCoProcessors() {
        return this.cpuCoProcessors;
    }

    @Nullable
    public ITextComponent getName() {
        return this.cpuName;
    }

    public boolean hasNoCPU() {
        return this.noCPU;
    }

    public boolean canMerge() {
        return this.mergeAvailable;
    }

    public void setJob(@Nullable Future<ICraftingPlan> job) {
        setJob(job, getGrid());
    }

    /**
     * Adopts a transferred calculation without rebinding it to the receiving network.
     */
    public void setJob(@Nullable Future<ICraftingPlan> job, @Nullable IGrid source) {
        setJob(job, source, this.pendingFillsOrder);
    }

    public void setJob(@Nullable Future<ICraftingPlan> job, @Nullable IGrid source,
                       @Nullable PendingGridFills.Order order) {
        var generation = beginPlan();
        if (generation == null) {
            if (job != null) {
                cancelCalculation(job);
            }
            return;
        }
        var currentGrid = getGrid();
        if (this.calculationGeneration != generation || source != currentGrid) {
            if (this.calculationGeneration == generation) {
                this.invalidateCalculationSource();
            }
            if (job != null) {
                cancelCalculation(job);
            }
            return;
        }
        this.calculationGrid = source;
        this.pendingFillsOrder = order;
        this.job = job;
    }

    @Nullable
    public PendingGridFills.Order getPendingFillsOrder() {
        return this.pendingFillsOrder;
    }

    @Nullable
    public CraftingPlanSummary getPlan() {
        return this.plan;
    }

    public void setPlan(@Nullable CraftingPlanSummary plan) {
        this.plan = plan;
    }

    @Override
    public void onClientDataSync(ShortSet updatedFields) {
        super.onClientDataSync(updatedFields);
        if (updatedFields.contains((short) 13) && this.boundPlanSession == null && this.clientPlanRequest != null) {
            // A stale introduction can only elicit a rejected request, never bind this client to an old window.
            sendClientAction(ACTION_REQUEST_PLAN, new PlanRequest(this.planSession, null, this.clientPlanRequest));
        }
    }

    private void requestPlan(PlanRequest request) {
        if (!this.planSession.equals(request.session()) || request.request() == null
            || request.previousRequest() != null
            || this.serverPlanRequest != null && !this.serverPlanRequest.equals(request.request())) {
            return;
        }
        this.serverPlanRequest = request.request();
        sendPlanState();
    }

    private void sendPlanState() {
        if (this.serverPlanRequest != null) {
            sendPacketToClient(new CraftConfirmPlanPacket(this.windowId, this.planSession, this.serverPlanRequest,
                this.planRevision, this.plan));
        }
    }

    public void receivePlan(String session, UUID request, long revision, @Nullable CraftingPlanSummary plan) {
        if (!request.equals(this.clientPlanRequest)
            || this.boundPlanSession != null && !this.boundPlanSession.equals(session)
            || revision < 0 || revision < this.lastPlanRevision
            || revision == this.lastPlanRevision && this.plan != null) {
            return;
        }
        this.boundPlanSession = session;
        this.lastPlanRevision = revision;
        this.plan = plan;
    }

    @Nullable
    private PlanRequest beginClientReplan() {
        if (this.boundPlanSession == null || this.clientPlanRequest == null || this.plan == null) {
            return null;
        }
        var previous = this.clientPlanRequest;
        this.clientPlanRequest = UUID.randomUUID();
        this.lastPlanRevision = -1;
        this.plan = null;
        this.mergeAvailable = false;
        return new PlanRequest(this.boundPlanSession, previous, this.clientPlanRequest);
    }

    private void replan(PlanRequest request) {
        if (!this.planSession.equals(request.session()) || this.serverPlanRequest == null
            || !this.serverPlanRequest.equals(request.previousRequest()) || request.request() == null
            || this.serverPlanRequest.equals(request.request())) {
            return;
        }
        this.serverPlanRequest = request.request();
        replan();
    }

    public void replan() {
        clearError();

        if (isClientSide()) {
            var request = beginClientReplan();
            if (request != null) {
                sendClientAction(ACTION_REPLAN, request);
            }
            return;
        }

        var batchTargets = this.batchTargets;
        var batchMainProvider = this.batchMainProvider;
        if (batchTargets != null && batchMainProvider != null) {
            if (!planBatchJob(batchTargets, batchMainProvider)) {
                goBack();
            }
            return;
        }

        if (this.whatToCraft != null) {
            if (!planJob(this.whatToCraft, this.amount, this.strategy)) {
                goBack();
            }
        } else {
            goBack();
        }
    }

    public void goBack() {
        clearError();

        EntityPlayer player = getPlayerInventory().player;
        if (player instanceof EntityPlayerMP serverPlayer) {
            if (this.autoCraftingQueue != null && !this.autoCraftingQueue.isEmpty()) {
                ContainerCraftConfirm.openWithCraftingList(getActionHost(), serverPlayer, getLocator(),
                    this.autoCraftingQueue, getReturnToContainerOverride());
            } else if (this.whatToCraft != null) {
                ContainerCraftAmount.open(serverPlayer, getLocator(), this.whatToCraft, this.amount,
                    getReturnToContainerOverride());
            } else {
                this.host.returnToMainContainer(getPlayer(), this);
            }
        } else {
            sendClientAction(ACTION_BACK);
        }
    }

    @Override
    public ISubGuiHost getHost() {
        return this.host;
    }

    /**
     * Returns the live network that produced the current result, before a GUI transition closes this owner.
     */
    @Nullable
    public IGrid getResultGrid() {
        var generation = this.calculationGeneration;
        var source = this.calculationGrid;
        return source != null && this.result != null && isCalculationCurrent(generation, source) ? source : null;
    }

    public void clearError() {
        this.submitError = NO_ERROR;
    }

    public record PlanRequest(String session, @Nullable UUID previousRequest, UUID request) {
    }

    @Nullable
    public ICraftingPlan getResult() {
        return this.result;
    }

    public record SyncableSubmitResult(@Nullable ICraftingSubmitResult result) implements PacketWritable {

        @SuppressWarnings("unused")
        public SyncableSubmitResult(ByteBuf data) {
            this(readFromPacket(new PacketBuffer(data)));
        }

        @Nullable
        private static ICraftingSubmitResult readFromPacket(PacketBuffer buffer) {
            if (!buffer.readBoolean()) {
                return null;
            }

            if (buffer.readBoolean()) {
                return CraftingSubmitResult.successful(null);
            }

            CraftingSubmitErrorCode errorCode = NetworkPacketHelper.readEnumOrNull(buffer,
                CraftingSubmitErrorCode.class);
            if (errorCode == null) {
                throw new IllegalArgumentException("Invalid crafting submit error code");
            }
            return switch (errorCode) {
                case NO_SUITABLE_CPU_FOUND -> CraftingSubmitResult.noSuitableCpu(new UnsuitableCpus(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt()));
                case MISSING_INGREDIENT -> CraftingSubmitResult.missingIngredient(GenericStack.readBuffer(buffer));
                default -> CraftingSubmitResult.simpleError(errorCode);
            };
        }

        @Override
        public void writeToPacket(ByteBuf data) {
            PacketBuffer buffer = new PacketBuffer(data);
            if (this.result == null) {
                buffer.writeBoolean(false);
                return;
            }

            buffer.writeBoolean(true);
            buffer.writeBoolean(this.result.successful());
            if (!this.result.successful()) {
                CraftingSubmitErrorCode errorCode = Objects.requireNonNull(this.result.errorCode());
                buffer.writeEnumValue(errorCode);
                switch (errorCode) {
                    case NO_SUITABLE_CPU_FOUND -> {
                        UnsuitableCpus unsuitableCpus = Objects.requireNonNull((UnsuitableCpus) this.result.errorDetail());
                        buffer.writeInt(unsuitableCpus.offline());
                        buffer.writeInt(unsuitableCpus.busy());
                        buffer.writeInt(unsuitableCpus.tooSmall());
                        buffer.writeInt(unsuitableCpus.excluded());
                    }
                    case MISSING_INGREDIENT -> {
                        GenericStack missingIngredient = Objects.requireNonNull((GenericStack) this.result.errorDetail());
                        GenericStack.writeBuffer(missingIngredient, buffer);
                    }
                    default -> {
                    }
                }
            }
        }

    }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class StartJobRequest {
        private final boolean forceStart;
        private final boolean skipMerge;
        private final int priority;
        private final boolean subscribed;

        private StartJobRequest(boolean forceStart, boolean skipMerge, int priority, boolean subscribed) {
            this.forceStart = forceStart;
            this.skipMerge = skipMerge;
            this.priority = priority;
            this.subscribed = subscribed;
        }
    }
}
