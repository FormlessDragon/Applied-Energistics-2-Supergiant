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

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.me.common.IncrementalUpdateHelper;
import appeng.container.me.crafting.CraftingStatus;
import appeng.core.network.clientbound.CraftingStatusPacket;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.tile.crafting.TileCraftingUnit;
import appeng.util.NullConfigManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.jetbrains.annotations.Nullable;

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
    @Nullable
    private ITextComponent initialTitle;
    private boolean cachedSuspend;

    public ContainerCraftingCPU( InventoryPlayer ip, TileCraftingUnit host) {
        this(ip, (Object) host);
    }

    public ContainerCraftingCPU( InventoryPlayer ip, TileCraftingUnit host,
                                @Nullable ITextComponent initialTitle) {
        this(ip, host);
        setInitialTitle(initialTitle);
    }

    protected ContainerCraftingCPU( InventoryPlayer ip, Object host) {
        super(ip, host);

        this.configHost = host instanceof IConfigurableObject ? (IConfigurableObject) host : null;
        this.grid = extractGrid(host);

        if (host instanceof TileCraftingUnit craftingUnit) {
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
        if (host instanceof TileCraftingUnit craftingUnit) {
            appeng.api.networking.IManagedGridNode node = craftingUnit.getMainNode();
            return node != null ? node.getGrid() : null;
        }

        if (host instanceof IActionHost actionHost) {
            IGridNode node = actionHost.getActionableNode();
            return node != null ? node.getGrid() : null;
        }

        return null;
    }

    @Nullable
    public ITextComponent getInitialTitle() {
        return this.initialTitle;
    }

    public void setInitialTitle(@Nullable ITextComponent title) {
        if (isClientSide()) {
            this.initialTitle = title != null ? title : new TextComponentString("");
        }
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
            for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : allItems) {
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
            appeng.crafting.execution.CraftingCpuLogic logic = this.cpu.craftingLogic;
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
