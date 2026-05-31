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

package ae2.me.cluster.implementations;

import ae2.api.networking.IGrid;
import ae2.api.networking.events.GridCraftingCpuChange;
import ae2.me.cluster.MBCalculator;
import ae2.tile.crafting.TileCraftingUnit;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Iterator;

public class CraftingCPUCalculator extends MBCalculator<TileCraftingUnit, CraftingCPUCluster> {

    public CraftingCPUCalculator(TileCraftingUnit t) {
        super(t);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        if (max.getX() - min.getX() > 16) {
            return false;
        }

        if (max.getY() - min.getY() > 16) {
            return false;
        }

        return max.getZ() - min.getZ() <= 16;
    }

    @Override
    public CraftingCPUCluster createCluster(World world, BlockPos min, BlockPos max) {
        return new CraftingCPUCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(World world, BlockPos min, BlockPos max) {
        boolean storage = false;

        for (BlockPos blockPos : BlockPos.getAllInBox(min, max)) {
            if (!(world.getTileEntity(blockPos) instanceof TileCraftingUnit craftingTile)) {
                return false;
            }

            storage |= craftingTile.getStorageBytes() > 0;
        }

        return storage;
    }

    @Override
    public void updateBlockEntities(CraftingCPUCluster c, World world, BlockPos min, BlockPos max) {
        for (BlockPos blockPos : BlockPos.getAllInBox(min, max)) {
            final TileCraftingUnit tile = (TileCraftingUnit) world.getTileEntity(blockPos);
            tile.updateStatus(c);
            c.addTileEntity(tile);
        }

        c.done();

        final Iterator<TileCraftingUnit> i = c.getBlockEntities();
        while (i.hasNext()) {
            var tile = i.next();
            var node = tile.getGridNode();
            if (node != null) {
                final IGrid grid = node.grid();
                grid.postEvent(new GridCraftingCpuChange(node));
                return;
            }
        }
    }

    @Override
    public boolean isValidBlockEntity(TileEntity te) {
        return te instanceof TileCraftingUnit;
    }
}
