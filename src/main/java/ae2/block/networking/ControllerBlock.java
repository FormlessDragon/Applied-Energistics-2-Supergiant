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

import ae2.block.AEBaseTileBlock;
import ae2.core.definitions.AEBlocks;
import ae2.tile.networking.TileController;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

@SuppressWarnings("deprecation")
public class ControllerBlock extends AEBaseTileBlock<TileController> {

    public static final PropertyEnum<ControllerBlockState> CONTROLLER_STATE = PropertyEnum.create("state",
        ControllerBlockState.class);
    public static final PropertyEnum<ControllerRenderType> CONTROLLER_TYPE = PropertyEnum.create("type",
        ControllerRenderType.class);

    public ControllerBlock() {
        super(Material.IRON);
        this.setHardness(6.0F);
        this.setResistance(10.0F);
        this.setTileEntity(TileController.class);
        this.setDefaultState(this.blockState.getBaseState()
                                            .withProperty(CONTROLLER_STATE, ControllerBlockState.offline)
                                            .withProperty(CONTROLLER_TYPE, ControllerRenderType.block));
    }

    private static boolean isController(IBlockAccess level, int x, int y, int z) {
// Do NOT query tile entity:
        Block block = level.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block == AEBlocks.CONTROLLER.block();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(CONTROLLER_STATE, CONTROLLER_TYPE);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
// Only used for columns, really
        ControllerRenderType type = ControllerRenderType.block;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

// Detect whether controllers are on both sides of the x, y, and z axes
        final boolean xx = isController(world, x - 1, y, z) && isController(world, x + 1, y, z);
        final boolean yy = isController(world, x, y - 1, z) && isController(world, x, y + 1, z);
        final boolean zz = isController(world, x, y, z - 1) && isController(world, x, y, z + 1);

        if (xx && !yy && !zz) {
            type = ControllerRenderType.column_x;
        } else if (!xx && yy && !zz) {
            type = ControllerRenderType.column_y;
        } else if (!xx && !yy && zz) {
            type = ControllerRenderType.column_z;
        } else if ((xx ? 1 : 0) + (yy ? 1 : 0) + (zz ? 1 : 0) >= 2) {
            final int v = (Math.abs(x) + Math.abs(y) + Math.abs(z)) % 2;
            type = v == 0 ? ControllerRenderType.inside_a : ControllerRenderType.inside_b;
        }

        return state.withProperty(CONTROLLER_TYPE, type);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(CONTROLLER_STATE).ordinal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        ControllerBlockState[] values = ControllerBlockState.values();
        int safeMeta = meta < 0 || meta >= values.length ? 0 : meta;
        return this.getDefaultState().withProperty(CONTROLLER_STATE, values[safeMeta]);
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileController tileEntity) {
        ControllerBlockState nextState = ControllerBlockState.offline;
        if (tileEntity.isOnline()) {
            nextState = tileEntity.isConflicted() ? ControllerBlockState.conflicted : ControllerBlockState.online;
        }
        return currentState.withProperty(CONTROLLER_STATE, nextState);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(worldIn, pos, state, playerIn, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        if (!playerIn.getHeldItem(hand).isEmpty()) {
            return false;
        }

        TileController tile = this.getTileEntity(worldIn, pos);
        if (tile != null) {
            if (!worldIn.isRemote) {
                tile.openGui(playerIn);
            }
            return true;
        }
        return false;
    }

    public enum ControllerBlockState implements IStringSerializable {
        offline,
        online,
        conflicted;

        @Override
        public String getName() {
            return this.name();
        }
    }

    public enum ControllerRenderType implements IStringSerializable {
        block,
        column_x,
        column_y,
        column_z,
        inside_a,
        inside_b;

        @Override
        public String getName() {
            return this.name();
        }
    }
}
