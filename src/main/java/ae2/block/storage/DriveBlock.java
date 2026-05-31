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
import ae2.block.AEBaseTileBlock;
import ae2.client.render.model.DriveModelData;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.tile.storage.TileDrive;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
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
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

public class DriveBlock extends AEBaseTileBlock<TileDrive> {
    public static final IUnlistedProperty<DriveModelData> RENDER_STATE = new IUnlistedProperty<>() {
        @Override
        public String getName() {
            return "drive_render_state";
        }

        @Override
        public boolean isValid(DriveModelData value) {
            return true;
        }

        @Override
        public Class<DriveModelData> getType() {
            return DriveModelData.class;
        }

        @Override
        public String valueToString(DriveModelData value) {
            return String.valueOf(value);
        }
    };

    public DriveBlock() {
        super(Material.IRON);
        setHardness(2.2F);
        setResistance(11.0F);
        setTileEntity(TileDrive.class);
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this, getOrientationStrategy().getProperties().toArray(new IProperty<?>[0]),
            new IUnlistedProperty<?>[]{RENDER_STATE});
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }

        TileDrive tile = this.getTileEntity(world, pos);
        return ((IExtendedBlockState) state).withProperty(RENDER_STATE,
            tile == null ? DriveModelData.createEmpty(10) : DriveModelData.fromDrive(tile));
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, net.minecraft.block.state.IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileDrive tile = this.getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.DRIVE, tile);
            }
            return true;
        }
        return false;
    }

}
