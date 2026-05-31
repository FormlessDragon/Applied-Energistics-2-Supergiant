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

package ae2.spatial;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import java.time.Instant;

public record TransitionInfo(String worldId, int dimensionId, BlockPos min, BlockPos max, Instant timestamp) {

    public static final String TAG_WORLD_ID = "world_id";
    public static final String TAG_DIMENSION_ID = "dimension_id";
    public static final String TAG_MIN = "min";
    public static final String TAG_MAX = "max";
    public static final String TAG_TIMESTAMP = "timestamp";

    public TransitionInfo(String worldId, int dimensionId, BlockPos min, BlockPos max, Instant timestamp) {
        this.worldId = worldId;
        this.dimensionId = dimensionId;
        this.min = min.toImmutable();
        this.max = max.toImmutable();
        this.timestamp = timestamp;
    }

    public static TransitionInfo fromTag(NBTTagCompound tag) {
        BlockPos min = readPos(tag, TAG_MIN, "min_x", "min_y", "min_z");
        BlockPos max = readPos(tag, TAG_MAX, "max_x", "max_y", "max_z");
        return new TransitionInfo(
            tag.getString(TAG_WORLD_ID),
            tag.getInteger(TAG_DIMENSION_ID),
            min,
            max,
            Instant.ofEpochMilli(tag.getLong(TAG_TIMESTAMP)));
    }

    private static NBTTagCompound writePos(BlockPos pos) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", pos.getX());
        tag.setInteger("y", pos.getY());
        tag.setInteger("z", pos.getZ());
        return tag;
    }

    private static BlockPos readPos(NBTTagCompound tag, String key, String legacyX, String legacyY, String legacyZ) {
        if (tag.hasKey(key, 10)) {
            NBTTagCompound posTag = tag.getCompoundTag(key);
            return new BlockPos(posTag.getInteger("x"), posTag.getInteger("y"), posTag.getInteger("z"));
        }
        return new BlockPos(tag.getInteger(legacyX), tag.getInteger(legacyY), tag.getInteger(legacyZ));
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_WORLD_ID, this.worldId);
        tag.setInteger(TAG_DIMENSION_ID, this.dimensionId);
        tag.setTag(TAG_MIN, writePos(this.min));
        tag.setTag(TAG_MAX, writePos(this.max));
        tag.setLong(TAG_TIMESTAMP, this.timestamp.toEpochMilli());
        return tag;
    }
}
