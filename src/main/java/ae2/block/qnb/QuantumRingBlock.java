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

import ae2.tile.qnb.TileQuantumBridge;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class QuantumRingBlock extends QuantumBaseBlock {

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(TWO_PIXELS, TWO_PIXELS, TWO_PIXELS,
        1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS);
    private static final AxisAlignedBB X_AXIS_SHAPE = new AxisAlignedBB(0.0, TWO_PIXELS, TWO_PIXELS,
        1.0, 1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS);
    private static final AxisAlignedBB Y_AXIS_SHAPE = new AxisAlignedBB(TWO_PIXELS, 0.0, TWO_PIXELS,
        1.0 - TWO_PIXELS, 1.0, 1.0 - TWO_PIXELS);
    private static final AxisAlignedBB Z_AXIS_SHAPE = new AxisAlignedBB(TWO_PIXELS, TWO_PIXELS, 0.0,
        1.0 - TWO_PIXELS, 1.0 - TWO_PIXELS, 1.0);

    public QuantumRingBlock() {
        super(Material.IRON);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        TileQuantumBridge bridge = this.getTileEntity(source, pos);
        if (bridge != null && bridge.isFormed() && !bridge.isCorner()) {
            return FULL_BLOCK_AABB;
        }

        return SHAPE;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World worldIn, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn, boolean isActualState) {
        TileQuantumBridge bridge = this.getTileEntity(worldIn, pos);
        if (bridge != null && bridge.isFormed() && !bridge.isCorner()) {
            addCollisionBoxToList(pos, entityBox, collidingBoxes, X_AXIS_SHAPE);
            addCollisionBoxToList(pos, entityBox, collidingBoxes, Y_AXIS_SHAPE);
            addCollisionBoxToList(pos, entityBox, collidingBoxes, Z_AXIS_SHAPE);
            return;
        }

        super.addCollisionBoxToList(state, worldIn, pos, entityBox, collidingBoxes, entityIn, isActualState);
    }
}

