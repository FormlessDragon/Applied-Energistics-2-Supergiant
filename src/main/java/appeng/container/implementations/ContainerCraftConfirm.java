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

package appeng.container.implementations;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ISubGuiHost;
import appeng.container.AEBaseContainer;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.container.guisync.GuiSync;
import appeng.container.guisync.PacketWritable;
import appeng.container.interfaces.ICraftingGridContainer;
import appeng.container.me.crafting.CraftingCPUCycler;
import appeng.container.me.crafting.CraftingCPURecord;
import appeng.container.me.crafting.CraftingPlanSummary;
import appeng.core.AELog;
import appeng.core.gui.locator.GuiHostLocator;
import appeng.core.localization.PlayerMessages;
import appeng.core.network.clientbound.CraftConfirmPlanPacket;
import appeng.core.network.serverbound.SwitchGuisPacket;
import appeng.crafting.TemporaryPseudoCraftingProvider;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.helpers.PlayerSource;
import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

public class ContainerCraftConfirm extends AEBaseContainer implements ISubGui {
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CYCLE_CPU = "cycleCpu";
    private static final String ACTION_START_JOB = "startJob";
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
    @Nullable
    private CraftingPlanSummary plan;
    @Nullable
    private List<ICraftingGridContainer.AutoCraftEntry> autoCraftingQueue;
    @Nullable
    private TemporaryPseudoCraftingProvider temporaryPseudoProvider;

    public ContainerCraftConfirm(InventoryPlayer ip, ISubGuiHost host) {
        super(ip, host);
        this.host = host;
        this.cpuCycler = new CraftingCPUCycler(this::cpuMatches, this::onCPUSelectionChanged);
        this.cpuCycler.setAllowNoSelection(true);

        registerClientAction(ACTION_BACK, this::goBack);
        registerClientAction(ACTION_CYCLE_CPU, Boolean.class, this::cycleSelectedCPU);
        registerClientAction(ACTION_START_JOB, Boolean.class, this::startJob);
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

    public static void openWithTemporaryPseudoPattern(@Nullable IActionHost terminal, EntityPlayerMP player,
                                                      @Nullable GuiHostLocator locator,
                                                      TemporaryPseudoCraftingProvider provider) {
        if (terminal == null || locator == null) {
            return;
        }

        try {
            SwitchGuisPacket.openSubGui(player, locator, GuiIds.GuiKey.CRAFT_CONFIRM, null);

            if (player.openContainer instanceof ContainerCraftConfirm container) {
                var primaryOutput = provider.pattern().getPrimaryOutput();
                container.temporaryPseudoProvider = provider;
                if (!container.planTemporaryPseudoJob(primaryOutput.what(), primaryOutput.amount(),
                    CalculationStrategy.REPORT_MISSING_ITEMS)) {
                    container.temporaryPseudoProvider = null;
                    container.setValidContainer(false);
                    return;
                }

                container.detectAndSendChanges();
            }
        } catch (Throwable e) {
            AELog.info(e);
        }
    }

    static boolean canUseCpuForRequest(ICraftingCPU cpu, @Nullable CraftingPlanSummary plan, boolean playerRequest) {
        return switch (cpu.getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> playerRequest;
            case MACHINE_ONLY -> !playerRequest;
        };
    }

    static boolean shouldAutoStart(ICraftingPlan result, boolean autoStart) {
        return autoStart && !result.simulation() && result.missingItems().isEmpty();
    }

    public boolean planJob(AEKey what, long amount, CalculationStrategy strategy) {
        if (this.job != null) {
            this.job.cancel(true);
        }
        this.result = null;
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

    private boolean planTemporaryPseudoJob(AEKey what, long amount, CalculationStrategy strategy) {
        if (this.job != null) {
            this.job.cancel(true);
        }
        this.result = null;
        this.clearError();
        this.whatToCraft = what;
        this.amount = amount;
        this.strategy = strategy;

        IGrid grid = getGrid();
        TemporaryPseudoCraftingProvider provider = this.temporaryPseudoProvider;
        if (grid == null || provider == null) {
            return false;
        }

        this.job = grid.getCraftingService().beginCraftingCalculation(getPlayer().world,
            new TemporaryPseudoSimulationRequester(provider), what, amount, strategy);
        return true;
    }

    public void cycleSelectedCPU(boolean next) {
        if (isClientSide()) {
            sendClientAction(ACTION_CYCLE_CPU, next);
        } else {
            this.cpuCycler.cycleCpu(next);
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

        this.cpuCycler.detectAndSendChanges(grid);
        super.broadcastChanges();

        if (this.job != null && this.job.isDone()) {
            try {
                this.result = this.job.get();
                if (this.result == null) {
                    this.getPlayerInventory().player.sendMessage(PlayerMessages.CraftingJobError.text(
                        "Crafting calculation returned no plan."));
                    AELog.warn("Crafting calculation returned no plan.");
                    this.setValidContainer(false);
                    this.job = null;
                    return;
                }

                if (shouldAutoStart(this.result, this.isAutoStart())) {
                    this.startJob(false);
                    return;
                }

                if (!this.result.missingItems().isEmpty()) {
                    this.setAutoStart(false);
                }

                this.plan = CraftingPlanSummary.fromJob(this.getGrid(), this.getActionSrc(), this.result);
                sendPacketToClient(new CraftConfirmPlanPacket(this.plan));
            } catch (Throwable e) {
                this.getPlayerInventory().player.sendMessage(PlayerMessages.CraftingJobError.text(e.toString()));
                AELog.warn("Failed to start crafting job.", e);
                this.setValidContainer(false);
                this.result = null;
            }

            this.job = null;
        }
    }

    @Nullable
    private IGrid getGrid() {
        IActionHost actionHost = (IActionHost) this.getTarget();
        IGridNode node = actionHost.getActionableNode();
        return node != null ? node.grid() : null;
    }

    private boolean cpuMatches(ICraftingCPU cpu) {
        if (!canUseCpuForRequest(cpu, this.plan, getPlayer() != null)) {
            return false;
        }
        if (this.plan == null) {
            return true;
        }
        return cpu.getAvailableStorage() >= this.plan.usedBytes() && !cpu.isBusy();
    }

    public void startJob() {
        startJob(false);
    }

    public void startJob(boolean forceStart) {
        clearError();

        if (isClientSide()) {
            sendClientAction(ACTION_START_JOB, forceStart);
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
                this.getActionSrc(), forceStart);
            this.setAutoStart(false);
            if (submitResult.successful()) {
                if (this.temporaryPseudoProvider != null) {
                    this.temporaryPseudoProvider = null;
                }
                if (this.autoCraftingQueue != null && !this.autoCraftingQueue.isEmpty()) {
                    EntityPlayer player = getPlayer();
                    if (player instanceof EntityPlayerMP serverPlayer) {
                        ContainerCraftConfirm.openWithCraftingList(getActionHost(), serverPlayer, getLocator(),
                            this.autoCraftingQueue, getReturnToContainerOverride());
                    }
                } else {
                    EntityPlayer player = getPlayer();
                    if (!(player instanceof EntityPlayerMP serverPlayer)
                        || !SwitchGuisPacket.restoreExternalGui(serverPlayer)) {
                        this.host.returnToMainContainer(player, this);
                    }
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

    private IActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInventory().player, (IActionHost) this.getTarget());
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.job != null) {
            this.job.cancel(true);
            this.job = null;
        }
        this.temporaryPseudoProvider = null;
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

        if (this.whatToCraft != null) {
            boolean planned = this.temporaryPseudoProvider != null
                ? planTemporaryPseudoJob(this.whatToCraft, this.amount, this.strategy)
                : planJob(this.whatToCraft, this.amount, this.strategy);
            if (!planned) {
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

            CraftingSubmitErrorCode errorCode = buffer.readEnumValue(CraftingSubmitErrorCode.class);
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

    private final class TemporaryPseudoSimulationRequester implements ICraftingSimulationRequester {
        private final TemporaryPseudoCraftingProvider provider;

        private TemporaryPseudoSimulationRequester(TemporaryPseudoCraftingProvider provider) {
            this.provider = provider;
        }

        @Override
        public IActionSource getActionSource() {
            return ContainerCraftConfirm.this.getActionSrc();
        }

        @Override
        @Nullable
        public IGridNode getGridNode() {
            IActionHost actionHost = (IActionHost) ContainerCraftConfirm.this.getTarget();
            return actionHost.getActionableNode();
        }

        @Override
        public List<appeng.api.crafting.IPatternDetails> getAdditionalPatterns() {
            return List.of(this.provider.pattern());
        }

        @Override
        public List<ICraftingProvider> getAdditionalProviders() {
            return List.of(this.provider);
        }
    }
}
