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
import net.minecraft.util.EnumFacing.AxisDirection;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3i;

import static net.minecraft.util.EnumFacing.AxisDirection.NEGATIVE;
import static net.minecraft.util.EnumFacing.AxisDirection.POSITIVE;

public class QuadCornerKicker implements RenderContext.QuadTransform {

    public static final QuadCornerKicker INSTANCE = new QuadCornerKicker();
    public static final int[][] horizonals = new int[][]{
        {2, 3, 4, 5},
        {2, 3, 4, 5},
        {0, 1, 4, 5},
        {0, 1, 4, 5},
        {0, 1, 2, 3},
        {0, 1, 2, 3}};
    private final static double EPSILON = 0.00001;
    private int mySide;
    private int facadeMask;
    private AxisAlignedBB box;
    private double thickness;

    public QuadCornerKicker() {
        super();
    }

    private static boolean epsComp(float a, float b) {
        if (a == b) {
            return true;
        } else {
            return Math.abs(a - b) < EPSILON;
        }
    }

    public void setSide(int side) {
        this.mySide = side;
    }

    public void setFacadeMask(int mask) {
        this.facadeMask = mask;
    }

    public void setBox(AxisAlignedBB box) {
        this.box = box;
    }

    public void setThickness(double thickness) {
        this.thickness = thickness;
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        EnumFacing nominalFace = quad.nominalFace();
        if (nominalFace == null || this.box == null || this.mySide < 0 || this.mySide >= horizonals.length) {
            return true;
        }

        int side = nominalFace.ordinal();
        if (side != this.mySide && side != (this.mySide ^ 1)) {
            for (int hoz : horizonals[this.mySide]) {
                if (side != hoz && side != (hoz ^ 1) && (this.facadeMask & 1 << hoz) != 0) {
                    Corner corner = Corner.fromSides(this.mySide ^ 1, side, hoz);
                    for (int i = 0; i < 4; i++) {
                        float x = quad.posByIndex(i, 0);
                        float y = quad.posByIndex(i, 1);
                        float z = quad.posByIndex(i, 2);
                        if (epsComp(x, corner.pX(this.box)) && epsComp(y, corner.pY(this.box))
                            && epsComp(z, corner.pZ(this.box))) {
                            Vec3i vec = EnumFacing.byIndex(hoz).getDirectionVec();
                            x -= (float) (vec.getX() * this.thickness);
                            y -= (float) (vec.getY() * this.thickness);
                            z -= (float) (vec.getZ() * this.thickness);
                            quad.pos(i, x, y, z);
                        }
                    }
                }
            }
        }

        return true;
    }

    public enum Corner {

        MIN_X_MIN_Y_MIN_Z(NEGATIVE, NEGATIVE, NEGATIVE), MIN_X_MIN_Y_MAX_Z(NEGATIVE, NEGATIVE, POSITIVE),
        MIN_X_MAX_Y_MIN_Z(NEGATIVE, POSITIVE, NEGATIVE), MIN_X_MAX_Y_MAX_Z(NEGATIVE, POSITIVE, POSITIVE),

        MAX_X_MIN_Y_MIN_Z(POSITIVE, NEGATIVE, NEGATIVE), MAX_X_MIN_Y_MAX_Z(POSITIVE, NEGATIVE, POSITIVE),
        MAX_X_MAX_Y_MIN_Z(POSITIVE, POSITIVE, NEGATIVE), MAX_X_MAX_Y_MAX_Z(POSITIVE, POSITIVE, POSITIVE);

        private static final int[] sideMask = {0, 2, 0, 1, 0, 4};
        private final AxisDirection xAxis;
        private final AxisDirection yAxis;
        private final AxisDirection zAxis;

        Corner(AxisDirection xAxis, AxisDirection yAxis, AxisDirection zAxis) {
            this.xAxis = xAxis;
            this.yAxis = yAxis;
            this.zAxis = zAxis;
        }

        public static Corner fromSides(int sideA, int sideB, int sideC) {
            return values()[sideMask[sideA] | sideMask[sideB] | sideMask[sideC]];
        }

        public float pX(AxisAlignedBB box) {
            return (float) (this.xAxis == NEGATIVE ? box.minX : box.maxX);
        }

        public float pY(AxisAlignedBB box) {
            return (float) (this.yAxis == NEGATIVE ? box.minY : box.maxY);
        }

        public float pZ(AxisAlignedBB box) {
            return (float) (this.zAxis == NEGATIVE ? box.minZ : box.maxZ);
        }
    }

}
