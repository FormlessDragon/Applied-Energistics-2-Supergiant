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

package ae2.client.render.model;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

final class RenderHelper {
    private static final EnumMap<EnumFacing, List<Vec3d>> CORNERS_FOR_FACING = generateCornersForFacings();

    private RenderHelper() {
    }

    static List<Vec3d> getFaceCorners(EnumFacing side) {
        return CORNERS_FOR_FACING.get(side);
    }

    private static EnumMap<EnumFacing, List<Vec3d>> generateCornersForFacings() {
        EnumMap<EnumFacing, List<Vec3d>> result = new EnumMap<>(EnumFacing.class);

        for (EnumFacing facing : EnumFacing.values()) {
            List<Vec3d> corners;

            float offset = facing.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE ? 0 : 1;

            corners = switch (facing.getAxis()) {
                case X -> cornersOf(new Vec3d(offset, 1, 1), new Vec3d(offset, 0, 1),
                    new Vec3d(offset, 0, 0), new Vec3d(offset, 1, 0));
                case Y -> cornersOf(new Vec3d(1, offset, 1), new Vec3d(1, offset, 0),
                    new Vec3d(0, offset, 0), new Vec3d(0, offset, 1));
                default -> cornersOf(new Vec3d(0, 1, offset), new Vec3d(0, 0, offset),
                    new Vec3d(1, 0, offset), new Vec3d(1, 1, offset));
            };

            if (facing.getAxisDirection() == EnumFacing.AxisDirection.NEGATIVE) {
                Collections.reverse(corners);
            }

            result.put(facing, ImmutableList.copyOf(corners));
        }

        return result;
    }

    private static List<Vec3d> cornersOf(Vec3d first, Vec3d second, Vec3d third, Vec3d fourth) {
        List<Vec3d> corners = new ObjectArrayList<>(4);
        corners.add(first);
        corners.add(second);
        corners.add(third);
        corners.add(fourth);
        return corners;
    }
}
