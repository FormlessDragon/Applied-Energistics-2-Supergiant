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

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.api.orientation.RelativeSide;
import ae2.block.AEBaseTileBlock;
import ae2.tile.networking.TileCrystalResonanceGenerator;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

public class CrystalResonanceGeneratorBlock extends AEBaseTileBlock<TileCrystalResonanceGenerator> {
    private static final AxisAlignedBB DOWN_AABB = createAabb(EnumFacing.DOWN);
    private static final AxisAlignedBB UP_AABB = createAabb(EnumFacing.UP);
    private static final AxisAlignedBB NORTH_AABB = createAabb(EnumFacing.NORTH);
    private static final AxisAlignedBB SOUTH_AABB = createAabb(EnumFacing.SOUTH);
    private static final AxisAlignedBB WEST_AABB = createAabb(EnumFacing.WEST);
    private static final AxisAlignedBB EAST_AABB = createAabb(EnumFacing.EAST);

    public CrystalResonanceGeneratorBlock() {
        super(Material.GLASS);
        this.setOpaque();
        this.setFullSize();
        this.setHardness(0.5F);
        this.setResistance(3.0F);
        this.setTileEntity(TileCrystalResonanceGenerator.class);
        this.setDefaultState(this.blockState.getBaseState().withProperty(BlockDirectional.FACING, EnumFacing.UP));
    }

    private static AxisAlignedBB createAabb(EnumFacing facing) {
        double minX = 0.0D;
        double minY = 0.0D;
        double minZ = 0.0D;
        double maxX = 1.0D;
        double maxY = 1.0D;
        double maxZ = 1.0D;

        switch (facing) {
            case DOWN -> {
                minX = minZ = 2.0D / 16.0D;
                maxX = maxZ = 14.0D / 16.0D;
                minY = 1.0D / 16.0D;
            }
            case EAST -> {
                minY = minZ = 2.0D / 16.0D;
                maxY = maxZ = 14.0D / 16.0D;
                maxX = 15.0D / 16.0D;
            }
            case NORTH -> {
                minX = minY = 2.0D / 16.0D;
                maxX = maxY = 14.0D / 16.0D;
                minZ = 1.0D / 16.0D;
            }
            case SOUTH -> {
                minX = minY = 2.0D / 16.0D;
                maxX = maxY = 14.0D / 16.0D;
                maxZ = 15.0D / 16.0D;
            }
            case UP -> {
                minX = minZ = 2.0D / 16.0D;
                maxX = maxZ = 14.0D / 16.0D;
                maxY = 15.0D / 16.0D;
            }
            case WEST -> {
                minY = minZ = 2.0D / 16.0D;
                maxY = maxZ = 14.0D / 16.0D;
                minX = 1.0D / 16.0D;
            }
            default -> {
            }
        }

        return new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(BlockDirectional.FACING).getIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        int facingIndex = meta & 7;
        if (facingIndex > 5) {
            facingIndex = 0;
        }
        return this.getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.byIndex(facingIndex));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return switch (this.getOrientation(state).getSide(RelativeSide.FRONT)) {
            case DOWN -> DOWN_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case EAST -> EAST_AABB;
            case UP -> UP_AABB;
        };
    }
}

