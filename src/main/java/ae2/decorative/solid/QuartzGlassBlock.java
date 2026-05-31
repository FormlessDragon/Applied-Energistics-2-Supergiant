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

package ae2.decorative.solid;

import net.minecraft.block.BlockGlass;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class QuartzGlassBlock extends BlockGlass {
    public static final UnlistedGlassStateProperty GLASS_STATE = new UnlistedGlassStateProperty();

    public QuartzGlassBlock() {
        super(Material.GLASS, false);
        this.setHardness(0.3F);
        this.setLightOpacity(0);
    }

    private static GlassState getGlassState(IBlockAccess level, IBlockState state, BlockPos pos) {
        int[] masks = new int[6];
        for (EnumFacing facing : EnumFacing.values()) {
            masks[facing.getIndex()] = makeBitmask(level, state, pos, facing);
        }

        boolean[] adjacentGlassBlocks = new boolean[6];
        for (EnumFacing facing : EnumFacing.values()) {
            adjacentGlassBlocks[facing.getIndex()] = isGlassBlock(level, state, pos, facing, facing,
                facing.getOpposite());
        }

        return new GlassState(masks, adjacentGlassBlocks);
    }

    private static int makeBitmask(IBlockAccess level, IBlockState state, BlockPos pos, EnumFacing side) {
        return switch (side) {
            case DOWN -> makeBitmask(level, state, pos, side, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.NORTH,
                EnumFacing.WEST);
            case UP -> makeBitmask(level, state, pos, side, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH,
                EnumFacing.EAST);
            case NORTH -> makeBitmask(level, state, pos, side, EnumFacing.UP, EnumFacing.WEST, EnumFacing.DOWN,
                EnumFacing.EAST);
            case SOUTH -> makeBitmask(level, state, pos, side, EnumFacing.UP, EnumFacing.EAST, EnumFacing.DOWN,
                EnumFacing.WEST);
            case WEST -> makeBitmask(level, state, pos, side, EnumFacing.UP, EnumFacing.SOUTH, EnumFacing.DOWN,
                EnumFacing.NORTH);
            case EAST -> makeBitmask(level, state, pos, side, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.DOWN,
                EnumFacing.SOUTH);
        };
    }

    private static int makeBitmask(IBlockAccess level, IBlockState state, BlockPos pos, EnumFacing face,
                                   EnumFacing up, EnumFacing right, EnumFacing down, EnumFacing left) {
        int bitmask = 0;

        if (!isGlassBlock(level, state, pos, face, up, face)) {
            bitmask |= 1;
        }
        if (!isGlassBlock(level, state, pos, face, right, face)) {
            bitmask |= 2;
        }
        if (!isGlassBlock(level, state, pos, face, down, face)) {
            bitmask |= 4;
        }
        if (!isGlassBlock(level, state, pos, face, left, face)) {
            bitmask |= 8;
        }
        return bitmask;
    }

    private static boolean isGlassBlock(IBlockAccess level, IBlockState state, BlockPos pos, EnumFacing queryingFace,
                                        EnumFacing adjDir, EnumFacing adjFace) {
        BlockPos adjacentPos = pos.offset(adjDir);
        IBlockState adjacentState = level.getBlockState(adjacentPos);
        if (!(adjacentState.getBlock() instanceof QuartzGlassBlock)) {
            return false;
        }

        return state.getBlock() instanceof QuartzGlassBlock;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[0], new IUnlistedProperty<?>[]{GLASS_STATE});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess level, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        return ((IExtendedBlockState) state).withProperty(GLASS_STATE, getGlassState(level, state, pos));
    }

    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess level, BlockPos pos, EnumFacing side) {
        IBlockState adjacentBlockState = level.getBlockState(pos.offset(side));
        if (adjacentBlockState.getBlock() instanceof QuartzGlassBlock
            && adjacentBlockState.getRenderType() == state.getRenderType()) {
            return false;
        }

        return super.shouldSideBeRendered(state, level, pos, side);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
