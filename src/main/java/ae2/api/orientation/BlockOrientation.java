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

package ae2.api.orientation;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.model.TRSRTransformation;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Quat4f;
import java.util.EnumSet;
import java.util.Set;

/**
 * All possible rotations for a fully orientable block.
 */
public enum BlockOrientation {

    // DUNSWE
    // @formatter:off
    DOWN_NORTH(90, 0, 0, 0),
    DOWN_WEST(90, 0, 270, 1),
    DOWN_SOUTH(90, 0, 180, 2),
    DOWN_EAST(90, 0, 90, 3),

    UP_NORTH(270, 0, 180, 0),
    UP_EAST(270, 0, 90, 1),
    UP_SOUTH(270, 0, 0, 2),
    UP_WEST(270, 0, 270, 3),

    NORTH_UP(0, 0, 0, 0), // Default,
    NORTH_WEST(0, 0, 270, 1),
    NORTH_DOWN(0, 0, 180, 2),
    NORTH_EAST(0, 0, 90, 3),

    SOUTH_UP(0, 180, 0, 0),
    SOUTH_EAST(0, 180, 90, 1),
    SOUTH_DOWN(0, 180, 180, 2),
    SOUTH_WEST(0, 180, 270, 3),

    WEST_UP(0, 270, 0, 0),
    WEST_SOUTH(0, 270, 270, 1),
    WEST_DOWN(0, 270, 180, 2),
    WEST_NORTH(0, 270, 90, 3),

    EAST_UP(0, 90, 0, 0),
    EAST_NORTH(0, 90, 270, 1),
    EAST_DOWN(0, 90, 180, 2),
    EAST_SOUTH(0, 90, 90, 3);
    // @formatter:on

    private static final BlockOrientation[] VALUES = values();

    private final int angleX;
    private final int angleY;
    private final int angleZ;
    private final Quat4f quaternion;
    private final TRSRTransformation transformation;
    /**
     * How many times it has been rotated clock-wise around in 90° increments around its facing.
     */
    private final int spin;

    BlockOrientation(int angleX, int angleY, int angleZ, int spin) {
        this.angleX = angleX;
        this.angleY = angleY;
        this.angleZ = angleZ;
        this.quaternion = createQuaternion(angleX, angleY, angleZ);
        this.transformation = angleX == 0 && angleY == 0 && angleZ == 0
            ? TRSRTransformation.identity()
            : new TRSRTransformation(null, this.quaternion, null, null);
        this.spin = spin;
    }

    public static BlockOrientation get(EnumFacing facing) {
        return get(facing, 0);
    }

    /**
     * Gets the block orientation in which the blocks front and top are facing the specified directions.
     */
    public static BlockOrientation get(EnumFacing front, EnumFacing top) {
        var offset = front.ordinal() * 4;
        for (var i = offset; i < offset + 4; i++) {
            var orientation = VALUES[i];
            if (orientation.getSide(RelativeSide.TOP) == top) {
                return orientation;
            }
        }
        return VALUES[offset]; // Degenerated up -> return default
    }

    public static BlockOrientation get(EnumFacing facing, int spin) {
        if (facing == null || spin < 0 || spin > 3) {
            return get(EnumFacing.NORTH);
        }

        return VALUES[facing.ordinal() * 4 + spin];
    }

    public static BlockOrientation get(TileEntity blockEntity) {
        var blockState = blockEntity.getWorld().getBlockState(blockEntity.getPos());
        return get(blockState);
    }

    public static BlockOrientation get(IBlockState state) {
        var strategy = IOrientationStrategy.get(state);
        return get(strategy, state);
    }

    public static BlockOrientation get(IOrientationStrategy strategy, IBlockState state) {
        var facing = strategy.getFacing(state);
        var spin = strategy.getSpin(state);
        return get(facing, spin);
    }

    private static EnumFacing rotateAround(EnumFacing side, EnumFacing.Axis axis, boolean positive) {
        int x = side.getXOffset();
        int y = side.getYOffset();
        int z = side.getZOffset();
        int rx;
        int ry;
        int rz;

        switch (axis) {
            case X -> {
                rx = x;
                ry = positive ? -z : z;
                rz = positive ? y : -y;
            }
            case Y -> {
                rx = positive ? z : -z;
                ry = y;
                rz = positive ? -x : x;
            }
            case Z -> {
                rx = positive ? -y : y;
                ry = positive ? x : -x;
                rz = z;
            }
            default -> throw new IllegalStateException("Unexpected axis: " + axis);
        }

        return EnumFacing.getFacingFromVector(rx, ry, rz);
    }

    private static Quat4f createQuaternion(int angleX, int angleY, int angleZ) {
        var quaternion = new Quat4f(0, 0, 0, 1);
        quaternion.mul(axisAngle(0, 1, 0, -angleY));
        quaternion.mul(axisAngle(1, 0, 0, -angleX));
        quaternion.mul(axisAngle(0, 0, 1, -angleZ));
        quaternion.normalize();
        return quaternion;
    }

    private static Quat4f axisAngle(float x, float y, float z, int angle) {
        var quaternion = new Quat4f();
        quaternion.set(new AxisAngle4f(x, y, z, (float) Math.toRadians(angle)));
        return quaternion;
    }

    /**
     * Changes the orientation of the given tile entity to this, if possible.
     */
    public void setOn(TileEntity tileEntity) {
        setOn(tileEntity.getWorld(), tileEntity.getPos());
    }

    /**
     * Changes the orientation of the block at the given position/level to this, if possible.
     */
    public void setOn(World level, BlockPos pos) {
        var state = level.getBlockState(pos);
        var strategy = IOrientationStrategy.get(state);
        var newState = strategy.setOrientation(state,
            getSide(RelativeSide.FRONT),
            getSpin());
        if (newState != state) {
            level.setBlockState(pos, newState, 3);
        }

    }

    public boolean isRedundant() {
        return angleX == 0 && angleY == 0 && angleZ == 0;
    }

    public Quat4f getQuaternion() {
        return new Quat4f(this.quaternion);
    }

    public TRSRTransformation getTransformation() {
        return this.transformation;
    }

    public EnumFacing rotate(EnumFacing facing) {
        if (isRedundant()) {
            return facing;
        }
        return TRSRTransformation.rotate(this.transformation.getMatrix(), facing);
    }

    public EnumFacing resultingRotate(EnumFacing facing) {
        for (EnumFacing candidate : EnumFacing.VALUES) {
            if (rotate(candidate) == facing) {
                return candidate;
            }
        }
        return facing;
    }

    public int getAngleX() {
        return angleX;
    }

    public int getAngleY() {
        return angleY;
    }

    public int getAngleZ() {
        return angleZ;
    }

    public int getSpin() {
        return spin;
    }

    public EnumFacing getSide(RelativeSide side) {
        return rotate(side.getUnrotatedSide());
    }

    public RelativeSide getRelativeSide(EnumFacing side) {
        return RelativeSide.fromUnrotatedSide(resultingRotate(side));
    }

    public Set<EnumFacing> getSides(Set<RelativeSide> relativeSides) {
        var result = EnumSet.noneOf(EnumFacing.class);
        for (var relativeSide : relativeSides) {
            result.add(getSide(relativeSide));
        }
        return result;
    }

    public Set<RelativeSide> getRelativeSides(Set<EnumFacing> sides) {
        var result = EnumSet.noneOf(RelativeSide.class);
        for (var side : sides) {
            result.add(getRelativeSide(side));
        }
        return result;
    }

    public BlockOrientation rotateClockwiseAround(EnumFacing side) {
        return rotateClockwiseAround(side.getAxis(), side.getAxisDirection());
    }

    public BlockOrientation rotateClockwiseAround(EnumFacing.Axis axis, EnumFacing.AxisDirection direction) {
        var facing = getSide(RelativeSide.FRONT);
        var up = getSide(RelativeSide.TOP);
        EnumFacing newFacing;
        EnumFacing newUp;
        if (direction == EnumFacing.AxisDirection.POSITIVE) {
            newFacing = rotateAround(facing, axis, true);
            newUp = rotateAround(up, axis, true);
        } else {
            newFacing = rotateAround(facing, axis, false);
            newUp = rotateAround(up, axis, false);
        }
        return get(newFacing, newUp);
    }
}
