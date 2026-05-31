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

import ae2.api.orientation.IOrientationStrategy;
import ae2.api.orientation.OrientationStrategies;
import ae2.block.AEBaseTileBlock;
import ae2.container.GuiIds;
import ae2.core.gui.GuiOpener;
import ae2.tile.misc.TileVibrationChamber;
import ae2.util.InteractionUtil;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class VibrationChamberBlock extends AEBaseTileBlock<TileVibrationChamber> {
    public static final PropertyBool ACTIVE = PropertyBool.create("active");

    public VibrationChamberBlock() {
        super(Material.IRON);
        setHardness(4.2F);
        setResistance(11.0F);
        setTileEntity(TileVibrationChamber.class);
        setDefaultState(this.blockState.getBaseState().withProperty(ACTIVE, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return createBlockState(ACTIVE);
    }

    @Override
    protected IBlockState updateBlockStateFromTileEntity(IBlockState currentState, TileVibrationChamber tileEntity) {
        return currentState.withProperty(ACTIVE, tileEntity.isOn);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.full();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        if (!world.isRemote && !InteractionUtil.isInAlternateUseMode(player)) {
            TileVibrationChamber tile = this.getTileEntity(world, pos);
            if (tile != null) {
                GuiOpener.openGui(player, GuiIds.GuiKey.VIBRATION_CHAMBER, tile);
            }
        }

        return true;
    }

}
