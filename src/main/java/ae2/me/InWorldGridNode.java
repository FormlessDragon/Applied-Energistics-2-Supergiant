/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.me;

import ae2.api.networking.GridFlags;
import ae2.api.networking.GridHelper;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.util.AEColor;
import ae2.core.AELog;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import java.util.EnumSet;
import java.util.Set;

/**
 * A grid node that is accessible from within the world will also look actively for connections to nodes that are
 * adjacent in the world.
 */
public class InWorldGridNode extends GridNode {

    private final BlockPos location;

    private final EnumSet<EnumFacing> exposedOnSides = EnumSet.noneOf(EnumFacing.class);

    public <T> InWorldGridNode(WorldServer level,
                               BlockPos location,
                               T owner,
                               IGridNodeListener<T> listener,
                               Set<GridFlags> flags) {
        super(level, owner, listener, flags);
        this.location = location;
    }

    @Override
    protected void findInWorldConnections() {
        // Clean up any connections that we might have left over to nodes that we can no longer reach
        cleanupConnections();

        // Find adjacent nodes in the world based on the sides of the host this node is exposed on
        sides:
        for (EnumFacing direction : exposedOnSides) {
            BlockPos pos = location.offset(direction);
            GridNode adjacentNode = (GridNode) GridHelper.getExposedNode(getLevel(), pos, direction.getOpposite());
            if (adjacentNode == null) {
                continue;
            }

            // It is implied that the other node is exposed on the side since the host did return it for the side
            // so the only remaining condition is that the grid colors are compatible
            if (!hasCompatibleColor(adjacentNode)) {
                continue;
            }

            // Clean up phantom node connections for this side, if applicable
            for (var c : this.connections) {
                if (c.isInWorld() && c.getDirection(this) == direction) {
                    // This can essentially only occur if the adjacent node has changed, but the previous node has
                    // not properly severed their connection
                    var os = c.getOtherSide(this);
                    if (os == adjacentNode) {
                        // Keep the existing connection and carry on
                        continue sides;
                    } else {
                        AELog.warn("Grid node %s did not disconnect properly and is now replaced with %s",
                            os, adjacentNode);
                        c.destroy();
                    }
                    break;
                }
            }

            GridConnection.create(this, adjacentNode, direction);
        }
    }

    @Override
    public String toString() {
        return super.toString() + " @ " + location.getX() + "," + location.getY() + "," + location.getZ();
    }

    private void cleanupConnections() {
        // NOTE: this makes a defensive copy of the connections
        for (var connection : getConnections()) {
            if (!connection.isInWorld()) {
                continue; // Purely internal connections are never cleaned up
            }

            EnumFacing ourSide = connection.getDirection(this);
            // If our external side is no longer exposed, the connection is invalid
            if (!isExposedOnSide(ourSide)) {
                connection.destroy();
                continue;
            }

            EnumFacing theirSide = null;
            if (ourSide != null) {
                theirSide = ourSide.getOpposite();
            }
            IGridNode otherNode = connection.getOtherSide(this);
            if (!(otherNode instanceof InWorldGridNode otherInWorldNode)) {
                connection.destroy();
                continue;
            }
            if (!otherInWorldNode.isExposedOnSide(theirSide) || !hasCompatibleColor(otherNode)) {
                connection.destroy();
            }
        }
    }

    private boolean hasCompatibleColor(IGridNode otherNode) {
        AEColor ourColor = getGridColor();
        AEColor theirColor = otherNode.getGridColor();
        return ourColor == AEColor.TRANSPARENT || theirColor == AEColor.TRANSPARENT || ourColor == theirColor;
    }

    public BlockPos getLocation() {
        return location;
    }

    public void setExposedOnSides(Set<EnumFacing> directions) {
        if (!exposedOnSides.equals(directions)) {
            exposedOnSides.clear();
            exposedOnSides.addAll(directions);
            updateState();
        }
    }

    public boolean isExposedOnSide(EnumFacing side) {
        return getMyGrid() != null && exposedOnSides.contains(side);
    }

}
