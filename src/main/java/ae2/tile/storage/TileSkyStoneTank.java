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

import ae2.tile.AEBaseTile;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import org.jetbrains.annotations.Nullable;

public class TileSkyStoneTank extends AEBaseTile {

    public static final int BUCKET_CAPACITY = 16;

    private final FluidTank tank = new FluidTank(Fluid.BUCKET_VOLUME * BUCKET_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            TileSkyStoneTank.this.markForUpdate();
            TileSkyStoneTank.this.saveChanges();
        }
    };

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        NBTTagCompound tankNbt = new NBTTagCompound();
        this.tank.writeToNBT(tankNbt);
        if (!tankNbt.isEmpty()) {
            data.setTag("tank", tankNbt);
        }
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.tank.readFromNBT(data.getCompoundTag("tank"));
    }

    public boolean onPlayerUse(EntityPlayer player, EnumHand hand) {
        return FluidUtil.interactWithFluidHandler(player, hand, this.tank);
    }

    public FluidTank getTank() {
        return this.tank;
    }

    public IFluidHandler getFluidHandler() {
        return this.tank;
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        super.readFromStream(data);
        try {
            NBTTagCompound tag = ByteBufUtils.readTag(data);
            this.tank.readFromNBT(tag == null ? new NBTTagCompound() : tag);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        NBTTagCompound tag = new NBTTagCompound();
        this.tank.writeToNBT(tag);
        ByteBufUtils.writeTag(data, tag);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.getFluidHandler();
        }
        return super.getCapability(capability, facing);
    }
}
