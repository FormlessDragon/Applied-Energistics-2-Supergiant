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

package ae2.block.qnb;

import ae2.block.AEBaseTileBlock;
import ae2.tile.qnb.TileQuantumBridge;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public abstract class QuantumBaseBlock extends AEBaseTileBlock<TileQuantumBridge> {

    public static final PropertyBool FORMED = PropertyBool.create("formed");
    protected static final double TWO_PIXELS = 2.0 / 16.0;
    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(TWO_PIXELS, TWO_PIXELS, TWO_PIXELS,
        1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS);

    protected QuantumBaseBlock(Material material) {
        super(material);
        this.setOpaque();
        this.setFullSize();
        this.setHardness(2.2F);
        this.setResistance(11.0F);
        this.setTileEntity(TileQuantumBridge.class);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FORMED, Boolean.FALSE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, new IProperty<?>[]{FORMED},
            new IUnlistedProperty<?>[]{QnbFormedState.PROPERTY});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        TileQuantumBridge bridge = this.getTileEntity(world, pos);
        if (bridge == null || !bridge.isFormed()) {
            return state;
        }

        return ((IExtendedBlockState) state).withProperty(QnbFormedState.PROPERTY,
            new QnbFormedState(bridge.getAdjacentQuantumBridges(), bridge.isCorner(), bridge.isPowered()));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FORMED) ? 1 : 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(FORMED, meta != 0);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileQuantumBridge tileEntity) {
        return currentState.withProperty(FORMED, tileEntity.isFormed());
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
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

        TileQuantumBridge bridge = this.getTileEntity(world, pos);
        if (bridge != null) {
            bridge.neighborUpdate(fromPos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileQuantumBridge bridge = this.getTileEntity(world, pos);
        if (bridge != null) {
            bridge.breakClusterOnRemove();
        }

        super.breakBlock(world, pos, state);
    }
}



