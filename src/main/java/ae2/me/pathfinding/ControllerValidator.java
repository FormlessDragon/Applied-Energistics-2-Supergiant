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

package ae2.me.pathfinding;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridVisitor;
import ae2.api.networking.pathing.ControllerState;
import ae2.tile.networking.TileController;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;

/**
 * Validates that the controller shape doesn't exceed the max size, and counts the number of adjacent controllers.
 */
public class ControllerValidator implements IGridVisitor {

    /**
     * Maximum size of controller structure on each axis.
     */
    public static final int MAX_SIZE = 7;

    private boolean valid = true;
    private int found = 0;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    /**
     * @param pos The position of the controller this visitor is first applied to.
     */
    private ControllerValidator(BlockPos pos) {
        this.minX = pos.getX();
        this.maxX = pos.getX();
        this.minY = pos.getY();
        this.maxY = pos.getY();
        this.minZ = pos.getZ();
        this.maxZ = pos.getZ();
    }

    /**
     * Conditions for valid controllers in grids: 1) The controller structure must not exceed max size (isValid()) 2)
     * All controllers of this grid must be connected to the first controller. This is true if the validator could reach
     * all controllers from the first. 3) There must not be any crosses.
     */
    public static ControllerState calculateState(Collection<TileController> controllers) {
        if (controllers.isEmpty()) {
            return ControllerState.NO_CONTROLLER;
        }

        var startingController = controllers.iterator().next();
        var startingNode = startingController.getGridNode();
        if (startingNode == null) {
            return ControllerState.CONTROLLER_CONFLICT;
        }

        var cv = new ControllerValidator(startingController.getPos());
        startingNode.beginVisit(cv);

        if (!cv.isValid()) {
            return ControllerState.CONTROLLER_CONFLICT;
        }

        if (cv.getFound() != controllers.size()) {
            return ControllerState.CONTROLLER_CONFLICT;
        }

        if (hasControllerCross(controllers)) {
            return ControllerState.CONTROLLER_CONFLICT;
        }

        return ControllerState.CONTROLLER_ONLINE;
    }

    /**
     * Return true if controllers have a cross pattern, i.e. two neighbors on two or three axes.
     */
    private static boolean hasControllerCross(Collection<TileController> controllers) {
        ObjectSet<BlockPos> posSet = new ObjectOpenHashSet<>(controllers.size());
        for (var controller : controllers) {
            posSet.add(controller.getPos().toImmutable());
        }

        for (var pos : posSet) {
            boolean northSouth = posSet.contains(pos.offset(EnumFacing.NORTH))
                && posSet.contains(pos.offset(EnumFacing.SOUTH));
            boolean eastWest = posSet.contains(pos.offset(EnumFacing.EAST))
                && posSet.contains(pos.offset(EnumFacing.WEST));
            boolean upDown = posSet.contains(pos.offset(EnumFacing.UP))
                && posSet.contains(pos.offset(EnumFacing.DOWN));

            if ((northSouth ? 1 : 0) + (eastWest ? 1 : 0) + (upDown ? 1 : 0) > 1) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean visitNode(IGridNode node) {
        if (this.isValid() && node.getOwner() instanceof TileController c) {

            var pos = c.getPos();

            this.minX = Math.min(pos.getX(), this.minX);
            this.maxX = Math.max(pos.getX(), this.maxX);
            this.minY = Math.min(pos.getY(), this.minY);
            this.maxY = Math.max(pos.getY(), this.maxY);
            this.minZ = Math.min(pos.getZ(), this.minZ);
            this.maxZ = Math.max(pos.getZ(), this.maxZ);

            if (this.maxX - this.minX < MAX_SIZE
                && this.maxY - this.minY < MAX_SIZE
                && this.maxZ - this.minZ < MAX_SIZE) {
                this.found++;
                return true;
            }

            this.valid = false;
        }

        // Only visit neighbors if this is a controller. This ensures that we only visit adjacent controllers.
        return false;
    }

    public boolean isValid() {
        return this.valid;
    }

    public int getFound() {
        return this.found;
    }
}
