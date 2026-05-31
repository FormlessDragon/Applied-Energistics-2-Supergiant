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

package ae2.parts.reporting;

import ae2.api.implementations.parts.IMonitorPart;
import ae2.api.networking.GridFlags;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.parts.AEBasePart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public abstract class AbstractReportingPart extends AEBasePart implements IMonitorPart {

    private byte spin = 0;
    private int opacity = -1;

    protected AbstractReportingPart(IPartItem<?> partItem, boolean requireChannel) {
        super(partItem);

        if (requireChannel) {
            this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
            this.getMainNode().setIdlePowerUsage(1.0 / 2.0);
        } else {
            this.getMainNode().setIdlePowerUsage(1.0 / 16.0);
        }
    }

    @Override
    public final void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(4, 4, 13, 12, 12, 14);
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.IBlockAccess level, BlockPos pos, BlockPos neighbor) {
        EnumFacing side = this.getSide();
        if (side != null && pos.offset(side).equals(neighbor)) {
            this.opacity = -1;
            this.getHost().markForUpdate();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.spin = data.getByte("spin");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setByte("spin", this.getSpin());
    }

    @Override
    public void writeToStream(PacketBuffer data) {
        super.writeToStream(data);
        data.writeByte(this.getSpin());
    }

    @Override
    public boolean readFromStream(PacketBuffer data) {
        boolean changed = super.readFromStream(data);
        byte oldSpin = this.spin;
        this.spin = data.readByte();
        return changed || oldSpin != this.spin;
    }

    @Override
    public final int getLightLevel() {
        return this.blockLight(this.isPowered() ? this.isLightSource() ? 15 : 9 : 0);
    }

    @Override
    public final void onPlacement(EntityPlayer player) {
        super.onPlacement(player);

        byte rotation = (byte) (MathHelper.floor(player.rotationYaw * 4.0F / 360.0F + 2.5D) & 3);
        EnumFacing side = getSide();
        if (side == EnumFacing.UP || side == EnumFacing.DOWN) {
            this.spin = rotation;
        }
    }

    @Override
    public Object getModelData() {
        return this.spin;
    }

    protected IPartModel selectModel(IPartModel offModels, IPartModel onModels, IPartModel hasChannelModels) {
        if (this.isActive()) {
            return hasChannelModels;
        } else if (this.isPowered()) {
            return onModels;
        } else {
            return offModels;
        }
    }

    public final byte getSpin() {
        return this.spin;
    }

    private int blockLight(int emit) {
        if (this.opacity == -1) {
            TileEntity tile = this.getHost() != null ? this.getHost().getTileEntity() : null;
            EnumFacing side = this.getSide();
            if (tile == null || side == null) {
                return emit;
            }

            World world = tile.getWorld();
            if (world == null) {
                return emit;
            }

            BlockPos pos = tile.getPos().offset(side);
            this.opacity = world.getBlockState(pos).getLightOpacity(world, pos);
        }

        return Math.max(0, emit - this.opacity);
    }

    public abstract boolean isLightSource();

}

