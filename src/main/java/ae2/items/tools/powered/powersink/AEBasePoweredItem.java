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

package ae2.items.tools.powered.powersink;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.items.AEBaseItem;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public abstract class AEBasePoweredItem extends AEBaseItem implements IAEItemPowerStorage {
    private static final String CURRENT_POWER_NBT_KEY = "internalCurrentPower";
    private static final String MAX_POWER_NBT_KEY = "internalMaxPower";
    private static final String STORED_ENERGY = "stored_energy";
    private static final String ENERGY_CAPACITY = "energy_capacity";
    private static final double MIN_POWER = 0.0001;

    private final double powerCapacity;

    protected AEBasePoweredItem(final double powerCapacity) {
        this.setMaxStackSize(1);
        this.setMaxDamage(32);
        this.hasSubtypes = false;
        this.setFull3D();
        this.powerCapacity = powerCapacity;
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);
        lines.add((int) this.getAECurrentPower(stack) + " / " + (int) this.getAEMaxPower(stack) + " AE");
    }

    @Override
    public boolean isDamageable() {
        return true;
    }

    @Override
    protected void getCheckedSubItems(final CreativeTabs creativeTab, final NonNullList<ItemStack> itemStacks) {
        super.getCheckedSubItems(creativeTab, itemStacks);

        final ItemStack charged = new ItemStack(this, 1);
        setAEMaxPower(charged, this.getAEMaxPower(charged));
        setAECurrentPower(charged, this.getAEMaxPower(charged));
        itemStacks.add(charged);
    }

    @Override
    public boolean isRepairable() {
        return false;
    }

    @Override
    public double getDurabilityForDisplay(final ItemStack is) {
        return 1 - this.getAECurrentPower(is) / this.getAEMaxPower(is);
    }

    @Override
    public boolean isDamaged(final ItemStack stack) {
        return true;
    }

    @Override
    public void setDamage(final ItemStack stack, final int damage) {
    }

    @Override
    public double injectAEPower(final ItemStack is, final double amount, Actionable mode) {
        final double maxStorage = this.getAEMaxPower(is);
        final double currentStorage = this.getAECurrentPower(is);
        final double required = maxStorage - currentStorage;
        final double overflow = amount - required;

        if (mode == Actionable.MODULATE) {
            final double toAdd = Math.min(amount, required);
            setAECurrentPower(is, currentStorage + toAdd);
        }

        return Math.max(0, overflow);
    }

    @Override
    public double extractAEPower(final ItemStack is, final double amount, Actionable mode) {
        final double currentStorage = this.getAECurrentPower(is);
        final double fulfillable = Math.min(amount, currentStorage);

        if (mode == Actionable.MODULATE) {
            setAECurrentPower(is, currentStorage - fulfillable);
        }

        return fulfillable;
    }

    @Override
    public double getAEMaxPower(final ItemStack is) {
        return readLegacyCompatibleDouble(is, ENERGY_CAPACITY, MAX_POWER_NBT_KEY, this.powerCapacity);
    }

    @Override
    public double getAECurrentPower(final ItemStack is) {
        return readLegacyCompatibleDouble(is, STORED_ENERGY, CURRENT_POWER_NBT_KEY, 0);
    }

    protected final void setAECurrentPower(ItemStack stack, double power) {
        final NBTTagCompound data = openNbtData(stack);
        if (power < MIN_POWER) {
            data.removeTag(STORED_ENERGY);
            data.removeTag(CURRENT_POWER_NBT_KEY);
        } else {
            data.setDouble(STORED_ENERGY, power);
            data.removeTag(CURRENT_POWER_NBT_KEY);
        }
    }

    protected final void setAEMaxPower(ItemStack stack, double maxPower) {
        final NBTTagCompound data = openNbtData(stack);
        if (Math.abs(maxPower - this.powerCapacity) < MIN_POWER) {
            data.removeTag(ENERGY_CAPACITY);
            data.removeTag(MAX_POWER_NBT_KEY);
        } else {
            data.setDouble(ENERGY_CAPACITY, maxPower);
            data.removeTag(MAX_POWER_NBT_KEY);
        }

        if (getAECurrentPower(stack) > maxPower) {
            setAECurrentPower(stack, maxPower);
        }
    }

    @Override
    public AccessRestriction getPowerFlow(final ItemStack is) {
        return AccessRestriction.WRITE;
    }

    @Override
    public double getChargeRate(ItemStack stack) {
        return 80.0;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return new PoweredItemCapabilities(stack, this);
    }

    protected final NBTTagCompound openNbtData(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    private double readLegacyCompatibleDouble(ItemStack stack, String key, String legacyKey, double defaultValue) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return defaultValue;
        }
        if (tag.hasKey(key, 99)) {
            return tag.getDouble(key);
        }
        if (tag.hasKey(legacyKey)) {
            return tag.getDouble(legacyKey);
        }
        return defaultValue;
    }
}
