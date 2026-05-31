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
package ae2.block.storage;

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.api.orientation.RelativeSide;
import ae2.api.storage.cells.CellState;
import ae2.block.AEBaseTileBlock;
import ae2.core.localization.PlayerMessages;
import ae2.tile.storage.TileMEChest;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.Arrays;

public class MEChestBlock extends AEBaseTileBlock<TileMEChest> {
    public static final PropertyBool LIGHTS_ON = PropertyBool.create("lights_on");

    public MEChestBlock() {
        super(Material.IRON);
        setHardness(2.2F);
        setResistance(11.0F);
        setTileEntity(TileMEChest.class);
        setOpaque();
        setFullSize();
        setDefaultState(this.blockState.getBaseState().withProperty(LIGHTS_ON, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        var properties = getOrientationStrategy().getProperties().toArray(new IProperty<?>[0]);
        IProperty<?>[] listedProperties = Arrays.copyOf(properties, properties.length + 1);
        listedProperties[properties.length] = LIGHTS_ON;
        return new ExtendedBlockState(this, listedProperties, new IUnlistedProperty<?>[]{FORWARD, UP});
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return super.getMetaFromState(state);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return super.getStateFromMeta(meta).withProperty(LIGHTS_ON, false);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileMEChest tile = this.getTileEntity(world, pos);
        if (tile == null) {
            return super.getActualState(state, world, pos).withProperty(LIGHTS_ON, false);
        }

        CellState cellState = tile.getCellCount() >= 1 ? tile.getCellStatus(0) : CellState.ABSENT;
        return super.getActualState(state, world, pos)
                    .withProperty(LIGHTS_ON, tile.isPowered() && cellState != CellState.ABSENT);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileMEChest tileEntity) {
        CellState cellState = tileEntity.getCellCount() >= 1 ? tileEntity.getCellStatus(0) : CellState.ABSENT;
        return currentState.withProperty(LIGHTS_ON, tileEntity.isPowered() && cellState != CellState.ABSENT);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileMEChest tile = this.getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                if (facing == tile.getOrientation().getSide(RelativeSide.TOP)) {
                    if (!tile.openGui(player)) {
                        player.sendStatusMessage(PlayerMessages.ChestCannotReadStorageCell.text(), true);
                    }
                } else {
                    tile.openCellInventoryGui(player);
                }
            }
            return true;
        }
        return false;
    }
}
