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

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridMultiblock;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.orientation.BlockOrientation;
import ae2.me.cluster.IAEMultiBlock;
import ae2.me.cluster.implementations.SpatialPylonCalculator;
import ae2.me.cluster.implementations.SpatialPylonCluster;
import ae2.tile.grid.AENetworkedTile;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class TileSpatialPylon extends AENetworkedTile implements IAEMultiBlock<SpatialPylonCluster> {

    private final SpatialPylonCalculator calculator = new SpatialPylonCalculator(this);
    private SpatialPylonCluster cluster;
    private boolean removing;

    private boolean clientPowered;
    private boolean clientOnline;
    private AxisPosition clientAxisPosition = AxisPosition.NONE;
    private SpatialPylonCluster.Axis clientAxis = SpatialPylonCluster.Axis.UNFORMED;
    private boolean hadLight;

    public TileSpatialPylon() {
        this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.MULTIBLOCK)
            .setIdlePowerUsage(0.5)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);
    }

    @Override
    public ItemStack getItemFromTile() {
        if (this.world == null) {
            return ItemStack.EMPTY;
        }
        Block block = this.world.getBlockState(this.pos).getBlock();
        Item item = Item.getItemFromBlock(block);
        return item == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    @Override
    public void onChunkUnloaded() {
        this.disconnect(false);
        super.onChunkUnloaded();
    }

    @Override
    public void onReady() {
        super.onReady();
        if (this.world != null && !this.world.isRemote) {
            this.calculator.calculateMultiblock(this.world, this.pos);
        }
    }

    @Override
    public void setRemoved() {
        this.removing = true;
        this.disconnect(false);
        super.setRemoved();
    }

    public void neighborChanged(BlockPos changedPos) {
        if (this.world != null && !this.world.isRemote) {
            this.calculator.updateMultiblockAfterNeighborUpdate(this.world, this.pos, changedPos);
        }
    }

    @Override
    public void disconnect(boolean b) {
        if (this.cluster != null) {
            this.cluster.destroy();
            this.updateStatus(null);
        }
    }

    @Override
    public SpatialPylonCluster getCluster() {
        return this.cluster;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    public void updateStatus(SpatialPylonCluster cluster) {
        if (this.removing) {
            return;
        }
        this.cluster = cluster;
        this.onGridConnectableSidesChanged();
        this.recalculateDisplay();
    }

    @Override
    public Set<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return this.cluster == null ? EnumSet.noneOf(EnumFacing.class) : EnumSet.allOf(EnumFacing.class);
    }

    public void recalculateDisplay() {
        AxisPosition axisPosition = AxisPosition.NONE;
        SpatialPylonCluster.Axis axis = SpatialPylonCluster.Axis.UNFORMED;
        boolean powered = false;
        boolean online = false;

        if (this.cluster != null) {
            if (this.cluster.getBoundsMin().equals(this.pos)) {
                axisPosition = AxisPosition.START;
            } else if (this.cluster.getBoundsMax().equals(this.pos)) {
                axisPosition = AxisPosition.END;
            } else {
                axisPosition = AxisPosition.MIDDLE;
            }

            axis = this.cluster.getCurrentAxis();
            powered = this.getMainNode().isPowered();
            online = this.cluster.isValid() && this.getMainNode().isOnline();
        }

        if (powered != this.clientPowered || online != this.clientOnline || axisPosition != this.clientAxisPosition
            || axis != this.clientAxis) {
            this.clientPowered = powered;
            this.clientOnline = online;
            this.clientAxisPosition = axisPosition;
            this.clientAxis = axis;
            this.markForUpdate();
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.clientPowered);
        data.writeBoolean(this.clientOnline);
        data.writeByte(this.clientAxisPosition.ordinal());
        data.writeByte(this.clientAxis.ordinal());
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);

        boolean powered = data.readBoolean();
        boolean online = data.readBoolean();
        AxisPosition[] axisPositions = AxisPosition.values();
        int axisPositionOrdinal = data.readUnsignedByte();
        AxisPosition axisPosition = axisPositionOrdinal < axisPositions.length
            ? axisPositions[axisPositionOrdinal]
            : AxisPosition.NONE;

        SpatialPylonCluster.Axis[] axes = SpatialPylonCluster.Axis.values();
        int axisOrdinal = data.readUnsignedByte();
        SpatialPylonCluster.Axis axis = axisOrdinal < axes.length
            ? axes[axisOrdinal]
            : SpatialPylonCluster.Axis.UNFORMED;

        changed = changed || powered != this.clientPowered || online != this.clientOnline
            || axisPosition != this.clientAxisPosition || axis != this.clientAxis;

        this.clientPowered = powered;
        this.clientOnline = online;
        this.clientAxisPosition = axisPosition;
        this.clientAxis = axis;

        return changed;
    }

    @Override
    protected void saveVisualState(NBTTagCompound data) {
        super.saveVisualState(data);
        data.setBoolean("powered", this.clientPowered);
        data.setBoolean("online", this.clientOnline);
        data.setByte("axisPosition", (byte) this.clientAxisPosition.ordinal());
        data.setByte("axis", (byte) this.clientAxis.ordinal());
    }

    @Override
    protected void loadVisualState(NBTTagCompound data) {
        super.loadVisualState(data);
        this.clientPowered = data.getBoolean("powered");
        this.clientOnline = data.getBoolean("online");

        int axisPositionOrdinal = data.getByte("axisPosition");
        AxisPosition[] axisPositions = AxisPosition.values();
        if (axisPositionOrdinal >= 0 && axisPositionOrdinal < axisPositions.length) {
            this.clientAxisPosition = axisPositions[axisPositionOrdinal];
        }

        int axisOrdinal = data.getByte("axis");
        SpatialPylonCluster.Axis[] axes = SpatialPylonCluster.Axis.values();
        if (axisOrdinal >= 0 && axisOrdinal < axes.length) {
            this.clientAxis = axes[axisOrdinal];
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.GRID_BOOT) {
            this.recalculateDisplay();
        }
    }

    public boolean isPoweredOn() {
        return this.clientPowered;
    }

    public int getLightValue() {
        return this.clientPowered ? 8 : 0;
    }

    @Override
    public void markForUpdate() {
        super.markForUpdate();
        if (this.world != null && !this.world.isRemote) {
            boolean hasLight = this.getLightValue() > 0;
            if (hasLight != this.hadLight) {
                this.hadLight = hasLight;
                this.world.checkLight(this.pos);
            }
        }
    }

    public ClientState getRenderState() {
        return new ClientState(this.clientPowered, this.clientOnline, this.clientAxisPosition, this.clientAxis);
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        if (this.cluster == null) {
            return Collections.emptyIterator();
        }

        List<IGridNode> nodes = new ObjectArrayList<>();
        Iterator<? extends TileEntity> iterator = this.cluster.getBlockEntities();
        while (iterator.hasNext()) {
            IGridNode node = ((TileSpatialPylon) iterator.next()).getGridNode();
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes.iterator();
    }

    public enum AxisPosition {
        NONE,
        START,
        MIDDLE,
        END
    }

    public record ClientState(boolean powered, boolean online, AxisPosition axisPosition,
                              SpatialPylonCluster.Axis axis) {
        public static final ClientState DEFAULT = new ClientState(false, false, AxisPosition.NONE,
            SpatialPylonCluster.Axis.UNFORMED);

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ClientState(
                boolean powered1, boolean online1, AxisPosition position, SpatialPylonCluster.Axis axis1
            ))) {
                return false;
            }
            return this.powered == powered1 && this.online == online1
                && this.axisPosition == position && this.axis == axis1;
        }

        @Override
        public int hashCode() {
            int result = Boolean.hashCode(this.powered);
            result = 31 * result + Boolean.hashCode(this.online);
            result = 31 * result + this.axisPosition.hashCode();
            result = 31 * result + this.axis.hashCode();
            return result;
        }

        @Override
        public @NotNull String toString() {
            return "ClientState[powered=" + this.powered + ", online=" + this.online + ", axisPosition="
                + this.axisPosition + ", axis=" + this.axis + "]";
        }
    }
}
