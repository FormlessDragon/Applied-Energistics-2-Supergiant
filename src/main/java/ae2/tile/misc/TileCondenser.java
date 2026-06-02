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
package ae2.tile.misc;

import ae2.api.AECapabilities;
import ae2.api.config.CondenserOutput;
import ae2.api.config.Settings;
import ae2.api.implementations.items.IStorageComponent;
import ae2.api.inventories.BaseInternalInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.stacks.AEFluidKey;
import ae2.api.storage.MEStorage;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.core.definitions.AEItems;
import ae2.tile.AEBaseInvTile;
import ae2.util.ConfigManager;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.CombinedInternalInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.AEItemFilters;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

public class TileCondenser extends AEBaseInvTile implements IConfigurableObject {

    public static final int BYTE_MULTIPLIER = 8;

    private static final IFluidTankProperties[] TANKS = {
        new FluidTankProperties(null, Fluid.BUCKET_VOLUME, true, false)
    };
    private final AppEngInternalInventory outputSlot = new AppEngInternalInventory(this, 1);
    private final AppEngInternalInventory storageSlot = new AppEngInternalInventory(this, 1);
    private final InternalInventory inputSlot = new CondenseItemHandler();
    private final IFluidHandler fluidHandler = new FluidHandler();
    private final CondenserMEStorage meStorage = new CondenserMEStorage(this);
    private final InternalInventory externalInv = new CombinedInternalInventory(this.inputSlot,
        new FilteredInternalInventory(this.outputSlot, AEItemFilters.EXTRACT_ONLY));
    private final ConfigManager cm = new ConfigManager(() -> {
        saveChanges();
        addPower(0);
    });
    private final InternalInventory combinedInv = new CombinedInternalInventory(this.inputSlot, this.outputSlot,
        this.storageSlot);
    private double storedPower;
    public TileCondenser() {
        this.cm.registerSetting(Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.outputSlot.writeToNBT(data, "outputSlot");
        this.storageSlot.writeToNBT(data, "storageSlot");
        this.cm.writeToNBT(data);
        data.setDouble("storedPower", this.getStoredPower());
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.outputSlot.readFromNBT(data, "outputSlot");
        this.storageSlot.readFromNBT(data, "storageSlot");
        this.cm.readFromNBT(data);
        this.setStoredPower(data.getDouble("storedPower"));
    }

    public double getStorage() {
        final ItemStack stack = this.storageSlot.getStackInSlot(0);
        if (!stack.isEmpty() && stack.getItem() instanceof IStorageComponent storageComponent) {
            if (storageComponent.isStorageComponent(stack)) {
                return storageComponent.getBytes(stack) * BYTE_MULTIPLIER;
            }
        }
        return 0;
    }

    public void addPower(double rawPower) {
        this.setStoredPower(this.getStoredPower() + rawPower);
        this.setStoredPower(MathHelper.clamp(this.getStoredPower(), 0.0, this.getStorage()));
        fillOutput();
    }

    private void fillOutput() {
        var requiredPower = this.getRequiredPower();
        while (requiredPower <= this.getStoredPower() && !getOutput().isEmpty() && requiredPower > 0) {
            if (this.canAddOutput()) {
                this.setStoredPower(this.getStoredPower() - requiredPower);
                this.addOutput();
            } else {
                break;
            }
        }
    }

    boolean canAddOutput() {
        return this.outputSlot.insertItem(0, getOutput(), true).isEmpty();
    }

    private void addOutput() {
        this.outputSlot.insertItem(0, getOutput(), false);
    }

    InternalInventory getOutputSlot() {
        return this.outputSlot;
    }

    private ItemStack getOutput() {
        return switch (this.cm.getSetting(Settings.CONDENSER_OUTPUT)) {
            case MATTER_BALLS -> AEItems.MATTER_BALL.stack();
            case SINGULARITY -> AEItems.SINGULARITY.stack();
            default -> ItemStack.EMPTY;
        };
    }

    public double getRequiredPower() {
        return this.cm.getSetting(Settings.CONDENSER_OUTPUT).requiredPower;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.combinedInv;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing side) {
        return this.externalInv;
    }

    @Nullable
    @Override
    public IItemHandler getExposedItemHandler(@Nullable EnumFacing side) {
        return this.externalInv.toItemHandler();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == outputSlot) {
            fillOutput();
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    public double getStoredPower() {
        return this.storedPower;
    }

    private void setStoredPower(double storedPower) {
        this.storedPower = storedPower;
        this.markDirty();
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public MEStorage getMEStorage() {
        return meStorage;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.ME_STORAGE) {
            return true;
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.ME_STORAGE) {
            return (T) this.getMEStorage();
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.getFluidHandler();
        }
        return super.getCapability(capability, facing);
    }

    private class CondenseItemHandler extends BaseInternalInventory {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canAddOutput();
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            if (!stack.isEmpty()) {
                TileCondenser.this.addPower(stack.getCount());
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!canAddOutput()) {
                return stack;
            }
            if (!simulate && !stack.isEmpty()) {
                TileCondenser.this.addPower(stack.getCount());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }

    private class FluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            return TANKS;
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            int amount = resource == null ? 0 : Math.min(resource.amount, Fluid.BUCKET_VOLUME);

            if (doFill && amount > 0) {
                var what = AEFluidKey.of(resource);
                if (what != null) {
                    var transferFactor = (double) what.getAmountPerOperation();
                    TileCondenser.this.addPower(amount / transferFactor);
                }
            }

            return amount;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }




}
