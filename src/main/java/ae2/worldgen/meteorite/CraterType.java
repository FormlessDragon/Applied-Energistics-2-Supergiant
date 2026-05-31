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

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

public enum CraterType {
    NONE(null),
    NORMAL(Blocks.AIR),
    LAVA(Blocks.LAVA),
    OBSIDIAN(Blocks.OBSIDIAN),
    WATER(Blocks.WATER),
    SNOW(Blocks.SNOW),
    ICE(Blocks.ICE);

    private final Block filler;

    CraterType(Block filler) {
        this.filler = filler;
    }

    public static CraterType fromOrdinal(byte ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return NORMAL;
        }
        return values()[ordinal];
    }

    public Block getFiller() {
        return filler;
    }
}
