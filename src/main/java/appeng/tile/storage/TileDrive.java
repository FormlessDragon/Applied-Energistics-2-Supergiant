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

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.util.AECableType;
import appeng.container.GuiIds;
import appeng.container.ISubGui;
import appeng.core.definitions.AEBlocks;
import appeng.core.gui.GuiOpener;
import appeng.helpers.IPriorityHost;
import appeng.me.storage.DriveWatcher;
import appeng.tile.grid.AENetworkedInvTile;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.AppEngCellInventory;
import appeng.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class TileDrive extends AENetworkedInvTile implements IChestOrDrive, IPriorityHost, IStorageProvider {
    private static final int CELL_COUNT = 10;

    private final AppEngCellInventory inv = new AppEngCellInventory(this, CELL_COUNT);
    private final DriveWatcher[] invBySlot = new DriveWatcher[CELL_COUNT];
    private final Item[] clientCellItems = new Item[CELL_COUNT];
    private final CellState[] clientCellState = new CellState[CELL_COUNT];
    private boolean isCached;
    private int priority;
    private boolean wasOnline;
    private boolean clientPowered;

    public TileDrive() {
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).addService(IStorageProvider.class, this);
        inv.setFilter(new CellValidInventoryFilter());
        Arrays.fill(clientCellState, CellState.ABSENT);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.DRIVE.stack();
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT)));
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        updateClientState();

        int packedState = 0;
        for (int i = 0; i < CELL_COUNT; i++) {
            packedState |= clientCellState[i].ordinal() << (i * 3);
        }
        if (clientPowered) {
            packedState |= 1 << 31;
        }
        data.writeInt(packedState);
        for (int i = 0; i < CELL_COUNT; i++) {
            data.writeInt(Item.getIdFromItem(clientCellItems[i]));
        }
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);

        int packedState = data.readInt();
        for (int i = 0; i < CELL_COUNT; i++) {
            var state = CellState.values()[(packedState >> (i * 3)) & 0b111];
            if (clientCellState[i] != state) {
                clientCellState[i] = state;
                changed = true;
            }
        }

        boolean powered = (packedState & (1 << 31)) != 0;
        if (clientPowered != powered) {
            clientPowered = powered;
            changed = true;
        }

        for (int i = 0; i < CELL_COUNT; i++) {
            Item item = Item.getItemById(data.readInt());
            if (clientCellItems[i] != item) {
                clientCellItems[i] = item;
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.isCached = false;
        this.priority = data.getInteger("priority");
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        this.inv.persist();
        super.saveAdditional(data);
        data.setInteger("priority", this.priority);
    }

    @Override
    public int getCellCount() {
        return CELL_COUNT;
    }

    @Override
    public CellState getCellStatus(int slot) {
        if (isClientSide()) {
            return clientCellState[slot];
        }

        var handler = invBySlot[slot];
        return handler != null ? handler.getStatus() : CellState.ABSENT;
    }

    @Override
    public boolean isPowered() {
        if (isClientSide()) {
            return clientPowered;
        }
        return getMainNode().isOnline();
    }

    @Override
    public boolean isCellBlinking(int slot) {
        return false;
    }

    @Nullable
    @Override
    public Item getCellItem(int slot) {
        if (isClientSide()) {
            return clientCellItems[slot];
        }

        var stack = inv.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack.getItem();
    }

    @Nullable
    @Override
    public MEStorage getCellInventory(int slot) {
        return invBySlot[slot];
    }

    @Nullable
    @Override
    public StorageCell getOriginalCellInventory(int slot) {
        return invBySlot[slot] != null ? invBySlot[slot].getCell() : null;
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return super.getCableConnectionType(dir);
    }

    @Override
    public AppEngCellInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (this.isCached) {
            this.isCached = false;
            updateState();
        }

        IStorageProvider.requestUpdate(getMainNode());
        markForUpdate();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        boolean currentOnline = getMainNode().isOnline();
        if (this.wasOnline != currentOnline) {
            this.wasOnline = currentOnline;
            IStorageProvider.requestUpdate(getMainNode());
            updateVisualState();
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        updateState();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        if (getMainNode().isOnline()) {
            updateState();
            for (var inventory : invBySlot) {
                if (inventory != null) {
                    storageMounts.mount(inventory, priority);
                }
            }
        }
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        this.isCached = false;
        updateState();
        IStorageProvider.requestUpdate(getMainNode());
        saveChanges();
    }

    @Override
    public void returnToMainContainer(net.minecraft.entity.player.EntityPlayer player, ISubGui subGui) {
        GuiOpener.openGui(player, GuiIds.GuiKey.DRIVE, this, true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return AEBlocks.DRIVE.stack();
    }

    private void updateState() {
        if (!isCached) {
            double power = 2.0;
            for (int slot = 0; slot < CELL_COUNT; slot++) {
                power += updateStateForSlot(slot);
            }
            getMainNode().setIdlePowerUsage(power);
            isCached = true;
        }
    }

    private double updateStateForSlot(int slot) {
        inv.setHandler(slot, null);
        invBySlot[slot] = null;
        ItemStack stack = inv.getStackInSlot(slot);
        if (!stack.isEmpty()) {
            StorageCell cell = StorageCells.getCellInventory(stack, this::onCellContentChanged);
            if (cell != null) {
                inv.setHandler(slot, cell);
                invBySlot[slot] = new DriveWatcher(cell, stack, this::updateVisualState);
                return cell.getIdleDrain();
            }
        }
        return 0;
    }

    private void onCellContentChanged() {
        saveChanges();
        updateVisualState();
    }

    private void updateVisualState() {
        if (updateClientState()) {
            markForUpdate();
        }
    }

    private boolean updateClientState() {
        if (isClientSide()) {
            return false;
        }

        updateState();

        boolean changed = false;
        boolean powered = getMainNode().isOnline();
        if (clientPowered != powered) {
            clientPowered = powered;
            changed = true;
        }

        for (int i = 0; i < CELL_COUNT; i++) {
            Item item = getCellItem(i);
            if (clientCellItems[i] != item) {
                clientCellItems[i] = item;
                changed = true;
            }

            CellState state = getCellStatus(i);
            if (clientCellState[i] != state) {
                clientCellState[i] = state;
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
    }

    private static class CellValidInventoryFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && StorageCells.isCellHandled(stack);
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return IAEItemFilter.super.allowExtract(inv, slot, amount);
        }
    }
}
