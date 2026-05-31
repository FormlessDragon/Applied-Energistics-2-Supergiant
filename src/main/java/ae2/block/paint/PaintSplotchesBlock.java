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

package ae2.block.paint;

import ae2.block.AEBaseTileBlock;
import ae2.core.DebugCreativeTab;
import ae2.helpers.Splotch;
import ae2.tile.misc.TilePaint;
import ae2.util.Platform;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Random;

public class PaintSplotchesBlock extends AEBaseTileBlock<TilePaint> {

    static final PaintSplotchesProperty SPLOTCHES = new PaintSplotchesProperty();
    private static final AxisAlignedBB NULL_AABB = new AxisAlignedBB(0, 0, 0, 0, 0, 0);

    public PaintSplotchesBlock() {
        super(new MaterialLiquid(MapColor.AIR));
        this.setOpaque();
        this.setFullSize();
        this.setTileEntity(TilePaint.class);
        this.setLightOpacity(0);
        this.setHardness(0.0F);
        this.setResistance(0.0F);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{SPLOTCHES});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        TilePaint tile = this.getTileEntity(world, pos);
        var splotches = tile != null ? tile.getDots() : Collections.<Splotch>emptyList();
        return ((IExtendedBlockState) state).withProperty(SPLOTCHES, new PaintSplotches(splotches));
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isReplaceable(IBlockAccess worldIn, BlockPos pos) {
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, net.minecraft.block.Block blockIn,
                                BlockPos fromPos) {
        TilePaint tile = this.getTileEntity(worldIn, pos);
        if (tile != null) {
            tile.neighborChanged();
        }
    }

    @Override
    public void getSubBlocks(CreativeTabs itemIn, NonNullList<ItemStack> items) {
        if (itemIn == DebugCreativeTab.INSTANCE) {
            items.add(new ItemStack(this));
        }
    }

    @Override
    public @Nullable Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return null;
    }

    @Override
    public void dropBlockAsItemWithChance(World worldIn, BlockPos pos, IBlockState state, float chance, int fortune) {
    }

    @Override
    public void fillWithRain(World worldIn, BlockPos pos) {
        if (Platform.isServer()) {
            worldIn.setBlockToAir(pos);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World worldIn, BlockPos pos) {
        return true;
    }

    /**
     * Lumen paint splotches contribute light-level 12, two or more have light-level 15. We model this with 0 = 0, 1 =
     * 12, 2 = 15.
     */
    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TilePaint tile = this.getTileEntity(world, pos);
        return tile != null ? tile.getLightLevel() : 0;
    }

    @Override
    public boolean isAir(IBlockState state, IBlockAccess world, BlockPos pos) {
        return true;
    }
}
