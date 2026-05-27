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

package appeng.block.networking;

import appeng.block.AEBaseTileBlock;
import appeng.tile.networking.TileEnergyCell;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class EnergyCellBlock extends AEBaseTileBlock<TileEnergyCell> {
    public static final int MAX_FULLNESS = 4;
    public static final PropertyInteger ENERGY_STORAGE = PropertyInteger.create("fullness", 0, MAX_FULLNESS);
    private static final String STORED_ENERGY = "stored_energy";
    private final double maxPower;
    private final double chargeRate;
    private final int priority;

    public EnergyCellBlock(double maxPower, double chargeRate, int priority) {
        super(Material.GLASS);
        this.maxPower = maxPower;
        this.chargeRate = chargeRate;
        this.priority = priority;
        this.setHardness(0.5F);
        this.setResistance(3.0F);
        this.setTileEntity(TileEnergyCell.class);
        this.setDefaultState(this.blockState.getBaseState().withProperty(ENERGY_STORAGE, 0));
    }

    @Override
    public void getSubBlocks(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks) {
        super.getSubBlocks(creativeTab, itemStacks);

        ItemStack charged = new ItemStack(this);
        setStoredEnergyTag(charged, getMaxPower(), getMaxPower());
        itemStacks.add(charged);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(ENERGY_STORAGE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ENERGY_STORAGE);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int safe = Math.clamp(meta, 0, MAX_FULLNESS);
        return this.getDefaultState().withProperty(ENERGY_STORAGE, safe);
    }

    public double getMaxPower() {
        return this.maxPower;
    }

    public double getChargeRate() {
        return this.chargeRate;
    }

    public int getPriority() {
        return this.priority;
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileEnergyCell tileEntity) {
        double maxPower = tileEntity.getAEMaxPower();
        int fullness = maxPower > 0
            ? TileEnergyCell.getStorageLevelFromFillFactor(tileEntity.getAECurrentPower() / maxPower)
            : 0;
        return currentState.withProperty(ENERGY_STORAGE, fullness);
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World worldIn, BlockPos pos) {
        TileEnergyCell cell = getTileEntity(worldIn, pos);
        if (cell != null) {
            double currentPower = cell.getAECurrentPower();
            double maxPower = cell.getAEMaxPower();
            if (maxPower <= 0) {
                return 0;
            }
            double fillFactor = currentPower / maxPower;
            return (int) Math.floor(fillFactor * 14.0D) + (currentPower > 0 ? 1 : 0);
        }
        return 0;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state,
                         int fortune) {
        ItemStack drop = new ItemStack(this);
        TileEnergyCell cell = getTileEntity(world, pos);
        if (cell != null) {
            double currentPower = cell.getAECurrentPower();
            if (currentPower > 0) {
                setStoredEnergyTag(drop, currentPower, cell.getAEMaxPower());
            }
            NBTTagCompound settings = cell.exportSettings(SettingsFrom.DISMANTLE_ITEM);
            if (!Platform.isNbtEmpty(settings)) {
                Platform.openNbtData(drop).merge(settings);
            }
        }
        drops.add(drop);
    }

    private void setStoredEnergyTag(ItemStack stack, double currentPower, double maxPower) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble(STORED_ENERGY, currentPower);
        NBTTagCompound blockEntityTag = new NBTTagCompound();
        blockEntityTag.setDouble("internalCurrentPower", currentPower);
        blockEntityTag.setDouble("internalMaxPower", maxPower);
        tag.setTag("BlockEntityTag", blockEntityTag);
        stack.setTagCompound(tag);
    }
}
