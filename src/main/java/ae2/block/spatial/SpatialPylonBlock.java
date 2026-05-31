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

package ae2.block.spatial;

import ae2.block.AEBaseTileBlock;
import ae2.tile.spatial.TileSpatialPylon;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public class SpatialPylonBlock extends AEBaseTileBlock<TileSpatialPylon> {
    public static final PropertyBool POWERED_ON = PropertyBool.create("powered_on");
    public static final IUnlistedProperty<TileSpatialPylon.ClientState> RENDER_STATE = new IUnlistedProperty<>() {
        @Override
        public String getName() {
            return "render_state";
        }

        @Override
        public boolean isValid(TileSpatialPylon.ClientState value) {
            return true;
        }

        @Override
        public Class<TileSpatialPylon.ClientState> getType() {
            return TileSpatialPylon.ClientState.class;
        }

        @Override
        public String valueToString(TileSpatialPylon.ClientState value) {
            return String.valueOf(value);
        }
    };

    public SpatialPylonBlock() {
        super(Material.GLASS);
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(TileSpatialPylon.class);
        this.setDefaultState(this.blockState.getBaseState().withProperty(POWERED_ON, Boolean.FALSE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{POWERED_ON}, new IUnlistedProperty<?>[]{RENDER_STATE});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        TileSpatialPylon tile = getTileEntity(world, pos);
        if (tile == null) {
            return state;
        }

        return ((IExtendedBlockState) state).withProperty(RENDER_STATE, tile.getRenderState());
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(POWERED_ON) ? 1 : 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(POWERED_ON, meta != 0);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileSpatialPylon tileEntity) {
        return currentState.withProperty(POWERED_ON, tileEntity.isPoweredOn());
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(POWERED_ON) ? 8 : 0;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        TileSpatialPylon tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.neighborChanged(fromPos);
        }
    }
}
