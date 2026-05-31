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

package ae2.block.misc;

import ae2.api.implementations.blockentities.ICrankable;
import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.tile.misc.TileCrank;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

@SuppressWarnings("deprecation")
public class CrankBlock extends AEBaseTileBlock<TileCrank> {
    private static final AxisAlignedBB DOWN_AABB = createAabb(EnumFacing.DOWN);
    private static final AxisAlignedBB UP_AABB = createAabb(EnumFacing.UP);
    private static final AxisAlignedBB NORTH_AABB = createAabb(EnumFacing.NORTH);
    private static final AxisAlignedBB SOUTH_AABB = createAabb(EnumFacing.SOUTH);
    private static final AxisAlignedBB WEST_AABB = createAabb(EnumFacing.WEST);
    private static final AxisAlignedBB EAST_AABB = createAabb(EnumFacing.EAST);

    public CrankBlock() {
        super(Material.WOOD);
        this.setOpaque();
        this.setFullSize();
        this.setHardness(0.5F);
        this.setResistance(2.5F);
        this.setTileEntity(TileCrank.class);
        this.setDefaultState(this.blockState.getBaseState().withProperty(BlockDirectional.FACING, EnumFacing.UP));
    }

    private static AxisAlignedBB createAabb(EnumFacing facing) {
        double xOff = -0.15D * facing.getXOffset();
        double yOff = -0.15D * facing.getYOffset();
        double zOff = -0.15D * facing.getZOffset();
        return new AxisAlignedBB(xOff + 0.15D, yOff + 0.15D, zOff + 0.15D,
            xOff + 0.85D, yOff + 0.85D, zOff + 0.85D);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return this.createBlockState(new IProperty<?>[0]);
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
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (player instanceof FakePlayer) {
            this.dropCrank(world, pos, state);
            return true;
        }

        TileCrank tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.power();
            return true;
        }

        return super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (this.getAttachedToPos(state, pos).equals(fromPos) && this.getCrankable(state, world, pos) == null) {
            this.dropCrank(world, pos, state);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.values()) {
            if (this.canBlockStay(world, pos, this.getDefaultState().withProperty(BlockDirectional.FACING, facing))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return this.canBlockStay(world, pos, this.getDefaultState().withProperty(BlockDirectional.FACING, side));
    }

    public boolean canBlockStay(World world, BlockPos pos, IBlockState state) {
        return this.getCrankable(state, world, pos) != null;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return switch (state.getValue(BlockDirectional.FACING)) {
            case DOWN -> DOWN_AABB;
            case NORTH -> NORTH_AABB;
            case SOUTH -> SOUTH_AABB;
            case WEST -> WEST_AABB;
            case EAST -> EAST_AABB;
            default -> UP_AABB;
        };
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    public ICrankable getCrankable(IBlockState state, World world, BlockPos pos) {
        EnumFacing facing = this.getOrientationStrategy().getFacing(state);
        BlockPos attachedToPos = this.getAttachedToPos(state, pos);
        return ICrankable.get(world, attachedToPos, facing);
    }

    private BlockPos getAttachedToPos(IBlockState state, BlockPos pos) {
        EnumFacing attachedToSide = this.getOrientationStrategy().getFacing(state).getOpposite();
        return pos.offset(attachedToSide);
    }

    private void dropCrank(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            this.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }
}
