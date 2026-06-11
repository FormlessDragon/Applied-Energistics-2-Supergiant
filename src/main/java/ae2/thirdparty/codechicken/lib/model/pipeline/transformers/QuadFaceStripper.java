/*
 * This file is part of CodeChickenLib.
 * Copyright (c) 2018, covers1624, All rights reserved.
 *
 * CodeChickenLib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * CodeChickenLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with CodeChickenLib. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.thirdparty.codechicken.lib.model.pipeline.transformers;

import ae2.client.render.mesh.MutableQuadView;
import ae2.client.render.mesh.RenderContext;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;

import static net.minecraft.util.EnumFacing.AxisDirection.POSITIVE;

public class QuadFaceStripper implements RenderContext.QuadTransform {

    private AxisAlignedBB bounds;
    private int mask;

    QuadFaceStripper() {
        super();
    }

    public QuadFaceStripper(AxisAlignedBB bounds, int mask) {
        this.bounds = bounds;
        this.mask = mask;
    }

    public void setBounds(AxisAlignedBB bounds) {
        this.bounds = bounds;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        if (this.mask == 0 || this.bounds == null) {
            return true;
        }

        EnumFacing face = quad.nominalFace();
        if (face == null) {
            return true;
        }
        if ((this.mask & 1 << face.ordinal()) != 0) {
            EnumFacing.AxisDirection dir = face.getAxisDirection();
            switch (face.getAxis()) {
                case X -> {
                    float bound = (float) (dir == POSITIVE ? this.bounds.maxX : this.bounds.minX);
                    float x1 = quad.posByIndex(0, 0);
                    float x2 = quad.posByIndex(1, 0);
                    float x3 = quad.posByIndex(2, 0);
                    float x4 = quad.posByIndex(3, 0);
                    return x1 != x2 || x2 != x3 || x3 != x4 || x4 != bound;
                }
                case Y -> {
                    float bound = (float) (dir == POSITIVE ? this.bounds.maxY : this.bounds.minY);
                    float y1 = quad.posByIndex(0, 1);
                    float y2 = quad.posByIndex(1, 1);
                    float y3 = quad.posByIndex(2, 1);
                    float y4 = quad.posByIndex(3, 1);
                    return y1 != y2 || y2 != y3 || y3 != y4 || y4 != bound;
                }
                case Z -> {
                    float bound = (float) (dir == POSITIVE ? this.bounds.maxZ : this.bounds.minZ);
                    float z1 = quad.posByIndex(0, 2);
                    float z2 = quad.posByIndex(1, 2);
                    float z3 = quad.posByIndex(2, 2);
                    float z4 = quad.posByIndex(3, 2);
                    return z1 != z2 || z2 != z3 || z3 != z4 || z4 != bound;
                }
                default -> {
                }
            }
        }
        return true;
    }
}
