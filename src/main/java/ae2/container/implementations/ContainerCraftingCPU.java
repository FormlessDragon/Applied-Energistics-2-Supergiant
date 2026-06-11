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
import ae2.api.stacks.KeyCounter;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.container.AEBaseContainer;
import ae2.container.guisync.GuiSync;
import ae2.container.me.common.IncrementalUpdateHelper;
import ae2.container.me.crafting.CraftingStatus;
import ae2.core.localization.PlayerMessages;
import ae2.core.network.clientbound.CraftingStatusPacket;
import ae2.core.network.clientbound.CraftingSupplierLocationsPacket;
import ae2.crafting.execution.CraftingCpuLogic;
import ae2.crafting.execution.CraftingSupplierLocation;
import ae2.hooks.ticking.TickHandler;
import ae2.me.cluster.implementations.CraftingCPUCluster;
import ae2.tile.crafting.ICraftingCPUTileEntity;
import ae2.util.NullConfigManager;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class ContainerCraftingCPU extends AEBaseContainer implements IConfigurableObject {

    private static final String ACTION_CANCEL_CRAFTING = "cancelCrafting";
    private static final String ACTION_TOGGLE_SCHEDULING = "toggleScheduling";

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
    private long nextSupplierTraceTick;

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

        if (this.cpu != null) {
            this.cpu.craftingLogic.removeListener(cpuChangeListener);
        }

        this.incrementalUpdateHelper.reset();
        this.cachedSuspend = false;

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
            sendPacketToClient(new CraftingStatusPacket(windowId, CraftingStatus.EMPTY));
        }
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

    @Override
    public void onContainerClosed(EntityPlayer player) {
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
                || this.cachedSuspend != this.cpu.craftingLogic.isJobSuspended()) {
                CraftingStatus status = CraftingStatus.create(this.incrementalUpdateHelper, this.cpu.craftingLogic);
                this.incrementalUpdateHelper.commitChanges();
                this.cachedSuspend = status.suspended();

                sendPacketToClient(new CraftingStatusPacket(windowId, status));
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
