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

package ae2.tile.grid;

import ae2.api.AECapabilities;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IManagedGridNode;
import ae2.api.orientation.BlockOrientation;
import ae2.api.util.AECableType;
import ae2.me.helpers.IGridConnectedTile;
import ae2.me.helpers.TileNodeListener;
import ae2.tile.AEBaseTile;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.Nullable;

public class AENetworkedTile extends AEBaseTile implements IGridConnectedTile {

    private final IManagedGridNode mainNode;

    public AENetworkedTile() {
        super();
        this.mainNode = createMainNode()
            .setVisualRepresentation(getItemFromTile())
            .setInWorldNode(true)
            .setTagName("proxy");
        onGridConnectableSidesChanged();
    }

    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, TileNodeListener.INSTANCE);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.getMainNode().loadFromNBT(data);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.getMainNode().saveToNBT(data);
    }

    @Override
    public final IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.SMART;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.getMainNode().destroy();
    }

    @Override
    public void onReady() {
        super.onReady();
        this.getMainNode().create(getWorld(), getPos());
        this.refreshBlockStateAfterReady();
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        onGridConnectableSidesChanged();
    }

    protected final void onGridConnectableSidesChanged() {
        getMainNode().setExposedOnSides(getGridConnectableSides(getOrientation()));
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.IN_WORLD_GRID_NODE_HOST) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.IN_WORLD_GRID_NODE_HOST) {
            return AECapabilities.IN_WORLD_GRID_NODE_HOST.cast(this);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.getMainNode().destroy();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        scheduleInit();
    }
}
