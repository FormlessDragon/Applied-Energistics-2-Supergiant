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

package ae2.api.util;

import net.minecraft.util.EnumFacing;

public enum AEPartLocation {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
    INTERNAL(0, 0, 0);

    public static final AEPartLocation[] SIDE_LOCATIONS = {DOWN, UP, NORTH, SOUTH, WEST, EAST};

    private static final EnumFacing[] FACINGS = {
        EnumFacing.DOWN,
        EnumFacing.UP,
        EnumFacing.NORTH,
        EnumFacing.SOUTH,
        EnumFacing.WEST,
        EnumFacing.EAST,
        null
    };

    private static final int[] OPPOSITES = {1, 0, 3, 2, 5, 4, 6};

    public final int xOffset;
    public final int yOffset;
    public final int zOffset;

    AEPartLocation(int xOffset, int yOffset, int zOffset) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
    }

    public static AEPartLocation fromOrdinal(int id) {
        if (id >= 0 && id < SIDE_LOCATIONS.length) {
            return SIDE_LOCATIONS[id];
        }

        return INTERNAL;
    }

    public static AEPartLocation fromFacing(EnumFacing side) {
        if (side == null) {
            return INTERNAL;
        }

        return values()[side.ordinal()];
    }

    public AEPartLocation getOpposite() {
        return fromOrdinal(OPPOSITES[this.ordinal()]);
    }

    public EnumFacing getFacing() {
        return FACINGS[this.ordinal()];
    }
}
