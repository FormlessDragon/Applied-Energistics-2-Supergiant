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

package ae2.tile;

import ae2.api.inventories.InternalInventory;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;

public abstract class AEBaseInvTile extends AEBaseTile implements InternalInventoryHost {

    public AEBaseInvTile() {
        super();
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        AppEngInternalInventory inv = this.getPersistedInternalInventory();
        if (inv != null) {
            inv.readFromNBT(data, "inv");
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        AppEngInternalInventory inv = this.getPersistedInternalInventory();
        if (inv != null) {
            inv.writeToNBT(data, "inv");
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        this.saveChanges();
    }

    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.getInternalInventory();
    }

    @Nullable
    public IItemHandler getExposedItemHandler(@Nullable EnumFacing side) {
        InternalInventory exposed = side == null ? this.getInternalInventory() : this.getExposedInventoryForSide(side);
        return exposed.size() > 0 ? exposed.toItemHandler() : null;
    }

    public void addAdditionalDrops(List<ItemStack> drops) {
        for (ItemStack stack : this.getInternalInventory()) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    public void clearContent() {
        this.getInternalInventory().clear();
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return this.getExposedItemHandler(facing) != null;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) this.getExposedItemHandler(facing);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public boolean isClientSide() {
        return this.getWorld() != null && this.getWorld().isRemote;
    }

    @Nullable
    private AppEngInternalInventory getPersistedInternalInventory() {
        InternalInventory inventory = this.getInternalInventory();
        if (inventory instanceof AppEngInternalInventory appEngInternalInventory) {
            if (appEngInternalInventory.getHost() != this) {
                return null;
            }
            return appEngInternalInventory;
        }
        return null;
    }
}
