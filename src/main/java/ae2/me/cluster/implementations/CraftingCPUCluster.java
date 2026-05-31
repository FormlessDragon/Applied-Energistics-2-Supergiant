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

package ae2.me.cluster.implementations;

import ae2.api.config.Actionable;
import ae2.api.config.CpuSelectionMode;
import ae2.api.config.Settings;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.CraftingJobStatus;
import ae2.api.networking.crafting.ICraftingCPU;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.events.GridCraftingCpuChange;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.util.IConfigManager;
import ae2.crafting.execution.CraftingCpuLogic;
import ae2.me.cluster.IAECluster;
import ae2.me.cluster.MBCalculator;
import ae2.me.helpers.MachineSource;
import ae2.tile.crafting.TileCraftingMonitor;
import ae2.tile.crafting.TileCraftingUnit;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

public final class CraftingCPUCluster implements IAECluster, ICraftingCPU {

    public final CraftingCpuLogic craftingLogic = new CraftingCpuLogic(this);
    private final BlockPos boundsMin;
    private final BlockPos boundsMax;
    private final ObjectList<TileCraftingUnit> blockEntities = new ObjectArrayList<>();
    private final ObjectList<TileCraftingMonitor> status = new ObjectArrayList<>();
    private final IConfigManager configManager;
    private ITextComponent myName;
    private boolean destroyed;
    private long storage;
    private MachineSource machineSrc;
    private int accelerator;

    public CraftingCPUCluster(BlockPos boundsMin, BlockPos boundsMax) {
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
        this.configManager = IConfigManager.builder(this::markDirty)
                                           .registerSetting(Settings.CPU_SELECTION_MODE, CpuSelectionMode.ANY)
                                           .build();
    }

    @Override
    public boolean isDestroyed() {
        return this.destroyed;
    }

    @Override
    public BlockPos getBoundsMin() {
        return this.boundsMin;
    }

    @Override
    public BlockPos getBoundsMax() {
        return this.boundsMax;
    }

    @Override
    public void updateStatus(boolean updateGrid) {
        for (TileCraftingUnit tile : this.blockEntities) {
            tile.updateSubType(true);
        }
    }

    @Override
    public void destroy() {
        if (this.destroyed) {
            return;
        }
        this.destroyed = true;

        boolean ownsModification = !MBCalculator.isModificationInProgress();
        if (ownsModification) {
            MBCalculator.setModificationInProgress(this);
        }

        try {
            boolean posted = false;

            for (TileCraftingUnit tile : this.blockEntities) {
                final IGridNode node = tile.getActionableNode();
                if (node != null && !posted) {
                    node.grid().postEvent(new GridCraftingCpuChange(node));
                    posted = true;
                }

                tile.updateStatus(null);
            }
        } finally {
            if (ownsModification) {
                MBCalculator.setModificationInProgress(null);
            }
        }
    }

    @Override
    public Iterator<TileCraftingUnit> getBlockEntities() {
        return this.blockEntities.iterator();
    }

    public void addTileEntity(TileCraftingUnit tile) {
        if (this.machineSrc == null || tile.isCoreBlock()) {
            this.machineSrc = new MachineSource(tile);
        }

        tile.setCoreBlock(false);
        tile.saveChanges();
        this.blockEntities.addFirst(tile);

        if (tile instanceof TileCraftingMonitor monitor) {
            this.status.add(monitor);
        }

        if (tile.getStorageBytes() > 0) {
            this.storage += tile.getStorageBytes();
        }

        if (tile.getAcceleratorThreads() > 0) {
            if (tile.getAcceleratorThreads() <= 16) {
                this.accelerator += tile.getAcceleratorThreads();
            } else {
                throw new IllegalArgumentException("Co-processor threads may not exceed 16 per single unit block.");
            }
        }
    }

    @SuppressWarnings("unused")
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return craftingLogic.insert(what, amount, mode);
    }

    public void markDirty() {
        TileCraftingUnit core = this.getCore();
        if (core != null) {
            core.saveChanges();
        }
    }

    public void updateOutput(@Nullable GenericStack finalOutput) {
        GenericStack send = finalOutput;

        if (finalOutput != null && finalOutput.amount() <= 0) {
            send = null;
        }

        for (TileCraftingMonitor tile : this.status) {
            tile.setJob(send);
        }
    }

    public IActionSource getSrc() {
        return Objects.requireNonNull(this.machineSrc);
    }

    @Nullable
    private TileCraftingUnit getCore() {
        if (this.machineSrc == null) {
            return null;
        }

        return this.machineSrc.machine()
                              .filter(TileCraftingUnit.class::isInstance)
                              .map(TileCraftingUnit.class::cast)
                              .orElse(null);
    }

    @Nullable
    public IGrid getGrid() {
        IGridNode node = getNode();
        return node != null ? node.grid() : null;
    }

    @Override
    public void cancelJob() {
        craftingLogic.cancel();
    }

    public ICraftingSubmitResult submitJob(IGrid grid, ICraftingPlan plan, IActionSource src,
                                           @Nullable ICraftingRequester requestingMachine) {
        return craftingLogic.trySubmitJob(grid, plan, src, requestingMachine);
    }

    @Override
    public boolean isBusy() {
        return craftingLogic.hasJob();
    }

    @Nullable
    @Override
    public CraftingJobStatus getJobStatus() {
        var finalOutput = craftingLogic.getFinalJobOutput();
        if (finalOutput == null) {
            return null;
        }

        var elapsedTimeTracker = craftingLogic.getElapsedTimeTracker();
        long totalItems = elapsedTimeTracker.getStartedWorkUnits();
        long progress = Math.max(0, totalItems - elapsedTimeTracker.getRemainingWorkUnits());
        return new CraftingJobStatus(
            finalOutput,
            totalItems,
            progress,
            elapsedTimeTracker.getElapsedTime());
    }

    @Override
    public long getAvailableStorage() {
        return this.storage;
    }

    @Override
    public int getCoProcessors() {
        return this.accelerator;
    }

    @Nullable
    @Override
    public ITextComponent getName() {
        return this.myName;
    }

    @Nullable
    public IGridNode getNode() {
        TileCraftingUnit core = getCore();
        return core != null ? core.getActionableNode() : null;
    }

    public boolean isActive() {
        IGridNode node = getNode();
        return node != null && node.isActive();
    }

    public void writeToNBT(NBTTagCompound data) {
        this.craftingLogic.writeToNBT(data);
        this.configManager.writeToNBT(data);
    }

    public void done() {
        final TileCraftingUnit core = this.getCore();
        if (core == null) {
            return;
        }

        core.setCoreBlock(true);

        if (core.getPreviousState() != null) {
            this.readFromNBT(core.getPreviousState());
            core.setPreviousState(null);
        }

        this.updateName();
    }

    public void readFromNBT(NBTTagCompound data) {
        this.craftingLogic.readFromNBT(data);
        this.configManager.readFromNBT(data);
    }

    public void updateName() {
        this.myName = null;
        for (TileCraftingUnit tile : this.blockEntities) {
            if (tile.hasCustomName()) {
                var customName = tile.getCustomName();
                if (this.myName == null) {
                    if (customName != null) {
                        this.myName = customName.createCopy();
                    }
                } else if (customName != null) {
                    this.myName.appendText(" ").appendSibling(customName.createCopy());
                }
            }
        }
    }

    @Nullable
    public World getLevel() {
        TileCraftingUnit core = this.getCore();
        return core == null ? null : core.getWorld();
    }

    public void breakCluster() {
        final TileCraftingUnit tile = this.getCore();
        if (tile != null) {
            tile.breakCluster();
        }
    }

    @Override
    public CpuSelectionMode getSelectionMode() {
        return this.configManager.getSetting(Settings.CPU_SELECTION_MODE);
    }

    public IConfigManager getConfigManager() {
        return this.configManager;
    }

    public boolean canBeAutoSelectedFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public boolean isPreferredFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> false;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }
}
