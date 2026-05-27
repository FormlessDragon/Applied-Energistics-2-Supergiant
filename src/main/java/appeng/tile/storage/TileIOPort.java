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
package appeng.tile.storage;

import appeng.api.config.Actionable;
import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.settings.TickRates;
import appeng.me.helpers.MachineSource;
import appeng.tile.grid.AENetworkedInvTile;
import appeng.util.ConfigManager;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.CombinedInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.AEItemFilters;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

public class TileIOPort extends AENetworkedInvTile implements IUpgradeableObject, IConfigurableObject, IGridTickable {
    private static final int NUMBER_OF_CELL_SLOTS = 6;
    private static final int NUMBER_OF_UPGRADE_SLOTS = 3;
    private final AppEngInternalInventory inputCells = new AppEngInternalInventory(this, NUMBER_OF_CELL_SLOTS);
    private final ConfigManager manager = new ConfigManager(this::onStateChanged);
    private final AppEngInternalInventory outputCells = new AppEngInternalInventory(this, NUMBER_OF_CELL_SLOTS);
    private final InternalInventory combinedInventory = new CombinedInternalInventory(this.inputCells, this.outputCells);
    private final InternalInventory inputCellsExt = new FilteredInternalInventory(this.inputCells,
        AEItemFilters.INSERT_ONLY);
    private final InternalInventory outputCellsExt = new FilteredInternalInventory(this.outputCells,
        AEItemFilters.EXTRACT_ONLY);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AEBlocks.IO_PORT.item(),
        NUMBER_OF_UPGRADE_SLOTS, this::onStateChanged);
    private final MachineSource mySrc = new MachineSource(this);

    private YesNo lastRedstoneState = YesNo.UNDECIDED;
    private boolean active;

    public TileIOPort() {
        this.getMainNode()
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .addService(IGridTickable.class, this);
        this.manager.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.manager.registerSetting(Settings.FULLNESS_MODE, FullnessMode.EMPTY);
        this.manager.registerSetting(Settings.OPERATION_MODE, OperationMode.EMPTY);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.inputCells.writeToNBT(data, "inputCells");
        this.outputCells.writeToNBT(data, "outputCells");
        this.manager.writeToNBT(data);
        this.upgrades.writeToNBT(data, "upgrades");
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.inputCells.readFromNBT(data, "inputCells");
        this.outputCells.readFromNBT(data, "outputCells");
        this.manager.readFromNBT(data);
        this.upgrades.readFromNBT(data, "upgrades");
        this.lastRedstoneState = YesNo.UNDECIDED;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.isActive());
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean nextActive = data.readBoolean();
        changed = changed || nextActive != this.active;
        this.active = nextActive;
        return changed;
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.IO_PORT.stack();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return super.getCableConnectionType(dir);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.markForUpdate();
        }
    }

    public void updateRedstoneState() {
        if (this.world == null) {
            return;
        }

        YesNo currentState = this.world.isBlockPowered(this.pos) ? YesNo.YES : YesNo.NO;
        if (this.lastRedstoneState == YesNo.UNDECIDED) {
            this.lastRedstoneState = currentState;
            this.updateTask();
            return;
        }

        if (this.lastRedstoneState != currentState) {
            this.lastRedstoneState = currentState;
            this.updateTask();
        }
    }

    public boolean isActive() {
        if (this.world != null && !this.world.isRemote) {
            return this.getMainNode().isOnline();
        }
        return this.active;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Nullable
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        } else if (ISegmentedInventory.CELLS.equals(id)) {
            return this.combinedInventory;
        }
        return null;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.combinedInventory;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        EnumFacing up = this.getOrientation().getSide(appeng.api.orientation.RelativeSide.TOP);
        if (side == up || side == up.getOpposite()) {
            return this.inputCellsExt;
        }
        return this.outputCellsExt;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.inputCells) {
            this.updateTask();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.IOPort, !this.hasWork());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.getMainNode().isActive()) {
            return TickRateModulation.IDLE;
        }

        TickRateModulation result = TickRateModulation.SLEEP;
        long itemsToMove = 256;

        switch (this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item())) {
            case 1 -> itemsToMove *= 2;
            case 2 -> itemsToMove *= 4;
            case 3 -> itemsToMove *= 8;
            default -> {
            }
        }

        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            return TickRateModulation.IDLE;
        }

        for (int slot = 0; slot < NUMBER_OF_CELL_SLOTS; slot++) {
            ItemStack cell = this.inputCells.getStackInSlot(slot);
            StorageCell cellInv = StorageCells.getCellInventory(cell, null);

            if (cellInv == null) {
                this.moveSlot(slot);
                continue;
            }

            if (itemsToMove > 0) {
                itemsToMove = this.transferContents(grid, cellInv, itemsToMove);
                result = itemsToMove > 0 ? TickRateModulation.IDLE : TickRateModulation.URGENT;
            }

            if (itemsToMove > 0 && this.matchesFullnessMode(cellInv) && this.moveSlot(slot)) {
                result = TickRateModulation.URGENT;
            }
        }

        return result;
    }

    public boolean matchesFullnessMode(StorageCell inv) {
        return switch (this.manager.getSetting(Settings.FULLNESS_MODE)) {
            case HALF -> true;
            case EMPTY -> inv.getStatus() == CellState.EMPTY;
            case FULL -> inv.getStatus() == CellState.FULL;
        };
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        for (ItemStack upgrade : this.upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        super.exportSettings(mode, output);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            this.inputCells.writeToNBT(output, "inputCells");
            this.outputCells.writeToNBT(output, "outputCells");
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            this.inputCells.readFromNBT(input, "inputCells");
            this.outputCells.readFromNBT(input, "outputCells");
        }
    }

    private void updateTask() {
        this.getMainNode().ifPresent((grid, node) -> {
            if (this.hasWork()) {
                grid.getTickManager().wakeDevice(node);
            } else {
                grid.getTickManager().sleepDevice(node);
            }
        });
    }

    private boolean hasWork() {
        return this.isEnabled() && !this.inputCells.isEmpty();
    }

    private boolean getRedstoneState() {
        if (this.lastRedstoneState == YesNo.UNDECIDED) {
            this.updateRedstoneState();
        }
        return this.lastRedstoneState == YesNo.YES;
    }

    private boolean isEnabled() {
        if (!this.upgrades.isInstalled(AEItems.REDSTONE_CARD.item())) {
            return true;
        }

        RedstoneMode redstoneMode = this.manager.getSetting(Settings.REDSTONE_CONTROLLED);
        if (redstoneMode == RedstoneMode.IGNORE) {
            return true;
        }
        if (redstoneMode == RedstoneMode.HIGH_SIGNAL) {
            return this.getRedstoneState();
        }
        return !this.getRedstoneState();
    }

    private void onStateChanged() {
        this.saveChanges();
        this.updateTask();
    }

    private long transferContents(IGrid grid, StorageCell cellInv, long itemsToMove) {
        MEStorage networkInv = grid.getStorageService().getInventory();
        KeyCounter srcList;
        MEStorage src;
        MEStorage destination;

        if (this.manager.getSetting(Settings.OPERATION_MODE) == OperationMode.EMPTY) {
            src = cellInv;
            srcList = cellInv.getAvailableStacks();
            destination = networkInv;
        } else {
            src = networkInv;
            srcList = grid.getStorageService().getCachedInventory();
            destination = cellInv;
        }

        IEnergyService energy = grid.getEnergyService();
        boolean didStuff;

        do {
            didStuff = false;

            for (Object2LongMap.Entry<AEKey> srcEntry : srcList) {
                long totalStackSize = srcEntry.getLongValue();
                if (totalStackSize <= 0) {
                    continue;
                }

                AEKey what = srcEntry.getKey();
                long possible = destination.insert(what, totalStackSize, Actionable.SIMULATE, this.mySrc);
                if (possible <= 0) {
                    continue;
                }

                possible = Math.min(possible, itemsToMove * what.getAmountPerOperation());
                possible = src.extract(what, possible, Actionable.MODULATE, this.mySrc);
                if (possible <= 0) {
                    continue;
                }

                long inserted = StorageHelper.poweredInsert(energy, destination, what, possible, this.mySrc);
                if (inserted < possible) {
                    src.insert(what, possible - inserted, Actionable.MODULATE, this.mySrc);
                }

                if (inserted > 0) {
                    itemsToMove -= Math.max(1, inserted / what.getAmountPerOperation());
                    didStuff = true;
                }

                break;
            }
        } while (itemsToMove > 0 && didStuff);

        return itemsToMove;
    }

    private boolean moveSlot(int slot) {
        if (this.outputCells.addItems(this.inputCells.getStackInSlot(slot)).isEmpty()) {
            this.inputCells.setItemDirect(slot, ItemStack.EMPTY);
            return true;
        }
        return false;
    }
}
