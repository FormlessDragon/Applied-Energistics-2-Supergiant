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

package ae2.worldgen.meteorite.fallout;

import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;

public enum FalloutMode {
    NONE,
    DEFAULT,
    SAND,
    TERRACOTTA,
    ICE_SNOW;

    public static FalloutMode fromBiome(Biome biome) {
        if (biome == null) {
            return DEFAULT;
        }
        if (BiomeDictionary.hasType(biome, Type.SANDY) || BiomeDictionary.hasType(biome, Type.BEACH)) {
            return SAND;
        }
        if (BiomeDictionary.hasType(biome, Type.MESA)) {
            return TERRACOTTA;
        }
        if (BiomeDictionary.hasType(biome, Type.SNOWY) || BiomeDictionary.hasType(biome, Type.COLD)) {
            return ICE_SNOW;
        }
        return DEFAULT;
    }

    public static FalloutMode fromOrdinal(byte ordinal) {
        final FalloutMode[] values = values();
        final int index = Byte.toUnsignedInt(ordinal);
        if (index < values.length) {
            return values[index];
        }
        return DEFAULT;
    }

    public int adjustCrater() {
        return this == SAND || this == ICE_SNOW ? 2 : 0;
    }
}
