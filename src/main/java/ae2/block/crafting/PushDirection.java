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
package ae2.block.crafting;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;

/**
 * Extends the set of block facings with an all-direction state.
 */
public enum PushDirection implements IStringSerializable {
    ALL(null),
    NORTH(EnumFacing.NORTH),
    SOUTH(EnumFacing.SOUTH),
    EAST(EnumFacing.EAST),
    WEST(EnumFacing.WEST),
    UP(EnumFacing.UP),
    DOWN(EnumFacing.DOWN);

    private final EnumFacing direction;

    PushDirection(EnumFacing direction) {
        this.direction = direction;
    }

    public static PushDirection fromDirection(EnumFacing direction) {
        if (direction == null) {
            return ALL;
        }
        return valueOf(direction.name());
    }

    public EnumFacing getDirection() {
        return this.direction;
    }

    @Override
    public String getName() {
        return name().toLowerCase();
    }
}

