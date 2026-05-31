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

package ae2.parts.misc;

import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartHost;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.AECableType;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.AEBasePart;
import ae2.parts.PartModel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.EnumSet;

public class ToggleBusPart extends AEBasePart {

    @PartModels
    public static final ResourceLocation MODEL_BASE = AppEng.makeId("part/toggle_bus_base");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = AppEng.makeId("part/toggle_bus_status_off");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = AppEng.makeId("part/toggle_bus_status_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = AppEng.makeId("part/toggle_bus_status_has_channel");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_STATUS_HAS_CHANNEL);

    private final IManagedGridNode outerNode = GridHelper.createManagedNode(this, NodeListener.INSTANCE)
                                                         .setTagName("outer")
                                                         .setInWorldNode(true)
                                                         .setIdlePowerUsage(0.0)
                                                         .setFlags(GridFlags.PREFERRED);

    private IGridConnection connection;
    private boolean hasRedstone;
    private boolean clientSideEnabled;

    public ToggleBusPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode().setIdlePowerUsage(0.0);
        this.getMainNode().setFlags(GridFlags.PREFERRED);
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);
        data.writeBoolean(isEnabled());
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        boolean changed = super.readFromStream(data);
        boolean wasEnabled = this.clientSideEnabled;
        this.clientSideEnabled = data.readBoolean();
        return changed || wasEnabled != this.clientSideEnabled;
    }

    @Override
    public void writeVisualStateToNBT(NBTTagCompound data) {
        super.writeVisualStateToNBT(data);
        data.setBoolean("on", isEnabled());
    }

    @Override
    public void readVisualStateFromNBT(NBTTagCompound data) {
        super.readVisualStateFromNBT(data);
        this.clientSideEnabled = data.getBoolean("on");
    }

    protected boolean isEnabled() {
        if (isClientSide()) {
            return this.clientSideEnabled;
        }
        return this.getHost().hasRedstone();
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6, 6, 11, 10, 10, 16);
    }

    @Override
    public void onNeighborChanged(IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        boolean oldHasRedstone = this.hasRedstone;
        this.hasRedstone = this.getHost().hasRedstone();

        if (this.hasRedstone != oldHasRedstone) {
            this.updateInternalState();
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.getOuterNode().loadFromNBT(extra);
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.getOuterNode().saveToNBT(extra);
    }

    @Override
    public void removeFromWorld() {
        super.removeFromWorld();
        this.getOuterNode().destroy();
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.getOuterNode().create(getLevel(), getTileEntity().getPos());
        this.hasRedstone = this.getHost().hasRedstone();
        this.updateInternalState();
    }

    @Override
    public void setPartHostInfo(EnumFacing side, IPartHost host, TileEntity blockEntity) {
        super.setPartHostInfo(side, host, blockEntity);
        this.outerNode.setExposedOnSides(EnumSet.of(side));
    }

    @Override
    public IGridNode getExternalFacingNode() {
        return this.getOuterNode().getNode();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    @Override
    public void onPlacement(EntityPlayer player) {
        super.onPlacement(player);
        this.getOuterNode().setOwningPlayer(player);
    }

    private void updateInternalState() {
        boolean enabled = this.isEnabled();
        if (enabled == (this.connection == null)
            && this.getMainNode().getNode() != null
            && this.getOuterNode().getNode() != null) {
            if (enabled) {
                this.connection = GridHelper.createConnection(this.getMainNode().getNode(), this.getOuterNode().getNode());
            } else {
                this.connection.destroy();
                this.connection = null;
            }
        }
    }

    IManagedGridNode getOuterNode() {
        return this.outerNode;
    }

    @Override
    public IPartModel getStaticModels() {
        if (isEnabled() && this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (isEnabled() && this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }
}


