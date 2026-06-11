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

import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.BlockDefinition;
import ae2.me.cluster.IAEMultiBlock;
import ae2.me.cluster.MBCalculator;
import ae2.tile.qnb.TileQuantumBridge;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class QuantumCalculator extends MBCalculator<TileQuantumBridge, QuantumCluster> {

    public QuantumCalculator(TileQuantumBridge t) {
        super(t);
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        if ((max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1)
            * (max.getZ() - min.getZ() + 1) == 9) {
            final int ones = (max.getX() - min.getX() == 0 ? 1 : 0)
                + (max.getY() - min.getY() == 0 ? 1 : 0)
                + (max.getZ() - min.getZ() == 0 ? 1 : 0);

            final int threes = (max.getX() - min.getX() == 2 ? 1 : 0)
                + (max.getY() - min.getY() == 2 ? 1 : 0)
                + (max.getZ() - min.getZ() == 2 ? 1 : 0);

            return ones == 1 && threes == 2;
        }

        return false;
    }

    @Override
    public QuantumCluster createCluster(World level, BlockPos min, BlockPos max) {
        return new QuantumCluster(min, max);
    }

    @Override
    public boolean verifyInternalStructure(World level, BlockPos min, BlockPos max) {
        byte num = 0;

        for (BlockPos p : BlockPos.getAllInBox(min, max)) {
            final IAEMultiBlock<?> te = (IAEMultiBlock<?>) level.getTileEntity(p);

            if (te == null || !te.isValid()) {
                return false;
            }

            num++;
            if (num == 5) {
                if (!this.isBlockAtLocation(level, p, AEBlocks.QUANTUM_LINK)) {
                    return false;
                }
            } else if (!this.isBlockAtLocation(level, p, AEBlocks.QUANTUM_RING)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void updateBlockEntities(QuantumCluster c, World level, BlockPos min, BlockPos max) {
        byte num = 0;
        byte ringNum = 0;

        for (BlockPos p : BlockPos.getAllInBox(min, max)) {
            if (!(level.getTileEntity(p) instanceof TileQuantumBridge te)) {
                continue;
            }

            num++;
            final byte flags;
            if (num == 5) {
                flags = num;
                c.setCenter(te);
            } else {
                if (num == 1 || num == 3 || num == 7 || num == 9) {
                    flags = (byte) (this.target.getCorner() | num);
                } else {
                    flags = num;
                }

                c.getRing()[ringNum] = te;
                ringNum++;
            }

            te.updateStatus(c, flags, true);
        }
    }

    @Override
    public boolean isValidBlockEntity(TileEntity te) {
        return te instanceof TileQuantumBridge;
    }

    private boolean isBlockAtLocation(IBlockAccess level, BlockPos pos, BlockDefinition<?> def) {
        return def.block() == level.getBlockState(pos).getBlock();
    }
}

