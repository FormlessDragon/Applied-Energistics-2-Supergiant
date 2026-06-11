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
package ae2.tile.storage;

import ae2.api.implementations.blockentities.IChestOrDrive;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNodeListener;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.StorageCell;
import ae2.api.util.AECableType;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.definitions.AEBlocks;
import ae2.core.gui.GuiOpener;
import ae2.helpers.IPriorityHost;
import ae2.me.storage.DriveWatcher;
import ae2.tile.grid.AENetworkedInvTile;
import ae2.util.inv.AppEngCellInventory;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.Nullable;
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

    private static boolean isValidCellSlot(int slot) {
        return slot >= 0 && slot < CELL_COUNT;
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
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);

        int packedState = data.readInt();
        CellState[] cellStates = CellState.values();
        for (int i = 0; i < CELL_COUNT; i++) {
            int stateIndex = (packedState >> (i * 3)) & 0b111;
            CellState state = stateIndex < cellStates.length ? cellStates[stateIndex] : CellState.ABSENT;
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

    @Override
    public CellState getCellStatus(int slot) {
        if (!isValidCellSlot(slot)) {
            return CellState.ABSENT;
        }
        if (isClientSide()) {
            return clientCellState[slot];
        }

        var handler = invBySlot[slot];
        return handler != null ? handler.getStatus() : CellState.ABSENT;
    }

    @Nullable
    @Override
    public Item getCellItem(int slot) {
        if (!isValidCellSlot(slot)) {
            return null;
        }
        if (isClientSide()) {
            return clientCellItems[slot];
        }

        var stack = inv.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack.getItem();
    }

    @Nullable
    @Override
    public MEStorage getCellInventory(int slot) {
        if (!isValidCellSlot(slot)) {
            return null;
        }
        return invBySlot[slot];
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

    @Nullable
    @Override
    public StorageCell getOriginalCellInventory(int slot) {
        if (!isValidCellSlot(slot)) {
            return null;
        }
        return invBySlot[slot] != null ? invBySlot[slot].getCell() : null;
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

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openGui(player, GuiIds.GuiKey.DRIVE, this, true);
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
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && StorageCells.isCellHandled(stack);
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return IAEItemFilter.super.allowExtract(inv, slot, amount);
        }
    }
}
