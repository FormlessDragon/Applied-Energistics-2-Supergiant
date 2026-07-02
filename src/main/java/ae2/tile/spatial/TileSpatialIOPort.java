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

package ae2.tile.spatial;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.YesNo;
import ae2.api.implementations.items.ISpatialStorageCell;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.events.GridSpatialEvent;
import ae2.api.networking.spatial.ISpatialService;
import ae2.api.util.AECableType;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.hooks.ticking.TickHandler;
import ae2.tile.grid.AENetworkedInvTile;
import ae2.util.ILevelRunnable;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.WorldServer;

public class TileSpatialIOPort extends AENetworkedInvTile {
    private static final YesNo[] YES_NO_VALUES = YesNo.values();

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 2);
    private final InternalInventory invExt = new FilteredInternalInventory(this.inv, new SpatialIOFilter());
    private final ILevelRunnable transitionCallback = level -> transition();
    private YesNo lastRedstoneState = YesNo.UNDECIDED;
    private boolean active;

    public TileSpatialIOPort() {
        this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        if (data.hasKey("lastRedstoneState")) {
            int state = data.getInteger("lastRedstoneState");
            this.lastRedstoneState = state >= 0 && state < YES_NO_VALUES.length
                ? YES_NO_VALUES[state]
                : YesNo.UNDECIDED;
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setInteger("lastRedstoneState", this.lastRedstoneState.ordinal());
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.isActive());
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        if (data.readableBytes() < 1) {
            AELog.error("Malformed spatial IO port update payload at %s: missing active state byte", this.pos);
            return changed;
        }
        boolean nextActive = data.readBoolean();
        changed = changed || this.active != nextActive;
        this.active = nextActive;
        return changed;
    }

    public void updateRedstoneState() {
        if (this.world == null) {
            return;
        }

        YesNo currentState = this.world.isBlockPowered(this.pos) ? YesNo.YES : YesNo.NO;
        if (this.lastRedstoneState != currentState) {
            this.lastRedstoneState = currentState;
            if (currentState == YesNo.YES) {
                this.triggerTransition();
            }
            this.saveChanges();
        }
    }

    private void triggerTransition() {
        if (this.world != null && !this.world.isRemote) {
            ItemStack cell = this.inv.getStackInSlot(0);
            if (this.isSpatialCell(cell)) {
                TickHandler.instance().addCallable(null, this.transitionCallback);
            }
        }
    }

    private boolean isSpatialCell(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ISpatialStorageCell spatialStorageCell)) {
            return false;
        }
        return spatialStorageCell.isSpatialStorage(stack);
    }

    private void transition() {
        if (!(this.world instanceof WorldServer serverLevel)) {
            return;
        }

        ItemStack cell = this.inv.getStackInSlot(0);
        if (!this.isSpatialCell(cell) || !this.inv.getStackInSlot(1).isEmpty()) {
            return;
        }

        if (!this.getMainNode().isActive()) {
            return;
        }

        ISpatialStorageCell spatialCell = (ISpatialStorageCell) cell.getItem();
        this.getMainNode().ifPresent((grid, node) -> {
            ISpatialService spatial = grid.getSpatialService();
            if (!spatial.hasRegion() || !spatial.isValidRegion()) {
                return;
            }

            IEnergyService energy = grid.getEnergyService();
            double requiredPower = spatial.requiredPower();
            double extracted = energy.extractAEPower(requiredPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (Math.abs(extracted - requiredPower) >= requiredPower * 0.001) {
                return;
            }

            GridSpatialEvent event = grid.postEvent(new GridSpatialEvent(this.getWorld(), this.getPos(), requiredPower));
            if (event.isTransitionPrevented()) {
                return;
            }

            int playerId = node.getOwningPlayerId();
            if (spatialCell.doSpatialTransition(cell, serverLevel, spatial.getMin(), spatial.getMax(), playerId)) {
                energy.extractAEPower(requiredPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
                this.inv.setItemDirect(0, ItemStack.EMPTY);
                this.inv.setItemDirect(1, cell);
            }
        });
    }

    public boolean isActive() {
        if (this.world != null && !this.world.isRemote) {
            return this.getMainNode().isOnline();
        }
        return this.active;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.markForUpdate();
        }
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.SPATIAL_IO_PORT.stack();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return super.getCableConnectionType(dir);
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.invExt;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    private class SpatialIOFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return slot == 1;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return slot == 0 && TileSpatialIOPort.this.isSpatialCell(stack);
        }
    }
}
