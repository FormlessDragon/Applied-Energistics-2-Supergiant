/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2020, AlgorithmX2, All rights reserved.
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

package ae2.worldgen.meteorite;

import ae2.worldgen.meteorite.fallout.FalloutMode;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

public record PlacedMeteoriteSettings(BlockPos pos, float meteoriteRadius, CraterType craterType, FalloutMode fallout,
                                      boolean pureCrater, boolean craterLake) {

    public static PlacedMeteoriteSettings read(NBTTagCompound tag) {
        BlockPos pos = BlockPos.fromLong(tag.getLong(Constants.TAG_POS));
        float meteoriteRadius = tag.getFloat(Constants.TAG_RADIUS);
        CraterType craterType = CraterType.fromOrdinal(tag.getByte(Constants.TAG_CRATER));
        FalloutMode fallout = FalloutMode.fromOrdinal(tag.getByte(Constants.TAG_FALLOUT));
        boolean pureCrater = tag.getBoolean(Constants.TAG_PURE);
        boolean craterLake = tag.getBoolean(Constants.TAG_LAKE);
        return new PlacedMeteoriteSettings(pos, meteoriteRadius, craterType, fallout, pureCrater, craterLake);
    }

    public boolean shouldPlaceCrater() {
        return this.craterType != CraterType.NONE;
    }

    public NBTTagCompound write(NBTTagCompound tag) {
        tag.setLong(Constants.TAG_POS, this.pos.toLong());
        tag.setFloat(Constants.TAG_RADIUS, this.meteoriteRadius);
        tag.setByte(Constants.TAG_CRATER, (byte) this.craterType.ordinal());
        tag.setByte(Constants.TAG_FALLOUT, (byte) this.fallout.ordinal());
        tag.setBoolean(Constants.TAG_PURE, this.pureCrater);
        tag.setBoolean(Constants.TAG_LAKE, this.craterLake);
        return tag;
    }
}
