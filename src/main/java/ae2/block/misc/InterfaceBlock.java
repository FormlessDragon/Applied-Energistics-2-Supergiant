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

import ae2.api.networking.extensions.GridLogicExtensions;
import ae2.block.AEBaseTileBlock;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.tile.misc.TileInterface;
import net.minecraft.block.material.Material;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class InterfaceBlock extends AEBaseTileBlock<TileInterface> {

    public InterfaceBlock() {
        super(Material.IRON);
        setHardness(2.2F);
        setResistance(11.0F);
        setTileEntity(TileInterface.class);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileInterface tile = this.getTileEntity(world, pos);
        if (tile != null) {
            if (!world.isRemote) {
                tile.openGui(player, GuiHostLocators.forTile(tile));
            }
            return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        super.neighborChanged(state, world, pos, blockIn, fromPos);
        if (world.isRemote) {
            return;
        }

        var side = GridLogicExtensions.getNeighborSide(pos, fromPos);
        var tile = this.getTileEntity(world, pos);
        if (side != null && tile != null) {
            tile.getInterfaceLogic().onNeighborChanged(side);
        }
    }
}


