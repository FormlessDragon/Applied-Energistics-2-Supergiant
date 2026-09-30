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
import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ContainerCraftConfirm extends AEBaseContainer implements ISubGui {
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CYCLE_CPU = "cycleCpu";
    private static final String ACTION_SELECT_CPU_FROM_LIST = "selectCpuFromList";
    private static final String ACTION_START_JOB = "startJob";
    private static final String ACTION_SET_TASK_PRIORITY = "setTaskPriority";
    private static final String ACTION_REPLAN = "replan";

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
    private CalculationStrategy strategy = CalculationStrategy.REPORT_MISSING_ITEMS;
    @Nullable
    private Future<ICraftingPlan> job;
    @Nullable
    private ICraftingPlan result;
    /**
     * True once this confirmation submitted its plan. Only then do the crafting grid slots a batch order wants to fill
     * outlive this container.
     */
    private boolean jobStarted;
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
        this.cpuCycler = new CraftingCPUCycler(this::cpuMatches, this::onCPUSelectionChanged);
        this.cpuCycler.setAllowNoSelection(true);

        registerClientAction(ACTION_BACK, this::goBack);
        registerClientAction(ACTION_CYCLE_CPU, Boolean.class, this::cycleSelectedCPU);
        registerClientAction(ACTION_SELECT_CPU_FROM_LIST, Integer.class, this::selectCpu);
        registerClientAction(ACTION_START_JOB, StartJobRequest.class, this::startJob);
        registerClientAction(ACTION_SET_TASK_PRIORITY, Integer.class, this::setTaskPriority);
        registerClientAction(ACTION_REPLAN, this::replan);
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
     * one job that crafts all of the missing materials at once. The output of the transferred recipe stays the final
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

    public boolean planJob(AEKey what, long amount, CalculationStrategy strategy) {
        this.cancelPendingCalculations();
        this.result = null;
        this.mergeAvailable = false;
        invalidateMergeCache();
        invalidateMergeableCpuCache();
        this.clearError();
        this.whatToCraft = what;
        this.amount = amount;
        this.strategy = strategy;

        IGrid grid = getGrid();
        if (grid == null) {
            return false;
        }

        this.job = grid.getCraftingService().beginCraftingCalculation(getPlayer().world, this::getActionSrc, what,
            amount, strategy);
        return true;
    }

    /**
     * Starts one calculation per distinct missing material. Their results are merged into a single plan once they are
     * all done, so one CPU job crafts every missing material at once.
     */
    private boolean planBatchJob(List<GenericStack> missingMaterials, TemporaryPseudoCraftingProvider mainProvider) {
        this.cancelPendingCalculations();
        this.result = null;
        this.mergeAvailable = false;
        invalidateMergeCache();
        invalidateMergeableCpuCache();
        this.clearError();

        var mainOutput = mainProvider.pattern().getPrimaryOutput();
        this.whatToCraft = mainOutput.what();
        this.amount = mainOutput.amount();
        this.strategy = CalculationStrategy.REPORT_MISSING_ITEMS;

        IGrid grid = getGrid();
        if (grid == null) {
            return false;
        }

        ICraftingService craftingService = grid.getCraftingService();
        var jobs = new ObjectArrayList<Future<ICraftingPlan>>(missingMaterials.size());
        for (var material : missingMaterials) {
            jobs.add(craftingService.beginCraftingCalculation(getPlayer().world, this::getActionSrc,
                material.what(), material.amount(), CalculationStrategy.REPORT_MISSING_ITEMS));
        }

        this.batchJobs = jobs;
        this.batchTargets = List.copyOf(missingMaterials);
        this.batchMainProvider = mainProvider;
        return true;
    }

    private void cancelPendingCalculations() {
        if (this.job != null) {
            this.job.cancel(true);
            this.job = null;
        }
        this.batchJobs = null;
        this.batchTargets = null;
        this.batchMainProvider = null;
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

        IGrid grid = this.getGrid();
        if (grid == null) {
            this.setValidContainer(false);
            return;
        }

        if (this.batchJobs != null && allBatchJobsDone(this.batchJobs)) {
            var batchJobs = this.batchJobs;
            this.batchJobs = null;
            try {
                var subPlans = new ObjectArrayList<ICraftingPlan>(batchJobs.size());
                for (var batchJob : batchJobs) {
                    subPlans.add(batchJob.get());
                }
                var mainProvider = this.batchMainProvider;
                if (mainProvider == null) {
                    throw new IllegalStateException("Batch order is missing its placeholder output provider");
                }

                this.result = BatchCraftingPlan.combine(mainProvider.pattern().getPrimaryOutput(), subPlans,
                    List.of(mainProvider));
                if (shouldAutoStart(this.result, this.isAutoStart())) {
                    this.startJob(false, true);
                    return;
                }

                if (!this.result.missingItems().isEmpty()) {
                    this.setAutoStart(false);
                    // Slots whose material can never be obtained are dropped, so the terminal stops showing slots that
                    // nothing will ever be put into.
                    PendingGridFills.dropUnavailable(getPlayer(), this.result.missingItems());
                }

                this.plan = CraftingPlanSummary.fromJob(grid, this.result);
                this.mergeAvailable = canMergeCurrentResult(grid);
                sendPacketToClient(new CraftConfirmPlanPacket(this.plan));
            } catch (Throwable e) {
                ITextComponent error = getCraftingErrorText(e);
                this.getPlayerInventory().player.sendMessage(PlayerMessages.CraftingJobError.text(error));
                AELog.warn("Failed to start crafting job.", e);
                this.setValidContainer(false);
                this.result = null;
                this.batchTargets = null;
                this.batchMainProvider = null;
                invalidateMergeCache();
                return;
            }
        }

        if (this.job != null && this.job.isDone()) {
            try {
                this.result = this.job.get();
                if (this.result == null) {
                    this.getPlayerInventory().player.sendMessage(PlayerMessages.CraftingJobError.text(
                        PlayerMessages.CraftingNoPlan.text()));
                    AELog.warn(PlayerMessages.CraftingNoPlan.getTranslationKey());
                    this.setValidContainer(false);
                    this.job = null;
                    return;
                }

                if (shouldAutoStart(this.result, this.isAutoStart())) {
                    this.startJob(false, true);
                    return;
                }

                if (!this.result.missingItems().isEmpty()) {
                    this.setAutoStart(false);
                }

                this.plan = CraftingPlanSummary.fromJob(grid, this.result);
                this.mergeAvailable = canMergeCurrentResult(grid);
                sendPacketToClient(new CraftConfirmPlanPacket(this.plan));
            } catch (Throwable e) {
                ITextComponent error = getCraftingErrorText(e);
                this.getPlayerInventory().player.sendMessage(PlayerMessages.CraftingJobError.text(error));
                AELog.warn("Failed to start crafting job.", e);
                this.setValidContainer(false);
                this.result = null;
                invalidateMergeCache();
            }

            this.job = null;
        }
        ensureMergeableCpuCache(grid);
        this.cpuCycler.detectAndSendChanges(grid);
        this.mergeAvailable = canMergeCurrentResult(grid);
        this.canSubscribe = hasUsableWirelessTerminal(this.getPlayer());
        syncCpuList(grid);
        super.broadcastChanges();
    }

    private static boolean allBatchJobsDone(List<Future<ICraftingPlan>> jobs) {
        for (int i = 0, size = jobs.size(); i < size; i++) {
            if (!jobs.get(i).isDone()) {
                return false;
            }
        }
        return true;
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
        clearError();

        if (isClientSide()) {
            sendClientAction(ACTION_START_JOB, new StartJobRequest(forceStart, skipMerge, this.taskPriority, subscribed));
            return;
        }

        if (this.result != null && !this.result.simulation()) {
            if (!forceStart && !this.result.missingItems().isEmpty()) {
                return;
            }
            IGrid grid = getGrid();
            if (grid == null) {
                this.setValidContainer(false);
                return;
            }
            ICraftingService craftingService = grid.getCraftingService();
            ICraftingSubmitResult submitResult = craftingService.submitJob(this.result, null, this.selectedCpu, true,
                this.getActionSrc(), forceStart, skipMerge, new CraftingJobOptions(this.taskPriority, subscribed));
            this.setAutoStart(false);
            if (submitResult.successful()) {
                this.jobStarted = true;
                boolean hasQueuedJobs = this.autoCraftingQueue != null && !this.autoCraftingQueue.isEmpty();
                EntityPlayer player = getPlayer();
                if (hasQueuedJobs) {
                    if (player instanceof EntityPlayerMP serverPlayer) {
                        ContainerCraftConfirm.openWithCraftingList(getActionHost(), serverPlayer, getLocator(),
                            this.autoCraftingQueue, getReturnToContainerOverride());
                    }
                } else if (!(player instanceof EntityPlayerMP serverPlayer)
                    || !SwitchGuisPacket.restoreExternalGui(serverPlayer)) {
                    this.host.returnToMainContainer(player, this);
                }
            } else {
                AELog.info("Couldn't submit crafting job for %dx%s: %s [Detail: %s]",
                    this.result.finalOutput().amount(),
                    this.result.finalOutput().what(),
                    submitResult.errorCode(),
                    submitResult.errorDetail());
                this.submitError = new SyncableSubmitResult(submitResult);
            }
        }
    }

    private void startJob(StartJobRequest request) {
        this.taskPriority = request.priority;
        startJob(request.forceStart, request.skipMerge, request.subscribed && hasUsableWirelessTerminal(this.getPlayer()));
    }

    private IActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInventory().player, getActionHost());
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        this.cancelPendingCalculations();
        if (!this.jobStarted) {
            // The order was abandoned, so the crafting grid slots it wanted to fill are dropped as well.
            PendingGridFills.scheduleAbandonCheck(player);
        }
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
        this.job = job;
    }

    @Nullable
    public CraftingPlanSummary getPlan() {
        return this.plan;
    }

    public void setPlan(@Nullable CraftingPlanSummary plan) {
        this.plan = plan;
    }

    public void goBack() {
        clearError();

        EntityPlayer player = getPlayerInventory().player;
        if (player instanceof EntityPlayerMP serverPlayer) {
            if (this.autoCraftingQueue != null && !this.autoCraftingQueue.isEmpty()) {
                ContainerCraftConfirm.openWithCraftingList(getActionHost(), serverPlayer, getLocator(),
                    this.autoCraftingQueue, getReturnToContainerOverride());
            } else if (this.whatToCraft != null) {
                ContainerCraftAmount.open(serverPlayer, getLocator(), this.whatToCraft, Ints.saturatedCast(this.amount),
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

    public void replan() {
        clearError();

        if (isClientSide()) {
            sendClientAction(ACTION_REPLAN);
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

    public void clearError() {
        this.submitError = NO_ERROR;
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
