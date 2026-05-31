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

package ae2.hooks;

import ae2.entity.TinyTNTPrimedEntity;
import net.minecraft.block.BlockDispenser;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public final class TinyTNTDispenseItemBehavior extends BehaviorDefaultDispenseItem {

    @Override
    protected ItemStack dispenseStack(IBlockSource dispenser, ItemStack dispensedItem) {
        final EnumFacing facing = dispenser.getBlockState().getValue(BlockDispenser.FACING);
        final World world = dispenser.getWorld();
        final int x = dispenser.getBlockPos().getX() + facing.getXOffset();
        final int y = dispenser.getBlockPos().getY() + facing.getYOffset();
        final int z = dispenser.getBlockPos().getZ() + facing.getZOffset();
        final TinyTNTPrimedEntity primedTinyTNTEntity = new TinyTNTPrimedEntity(world, x + 0.5F, y + 0.5F,
            z + 0.5F, null);
        world.spawnEntity(primedTinyTNTEntity);
        dispensedItem.setCount(dispensedItem.getCount() - 1);
        return dispensedItem;
    }
}
