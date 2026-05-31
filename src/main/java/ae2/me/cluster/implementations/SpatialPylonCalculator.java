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

import ae2.me.cluster.IAEMultiBlock;
import ae2.me.cluster.MBCalculator;
import ae2.tile.spatial.TileSpatialPylon;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SpatialPylonCalculator extends MBCalculator<TileSpatialPylon, SpatialPylonCluster> {

    public SpatialPylonCalculator(TileSpatialPylon target) {
        super(target);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        return min.getX() == max.getX() && min.getY() == max.getY() && min.getZ() != max.getZ()
            || min.getX() == max.getX() && min.getY() != max.getY() && min.getZ() == max.getZ()
            || min.getX() != max.getX() && min.getY() == max.getY() && min.getZ() == max.getZ();
    }

    @Override
    public SpatialPylonCluster createCluster(World level, BlockPos min, BlockPos max) {
        return new SpatialPylonCluster(level, min, max);
    }

    @Override
    public boolean verifyInternalStructure(World level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
            var multiBlock = (IAEMultiBlock<?>) level.getTileEntity(pos);
            if (multiBlock == null || !multiBlock.isValid()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void updateBlockEntities(SpatialPylonCluster cluster, World level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
            var pylon = (TileSpatialPylon) level.getTileEntity(pos);
            if (pylon != null) {
                pylon.updateStatus(cluster);
                cluster.getLine().add(pylon);
            }
        }
    }

    @Override
    public boolean isValidBlockEntity(TileEntity te) {
        return te instanceof TileSpatialPylon;
    }
}
