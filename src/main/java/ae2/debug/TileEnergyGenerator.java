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

package ae2.debug;

import ae2.tile.AEBaseTile;
import ae2.tile.ServerTickingTile;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.IEnergyStorage;

public class TileEnergyGenerator extends AEBaseTile implements ServerTickingTile, IEnergyStorage {
    private int generationRate = 8;

    @Override
    public void serverTick() {
        var level = this.getWorld();
        if (level == null || level.isRemote) {
            return;
        }

        int tier = 1;
        for (EnumFacing facing : EnumFacing.VALUES) {
            TileEntity te = level.getTileEntity(this.pos.offset(facing));
            if (te instanceof TileEnergyGenerator) {
                tier++;
            }
        }

        int energyToInsert = 1;
        for (int i = 0; i < tier; i++) {
            energyToInsert *= this.generationRate;
        }

        for (EnumFacing facing : EnumFacing.VALUES) {
            TileEntity te = level.getTileEntity(this.pos.offset(facing));
            if (te != null && te.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, facing.getOpposite())) {
                IEnergyStorage consumer = te.getCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY,
                    facing.getOpposite());
                if (consumer != null && consumer.canReceive()) {
                    consumer.receiveEnergy(energyToInsert, false);
                }
            }
        }
    }

    public int getGenerationRate() {
        return this.generationRate;
    }

    public void setGenerationRate(int generationRate) {
        this.generationRate = generationRate;
        this.markForUpdate();
        this.saveChanges();
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        if (data.hasKey("generationRate")) {
            this.generationRate = data.getInteger("generationRate");
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setInteger("generationRate", this.generationRate);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        super.readFromStream(data);
        this.generationRate = data.readInt();
        return true;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeInt(this.generationRate);
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return 0;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return maxExtract;
    }

    @Override
    public int getEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canExtract() {
        return true;
    }

    @Override
    public boolean canReceive() {
        return false;
    }

    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability,
                                 EnumFacing facing) {
        if (capability == net.minecraftforge.energy.CapabilityEnergy.ENERGY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, EnumFacing facing) {
        if (capability == net.minecraftforge.energy.CapabilityEnergy.ENERGY) {
            return (T) this;
        }
        return super.getCapability(capability, facing);
    }
}
