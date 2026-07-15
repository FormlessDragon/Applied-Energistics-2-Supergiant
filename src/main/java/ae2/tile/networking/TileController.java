/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.tile.networking;

import ae2.api.config.Actionable;
import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.events.GridControllerChange;
import ae2.api.networking.events.GridPowerStorageChanged;
import ae2.api.networking.events.GridPowerStorageChanged.ChangeType;
import ae2.api.networking.events.GridPowerStorageStateChanged;
import ae2.api.networking.events.GridPowerStorageStateChanged.PowerEventType;
import ae2.api.networking.pathing.ControllerState;
import ae2.api.util.AECableType;
import ae2.block.networking.ControllerBlock;
import ae2.container.GuiIds;
import ae2.core.definitions.AEBlocks;
import ae2.core.gui.GuiOpener;
import ae2.tile.grid.AENetworkedPoweredTile;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public class TileController extends AENetworkedPoweredTile {
    private static final int BLOCK_UPDATE_CLIENTS = 2;

    static {
        GridHelper.addNodeOwnerEventHandler(
            GridControllerChange.class,
            TileController.class,
            TileController::updateState);
    }

    private boolean online;
    private boolean conflicted;

    public TileController() {
        super();
        this.setInternalMaxPower(8000);
        this.setInternalPublicPowerStorage(true);
        this.getMainNode().setIdlePowerUsage(3);
        this.getMainNode().setFlags(GridFlags.CANNOT_CARRY, GridFlags.DENSE_CAPACITY);
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public void onReady() {
        super.onReady();
        updateState();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        this.updateState();
    }

    public void updateState() {
        if (!this.getMainNode().isReady()) {
            return;
        }

        boolean nextOnline = false;
        boolean nextConflicted = false;

        var grid = getMainNode().getGrid();
        if (grid != null && grid.getEnergyService().isNetworkPowered()) {
            nextOnline = true;
            nextConflicted = grid.getPathingService().getControllerState() == ControllerState.CONTROLLER_CONFLICT;
        }

        if (this.online != nextOnline || this.conflicted != nextConflicted) {
            this.online = nextOnline;
            this.conflicted = nextConflicted;
            this.updateControllerStateForClients();
            this.markDirty();
        }
    }

    protected void updateControllerStateForClients() {
        if (this.world == null || this.world.isRemote) {
            return;
        }

        IBlockState state = this.getBlockState();
        if (state == null || !(state.getBlock() instanceof ControllerBlock block)) {
            return;
        }

        IBlockState newState = block.getTileEntityBlockState(state, this);
        if (newState != state) {
            this.world.setBlockState(this.pos, newState, BLOCK_UPDATE_CLIENTS);
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.online);
        data.writeBoolean(this.conflicted);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean nextOnline = data.readBoolean();
        boolean nextConflicted = data.readBoolean();
        if (this.online != nextOnline || this.conflicted != nextConflicted) {
            this.online = nextOnline;
            this.conflicted = nextConflicted;
            changed = true;
        }
        return changed;
    }

    @Override
    protected double getFunnelPowerDemand(double maxReceived) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            return grid.getEnergyService().getEnergyDemand(maxReceived);
        }
        return super.getFunnelPowerDemand(maxReceived);
    }

    @Override
    protected double funnelPowerIntoStorage(double power, Actionable mode) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            return grid.getEnergyService().injectPower(power, mode);
        }
        return super.funnelPowerIntoStorage(power, mode);
    }

    @Override
    protected void emitPowerStateEvent(PowerEventType type) {
        getMainNode().ifPresent(grid -> grid.postEvent(new GridPowerStorageStateChanged(this, type)));
    }

    @Override
    protected void emitPowerStorageChanged(ChangeType type) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.postEvent(new GridPowerStorageChanged(this, type));
        }
    }

    public boolean isOnline() {
        return this.online;
    }

    public boolean isConflicted() {
        return this.conflicted;
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.CONTROLLER.stack(1);
    }

    public void openGui(EntityPlayer player) {
        GuiOpener.openGui(player, GuiIds.GuiKey.CONTROLLER_STATUS, this);
    }
}


