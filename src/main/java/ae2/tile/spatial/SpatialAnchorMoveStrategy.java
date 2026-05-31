/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.tile.spatial;

import ae2.api.movable.DefaultBlockEntityMoveStrategy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class SpatialAnchorMoveStrategy extends DefaultBlockEntityMoveStrategy {
    @Override
    public boolean canHandle(Class<? extends TileEntity> type) {
        return TileSpatialAnchor.class.isAssignableFrom(type);
    }

    @Nullable
    @Override
    public NBTTagCompound beginMove(TileEntity tileEntity) {
        NBTTagCompound result = super.beginMove(tileEntity);
        if (result != null && tileEntity instanceof TileSpatialAnchor spatialAnchor) {
            spatialAnchor.releaseAll();
        }
        return result;
    }

    @Override
    public boolean completeMove(TileEntity blockEntity, IBlockState state, NBTTagCompound savedData, World newLevel,
                                BlockPos newPosition) {
        if (!super.completeMove(blockEntity, state, savedData, newLevel, newPosition)) {
            return false;
        }
        TileEntity movedTile = newLevel.getTileEntity(newPosition);
        if (movedTile instanceof TileSpatialAnchor spatialAnchor) {
            spatialAnchor.doneMoving();
        }
        return true;
    }
}
