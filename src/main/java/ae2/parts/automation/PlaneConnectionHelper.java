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

package ae2.parts.automation;

import ae2.api.parts.IPart;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartHost;
import ae2.parts.AEBasePart;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Helps plane parts (annihilation, formation) with determining and checking for connections to adjacent plane parts of
 * the same type to form a visually larger plane.
 */
public final class PlaneConnectionHelper {

    private final AEBasePart part;

    public PlaneConnectionHelper(AEBasePart part) {
        this.part = part;
    }

    /**
     * Gets on which sides this part has adjacent planes that it visually connects to
     */
    public PlaneConnections getConnections() {
        TileEntity hostTileEntity = getHostTileEntity();
        EnumFacing side = part.getSide();
        if (side == null) {
            return PlaneConnections.of(false, false, false, false);
        }

        final EnumFacing facingRight;
        final EnumFacing facingUp;
        switch (side) {
            case UP -> {
                facingRight = EnumFacing.EAST;
                facingUp = EnumFacing.NORTH;
            }
            case DOWN -> {
                facingRight = EnumFacing.WEST;
                facingUp = EnumFacing.NORTH;
            }
            case NORTH -> {
                facingRight = EnumFacing.WEST;
                facingUp = EnumFacing.UP;
            }
            case SOUTH -> {
                facingRight = EnumFacing.EAST;
                facingUp = EnumFacing.UP;
            }
            case WEST -> {
                facingRight = EnumFacing.SOUTH;
                facingUp = EnumFacing.UP;
            }
            case EAST -> {
                facingRight = EnumFacing.NORTH;
                facingUp = EnumFacing.UP;
            }
            default -> {
                return PlaneConnections.of(false, false, false, false);
            }
        }

        boolean left = false, right = false, down = false, up = false;

        if (hostTileEntity != null) {
            World level = hostTileEntity.getWorld();
            BlockPos pos = hostTileEntity.getPos();

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(facingRight.getOpposite())))) {
                left = true;
            }

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(facingRight)))) {
                right = true;
            }

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(facingUp.getOpposite())))) {
                down = true;
            }

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(facingUp)))) {
                up = true;
            }
        }

        return PlaneConnections.of(up, right, down, left);
    }

    /**
     * Get the bounding boxes of this plane parts components.
     */
    public void getBoxes(IPartCollisionHelper bch) {
        int minX = 1;
        int minY = 1;
        int maxX = 15;
        int maxY = 15;

        TileEntity hostEntity = getHostTileEntity();
        if (hostEntity != null) {
            World level = hostEntity.getWorld();

            final BlockPos pos = hostEntity.getPos();

            final EnumFacing e = bch.getWorldX();
            final EnumFacing u = bch.getWorldY();

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(e.getOpposite())))) {
                minX = 0;
            }

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(e)))) {
                maxX = 16;
            }

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(u.getOpposite())))) {
                minY = 0;
            }

            if (isCompatiblePlaneAdjacent(level.getTileEntity(pos.offset(u)))) {
                maxY = 16;
            }
        }

        bch.addBox(5, 5, 14, 11, 11, 15);
        bch.addBox(minX, minY, 15, maxX, maxY, 16);
    }

    /**
     * Call this when an adjacent block has changed since the connections need to be recalculated.
     */
    public void updateConnections() {
        IPartHost host = part.getHost();
        if (host != null) {
            host.markForUpdate();
        }
    }

    private boolean isCompatiblePlaneAdjacent(@Nullable TileEntity adjacentTileEntity) {
        if (adjacentTileEntity instanceof IPartHost) {
            final IPart p = ((IPartHost) adjacentTileEntity).getPart(part.getSide());
            return p != null && p.getClass() == part.getClass();
        }
        return false;
    }

    private @org.jspecify.annotations.Nullable TileEntity getHostTileEntity() {
        IPartHost host = part.getHost();
        if (host != null) {
            return host.getTileEntity();
        }
        return null;
    }

}
