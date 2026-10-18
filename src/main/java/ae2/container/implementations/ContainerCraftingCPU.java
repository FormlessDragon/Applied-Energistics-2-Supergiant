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

import ae2.api.config.CpuSelectionMode;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.networking.security.IActionHost;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import ae2.container.me.common.IncrementalUpdateHelper;
import ae2.container.me.crafting.CraftingStatus;
import ae2.container.me.crafting.CraftingStatusEntry;
import ae2.core.AELog;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.clientbound.CraftingStatusPacket;
import ae2.core.network.clientbound.CraftingSupplierLocationsPacket;
import ae2.crafting.execution.CraftingCpuLogic;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.hooks.ticking.TickHandler;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.tile.crafting.ICraftingCPUTileEntity;
import ae2.util.NullConfigManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

public class ContainerCraftingCPU extends AEBaseContainer implements IConfigurableObject {

    private static final String ACTION_CANCEL_CRAFTING = "cancelCrafting";
    private static final String ACTION_TOGGLE_SCHEDULING = "toggleScheduling";
    private static final String ACTION_SET_TASK_PRIORITY = "setTaskPriority";
    private static final String ACTION_REQUEST_STATUS = "requestStatus";
    private static final int MAX_PENDING_STATUS_ENTRIES = 4096;

    private final IncrementalUpdateHelper incrementalUpdateHelper = new IncrementalUpdateHelper();
    @Nullable
    private final IConfigurableObject configHost;
    @Nullable
    private final IGrid grid;
    private final Consumer<AEKey> cpuChangeListener = incrementalUpdateHelper::addChange;
    @GuiSync(0)
    public CpuSelectionMode schedulingMode = CpuSelectionMode.ANY;
    @GuiSync(1)
    public boolean cantStoreItems = false;
    @Nullable
    private CraftingCPUCluster cpu;
    private boolean cachedSuspend;
    private int cachedPriority;
    private long nextSupplierTraceTick;
    private long cpuEpoch;
    private long statusRevision;
    private long clientCpuEpoch = Long.MIN_VALUE;
    private long clientStatusRevision = -1;
    private boolean clientHasStatusBase;
    private boolean clientStatusRecoveryRequested;
    @Nullable
    private Long2ObjectMap<CraftingStatusEntry> clientStatusEntries;
    private boolean clientStatusModelInitialized;
    private boolean clientStatusDirty;
    private long clientElapsedTime;
    private long clientRemainingItemCount;
    private long clientStartItemCount;
    private boolean clientSuspended;
    private int clientPriority;
    @Nullable
    private GenericStack clientFinalOutput;

    public ContainerCraftingCPU(InventoryPlayer ip, ICraftingCPUTileEntity host) {
        this(ip, (Object) host);
    }

    protected ContainerCraftingCPU(InventoryPlayer ip, Object host) {
        super(ip, host);

        this.configHost = host instanceof IConfigurableObject ? (IConfigurableObject) host : null;
        this.grid = extractGrid(host);

        if (host instanceof ICraftingCPUTileEntity craftingUnit) {
            this.setCPU(craftingUnit.getCluster());
        }

        if (this.grid == null && isServerSide()) {
            this.setValidContainer(false);
        }

        registerClientAction(ACTION_CANCEL_CRAFTING, this::cancelCrafting);
        registerClientAction(ACTION_TOGGLE_SCHEDULING, this::toggleScheduling);
        registerClientAction(ACTION_SET_TASK_PRIORITY, Integer.class, this::setTaskPriority);
        registerClientAction(ACTION_REQUEST_STATUS, this::requestStatus);
    }

    @Nullable
    private static IGrid extractGrid(Object host) {
        if (host instanceof ICraftingCPUTileEntity craftingUnit) {
            IManagedGridNode node = craftingUnit.getMainNode();
            return node != null ? node.getGrid() : null;
        }

        if (host instanceof IActionHost actionHost) {
            IGridNode node = actionHost.getActionableNode();
            return node != null ? node.grid() : null;
        }

        return null;
    }

    protected void setCPU(@Nullable ICraftingCPU cpu) {
        if (cpu == this.cpu) {
            return;
        }

        this.cpuEpoch++;
        this.statusRevision = 0;

        if (this.cpu != null) {
            this.cpu.craftingLogic.removeListener(cpuChangeListener);
        }

        this.incrementalUpdateHelper.reset();
        this.cachedSuspend = false;
        this.cachedPriority = 0;

        if (cpu instanceof CraftingCPUCluster cluster) {
            this.cpu = cluster;

            KeyCounter allItems = new KeyCounter();
            cluster.craftingLogic.getAllItems(allItems);
            for (Object2LongMap.Entry<AEKey> entry : allItems) {
                incrementalUpdateHelper.addChange(entry.getKey());
            }

            cluster.craftingLogic.addListener(cpuChangeListener);
        } else {
            this.cpu = null;
            sendStatus(CraftingStatus.EMPTY);
        }
    }

    private void requestStatus() {
        if (isClientSide()) {
            if (!this.clientStatusRecoveryRequested) {
                this.clientStatusRecoveryRequested = true;
                sendClientAction(ACTION_REQUEST_STATUS);
            }
            return;
        }

        if (this.cpu == null) {
            sendStatus(CraftingStatus.EMPTY);
            return;
        }

        prepareFullStatus();
        this.cachedSuspend = false;
        this.cachedPriority = 0;
    }

    private void prepareFullStatus() {
        this.incrementalUpdateHelper.reset();
        if (this.cpu == null) {
            return;
        }

        KeyCounter allItems = new KeyCounter();
        this.cpu.craftingLogic.getAllItems(allItems);
        for (Object2LongMap.Entry<AEKey> entry : allItems) {
            this.incrementalUpdateHelper.addChange(entry.getKey());
        }
    }

    private boolean sendStatus(CraftingStatus status) {
        this.statusRevision++;
        try {
            sendPacketToClient(new CraftingStatusPacket(windowId, this.cpuEpoch, this.statusRevision, status));
            return true;
        } catch (RuntimeException e) {
            AELog.warn("Failed to send crafting CPU status; scheduling a full status retry", e);
            return false;
        }
    }

    public void acceptClientStatus(long epoch, long revision, CraftingStatus status) {
        if (revision <= 0 || epoch <= 0) {
            return;
        }
        if (epoch < this.clientCpuEpoch || epoch == this.clientCpuEpoch && revision <= this.clientStatusRevision) {
            return;
        }
        if (epoch > this.clientCpuEpoch) {
            if (!status.fullStatus()) {
                requestStatus();
                return;
            }
            this.clientCpuEpoch = epoch;
            this.clientStatusRevision = 0;
            this.clientHasStatusBase = false;
            resetClientStatusModel();
            this.clientStatusRecoveryRequested = false;
        }
        if (!status.fullStatus() && !this.clientHasStatusBase) {
            requestStatus();
            return;
        }

        this.clientStatusRevision = revision;
        if (!applyClientStatus(status)) {
            resetClientStatusModel();
            this.clientHasStatusBase = false;
            requestStatus();
            return;
        }
        this.clientHasStatusBase = true;
        if (status.fullStatus()) {
            this.clientStatusRecoveryRequested = false;
        }
    }

    private boolean applyClientStatus(CraftingStatus status) {
        Long2ObjectMap<CraftingStatusEntry> entries = getClientStatusEntries();
        if (status.fullStatus()) {
            entries.clear();
            this.clientStatusModelInitialized = true;
        } else if (!this.clientStatusModelInitialized) {
            return false;
        }

        for (CraftingStatusEntry entry : status.entries()) {
            if (entry.isDeleted()) {
                entries.remove(entry.serial());
                continue;
            }

            CraftingStatusEntry existingEntry = entries.get(entry.serial());
            if (existingEntry != null) {
                entries.put(entry.serial(), new CraftingStatusEntry(
                    existingEntry.serial(),
                    existingEntry.what(),
                    entry.storedAmount(),
                    entry.activeAmount(),
                    entry.pendingAmount()));
            } else if (entry.what() != null) {
                entries.put(entry.serial(), entry);
            }

            if (entries.size() > MAX_PENDING_STATUS_ENTRIES) {
                return false;
            }
        }

        this.clientElapsedTime = status.elapsedTime();
        this.clientRemainingItemCount = status.remainingItemCount();
        this.clientStartItemCount = status.startItemCount();
        this.clientSuspended = status.suspended();
        this.clientPriority = status.priority();
        this.clientFinalOutput = status.finalOutput();
        this.clientStatusDirty = true;
        return true;
    }

    private void resetClientStatusModel() {
        if (this.clientStatusEntries != null) {
            this.clientStatusEntries.clear();
        }
        this.clientStatusModelInitialized = false;
        this.clientStatusDirty = false;
    }

    private Long2ObjectMap<CraftingStatusEntry> getClientStatusEntries() {
        if (this.clientStatusEntries == null) {
            this.clientStatusEntries = new Long2ObjectLinkedOpenHashMap<>();
        }
        return this.clientStatusEntries;
    }

    @Nullable
    public CraftingStatus consumeClientStatus() {
        if (!this.clientStatusDirty || !this.clientStatusModelInitialized) {
            return null;
        }
        this.clientStatusDirty = false;
        return new CraftingStatus(
            true,
            this.clientElapsedTime,
            this.clientRemainingItemCount,
            this.clientStartItemCount,
            List.copyOf(getClientStatusEntries().values()),
            this.clientSuspended,
            this.clientPriority,
            this.clientFinalOutput);
    }

    public void cancelCrafting() {
        if (isClientSide()) {
            sendClientAction(ACTION_CANCEL_CRAFTING);
        } else if (this.cpu != null) {
            this.cpu.cancelJob();
        }
    }

    public void toggleScheduling() {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_SCHEDULING);
        } else if (this.cpu != null) {
            CraftingCpuLogic logic = this.cpu.craftingLogic;
            logic.setJobSuspended(!logic.isJobSuspended());
        }
    }

    public int getTaskPriority() {
        return this.cpu != null ? this.cpu.craftingLogic.getJobPriority() : 0;
    }

    public void setTaskPriority(int priority) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_TASK_PRIORITY, priority);
        } else if (this.cpu != null && this.cpu.craftingLogic.hasJob()) {
            this.cpu.craftingLogic.setJobPriority(priority);
        }
    }

    @Nullable
    public GenericStack getFinalJobOutput() {
        return this.cpu != null ? this.cpu.craftingLogic.getFinalJobOutput() : null;
    }

    @Override
    public void onContainerClosed(@NotNull EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.cpu != null) {
            this.cpu.craftingLogic.removeListener(cpuChangeListener);
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (isServerSide() && this.cpu != null) {
            this.schedulingMode = this.cpu.getSelectionMode();
            this.cantStoreItems = this.cpu.craftingLogic.isCantStoreItems();

            if (this.incrementalUpdateHelper.hasChanges()
                || this.cachedSuspend != this.cpu.craftingLogic.isJobSuspended()
                || this.cachedPriority != this.cpu.craftingLogic.getJobPriority()) {
                CraftingStatus status = CraftingStatus.create(this.incrementalUpdateHelper, this.cpu.craftingLogic);
                if (sendStatus(status)) {
                    this.incrementalUpdateHelper.commitChanges();
                    this.cachedSuspend = status.suspended();
                    this.cachedPriority = status.priority();
                } else {
                    prepareFullStatus();
                }
            }
        }

        super.detectAndSendChanges();
    }

    public CpuSelectionMode getSchedulingMode() {
        return schedulingMode;
    }

    public boolean isCantStoreItems() {
        return cantStoreItems;
    }

    public boolean allowConfiguration() {
        return true;
    }

    @Nullable
    IGrid getGrid() {
        return this.grid;
    }

    public void traceSupplierForSerial(long serial) {
        if (!(this.getPlayer() instanceof EntityPlayerMP player)) {
            return;
        }
        if (this.cpu == null || this.grid == null) {
            return;
        }

        var key = this.incrementalUpdateHelper.getBySerial(serial);
        if (key == null) {
            return;
        }

        var logic = this.cpu.craftingLogic;
        long activeAmount = logic.getWaitingFor(key);
        long pendingAmount = logic.getPendingOutputs(key);
        if (activeAmount <= 0 && pendingAmount <= 0) {
            return;
        }

        long currentTick = TickHandler.instance().getCurrentTick();
        if (currentTick < this.nextSupplierTraceTick) {
            return;
        }
        this.nextSupplierTraceTick = currentTick + 10;

        List<CraftingSupplierLocation> locations = logic.findSupplierLocations(this.grid, key);
        if (locations.isEmpty()) {
            player.sendStatusMessage(PlayerMessages.CraftingSupplierNotFound.text(), true);
            return;
        }

        sendPacketToClient(new CraftingSupplierLocationsPacket(locations));
    }

    @Override
    public IConfigManager getConfigManager() {
        if (this.cpu != null) {
            return this.cpu.getConfigManager();
        }
        if (this.configHost != null) {
            return this.configHost.getConfigManager();
        }
        return NullConfigManager.INSTANCE;
    }
}
