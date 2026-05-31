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

package ae2.parts;

import ae2.api.parts.IPartCollisionHelper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BusCollisionHelper implements IPartCollisionHelper {

    private final List<AxisAlignedBB> boxes;
    private final EnumFacing x;
    private final EnumFacing y;
    private final EnumFacing z;
    private final boolean visual;

    public BusCollisionHelper(List<AxisAlignedBB> boxes, @Nullable EnumFacing side, boolean visual) {
        this.boxes = boxes;
        this.visual = visual;

        if (side == null) {
            this.x = EnumFacing.EAST;
            this.y = EnumFacing.UP;
            this.z = EnumFacing.SOUTH;
            return;
        }

        var axes = switch (side) {
            case DOWN -> new EnumFacing[]{EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.DOWN};
            case UP -> new EnumFacing[]{EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.UP};
            case EAST -> new EnumFacing[]{EnumFacing.SOUTH, EnumFacing.UP, EnumFacing.EAST};
            case WEST -> new EnumFacing[]{EnumFacing.NORTH, EnumFacing.UP, EnumFacing.WEST};
            case NORTH -> new EnumFacing[]{EnumFacing.WEST, EnumFacing.UP, EnumFacing.NORTH};
            case SOUTH -> new EnumFacing[]{EnumFacing.EAST, EnumFacing.UP, EnumFacing.SOUTH};
        };
        this.x = axes[0];
        this.y = axes[1];
        this.z = axes[2];
    }

    @Override
    public void addBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        minX /= 16.0;
        minY /= 16.0;
        minZ /= 16.0;
        maxX /= 16.0;
        maxY /= 16.0;
        maxZ /= 16.0;

        double aX = minX * this.x.getXOffset() + minY * this.y.getXOffset() + minZ * this.z.getXOffset();
        double aY = minX * this.x.getYOffset() + minY * this.y.getYOffset() + minZ * this.z.getYOffset();
        double aZ = minX * this.x.getZOffset() + minY * this.y.getZOffset() + minZ * this.z.getZOffset();

        double bX = maxX * this.x.getXOffset() + maxY * this.y.getXOffset() + maxZ * this.z.getXOffset();
        double bY = maxX * this.x.getYOffset() + maxY * this.y.getYOffset() + maxZ * this.z.getYOffset();
        double bZ = maxX * this.x.getZOffset() + maxY * this.y.getZOffset() + maxZ * this.z.getZOffset();

        if (this.x.getXOffset() + this.y.getXOffset() + this.z.getXOffset() < 0) {
            aX += 1;
            bX += 1;
        }

        if (this.x.getYOffset() + this.y.getYOffset() + this.z.getYOffset() < 0) {
            aY += 1;
            bY += 1;
        }

        if (this.x.getZOffset() + this.y.getZOffset() + this.z.getZOffset() < 0) {
            aZ += 1;
            bZ += 1;
        }

        this.boxes.add(new AxisAlignedBB(
            Math.min(aX, bX),
            Math.min(aY, bY),
            Math.min(aZ, bZ),
            Math.max(aX, bX),
            Math.max(aY, bY),
            Math.max(aZ, bZ)));
    }

    @Override
    public EnumFacing getWorldX() {
        return this.x;
    }

    @Override
    public EnumFacing getWorldY() {
        return this.y;
    }

    @Override
    public EnumFacing getWorldZ() {
        return this.z;
    }

    @Override
    public boolean isBBCollision() {
        return !this.visual;
    }
}
