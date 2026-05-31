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

package ae2.block.qnb;

import ae2.client.EffectType;
import ae2.container.GuiIds;
import ae2.core.AppEng;
import ae2.core.gui.GuiOpener;
import ae2.tile.qnb.TileQuantumBridge;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

public class QuantumLinkChamberBlock extends QuantumBaseBlock {

    public QuantumLinkChamberBlock() {
        super(Material.GLASS);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        TileQuantumBridge bridge = this.getTileEntity(world, pos);
        if (bridge != null && bridge.hasQES() && AppEng.instance().getClientWorld() != null) {
            AppEng.instance().spawnEffect(EffectType.Energy, world, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, null);
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ)) {
            return true;
        }

        TileQuantumBridge bridge = this.getTileEntity(world, pos);
        if (bridge != null) {
            if (!world.isRemote) {
                GuiOpener.openGui(player, GuiIds.GuiKey.QNB, bridge);
            }
            return true;
        }

        return false;
    }
}
