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

package ae2.items.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public record SpatialPlotInfo(int id, BlockPos size) {

    private static final String TAG_ID = "id";
    private static final String TAG_SIZE = "size";

    public SpatialPlotInfo(int id, BlockPos size) {
        this.id = id;
        this.size = size.toImmutable();
    }

    public static SpatialPlotInfo fromNBT(NBTTagCompound tag) {
        return new SpatialPlotInfo(tag.getInteger(TAG_ID), BlockPos.fromLong(tag.getLong(TAG_SIZE)));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_ID, this.id);
        tag.setLong(TAG_SIZE, this.size.toLong());
        return tag;
    }
}
