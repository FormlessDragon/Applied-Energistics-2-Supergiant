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

package ae2.block.networking;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.block.AEBaseBlockItem;
import ae2.core.localization.ItemModText;
import ae2.util.Platform;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class EnergyCellBlockItem extends AEBaseBlockItem implements IAEItemPowerStorage {
    private static final String STORED_ENERGY = "stored_energy";

    public EnergyCellBlockItem(EnergyCellBlock block) {
        super(block);
    }

    @Override
    public void addCheckedInformation(ItemStack itemStack, World worldIn, List<String> toolTip,
                                      ITooltipFlag advancedTooltips) {
        toolTip.add(ItemModText.StoredEnergy.getLocal((long) getAECurrentPower(itemStack), (long) getAEMaxPower(itemStack)));
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                float hitX, float hitY, float hitZ, IBlockState newState) {
        syncBlockEntityTag(stack);
        return super.placeBlockAt(stack, player, world, pos, side, hitX, hitY, hitZ, newState);
    }

    @Override
    public double injectAEPower(ItemStack is, double amount, Actionable mode) {
        double currentPower = getAECurrentPower(is);
        double required = getAEMaxPower(is) - currentPower;
        double overflow = Math.clamp(amount - required, 0.0, amount);

        if (mode == Actionable.MODULATE) {
            setStoredEnergy(is, currentPower + Math.min(required, amount));
        }

        return overflow;
    }

    @Override
    public double extractAEPower(ItemStack is, double amount, Actionable mode) {
        double currentPower = getAECurrentPower(is);
        double fulfillable = Math.min(amount, currentPower);

        if (mode == Actionable.MODULATE) {
            setStoredEnergy(is, currentPower - fulfillable);
        }

        return fulfillable;
    }

    @Override
    public double getAEMaxPower(ItemStack is) {
        NBTTagCompound blockEntityTag = getBlockEntityTag(is);
        if (blockEntityTag.hasKey("internalMaxPower")) {
            return blockEntityTag.getDouble("internalMaxPower");
        }
        return getBlock().getMaxPower();
    }

    @Override
    public double getAECurrentPower(ItemStack is) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag != null && tag.hasKey(STORED_ENERGY, 99)) {
            NBTBase storedEnergy = tag.getTag(STORED_ENERGY);
            if (storedEnergy instanceof NBTTagDouble) {
                return ((NBTTagDouble) storedEnergy).getDouble();
            }
        }
        return getBlockEntityTag(is).getDouble("internalCurrentPower");
    }

    @Override
    public AccessRestriction getPowerFlow(ItemStack is) {
        return AccessRestriction.WRITE;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return getBlock().getChargeRate();
    }

    public EnergyCellBlock getBlock() {
        return (EnergyCellBlock) getBlockType();
    }

    private void setStoredEnergy(ItemStack stack, double amount) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        if (amount < 0.00001) {
            tag.removeTag(STORED_ENERGY);
        } else {
            tag.setDouble(STORED_ENERGY, amount);
        }

        NBTTagCompound blockEntityTag = getBlockEntityTag(stack);
        if (amount < 0.00001) {
            blockEntityTag.removeTag("internalCurrentPower");
        } else {
            blockEntityTag.setDouble("internalCurrentPower", amount);
        }
        blockEntityTag.setDouble("internalMaxPower", getBlock().getMaxPower());
        tag.setTag("BlockEntityTag", blockEntityTag);
    }

    private void syncBlockEntityTag(ItemStack stack) {
        double storedEnergy = getAECurrentPower(stack);
        NBTTagCompound tag = Platform.openNbtData(stack);
        NBTTagCompound blockEntityTag = getBlockEntityTag(stack);
        if (storedEnergy < 0.00001) {
            blockEntityTag.removeTag("internalCurrentPower");
        } else {
            blockEntityTag.setDouble("internalCurrentPower", storedEnergy);
        }
        blockEntityTag.setDouble("internalMaxPower", getAEMaxPower(stack));
        tag.setTag("BlockEntityTag", blockEntityTag);
    }

    private NBTTagCompound getBlockEntityTag(ItemStack stack) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        if (tag.hasKey("BlockEntityTag", 10)) {
            return tag.getCompoundTag("BlockEntityTag");
        }
        return new NBTTagCompound();
    }
}
