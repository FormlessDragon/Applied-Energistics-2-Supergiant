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

package ae2.me;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridNode;
import ae2.api.networking.pathing.ChannelMode;
import ae2.core.AELog;
import ae2.me.pathfinding.IPathItem;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public class GridConnection implements IGridConnection, IPathItem {
    private static long topologyRevision;

    static void topologyChanged() {
        topologyRevision++;
    }

    /**
     * Implements GridHelper's synchronous batch disconnect without repeated pivot searches.
     */
    public static void destroyConnections(Collection<? extends IGridConnection> connections) {
        Objects.requireNonNull(connections, "connections");
        if (connections.isEmpty()) {
            return;
        }
        var unique = new ReferenceOpenHashSet<GridConnection>();
        var detached = new ObjectArrayList<GridConnection>();
        if (connections instanceof List<? extends IGridConnection> list && list instanceof RandomAccess) {
            for (int i = 0; i < list.size(); i++) {
                collectAttached(list.get(i), unique, detached);
            }
        } else {
            for (var connection : connections) {
                collectAttached(connection, unique, detached);
            }
        }
        if (detached.isEmpty()) {
            return;
        }
        var seeds = new ObjectArrayList<GridNode>();
        var grids = new ReferenceOpenHashSet<Grid>();
        for (int i = 0; i < detached.size(); i++) {
            var connection = detached.get(i);
            seeds.add(connection.sideA);
            seeds.add(connection.sideB);
            grids.add(connection.sideA.getInternalGrid());
            grids.add(connection.sideB.getInternalGrid());
        }
        for (int i = 0; i < detached.size(); i++) {
            var connection = detached.get(i);
            connection.sideA.detachConnection(connection);
            connection.sideB.detachConnection(connection);
        }
        for (var grid : grids) {
            grid.getPathingService().repath();
        }
        long detachedRevision = topologyRevision;
        for (int i = 0; i < detached.size(); i++) {
            var connection = detached.get(i);
            connection.notifyDetached();
        }
        // Pure removal leaves each component in its old grid. It is enough to prove connectivity to that grid's
        // pivot or an already resolved region. Interrupted migrations need a complete classification instead.
        boolean canStopAtRetainedGrid = topologyRevision == detachedRevision;
        var resolved = new ReferenceOpenHashSet<GridNode>();
        var visited = new ReferenceOpenHashSet<GridNode>();
        var component = new ObjectArrayList<GridNode>();
        for (int seedIndex = 0; seedIndex < seeds.size(); seedIndex++) {
            var seed = seeds.get(seedIndex);
            if (!seed.isReady() || seed.getMyGrid() == null || resolved.contains(seed)) {
                continue;
            }
            var originalGrid = seed.getMyGrid();
            visited.clear();
            visited.add(seed);
            component.clear();
            component.add(seed);
            Grid retained = null;
            search:
            for (int i = 0; i < component.size(); i++) {
                var node = component.get(i);
                var nodeGrid = node.getMyGrid();
                if (canStopAtRetainedGrid && nodeGrid == originalGrid
                    && (node == originalGrid.getPivot() || resolved.contains(node))) {
                    retained = originalGrid;
                    break;
                }
                if (nodeGrid != null && nodeGrid.getPivot() == node
                    && (retained == null || isGridABetterThanGridB(nodeGrid, retained))) {
                    retained = nodeGrid;
                }
                for (var neighbor : node.connections.keySet()) {
                    if (!neighbor.isReady()) {
                        continue;
                    }
                    if (canStopAtRetainedGrid && neighbor.getMyGrid() == originalGrid
                        && (neighbor == originalGrid.getPivot() || resolved.contains(neighbor))) {
                        retained = originalGrid;
                        break search;
                    }
                    if (visited.add(neighbor)) {
                        component.add(neighbor);
                    }
                }
            }
            long revision = topologyRevision;
            if (retained == null) {
                revision++;
                retained = Grid.create(seed);
            }
            for (int i = 0; i < component.size() && topologyRevision == revision; i++) {
                var node = component.get(i);
                if (node.isReady() && node.getMyGrid() != retained) {
                    revision++;
                    node.setGrid(retained);
                }
            }
            if (topologyRevision != revision) {
                // A lifecycle callback changed connections. Previously classified components are no longer valid.
                var knownSeeds = new ReferenceOpenHashSet<>(seeds);
                for (int i = 0; i < component.size(); i++) {
                    var node = component.get(i);
                    if (knownSeeds.add(node)) {
                        seeds.add(node);
                    }
                }
                resolved.clear();
                canStopAtRetainedGrid = false;
                seedIndex = -1;
            } else {
                resolved.addAll(component);
            }
        }
    }

    private static void collectAttached(IGridConnection candidate, ReferenceOpenHashSet<GridConnection> unique,
                                        ObjectArrayList<GridConnection> detached) {
        if (!(candidate instanceof GridConnection connection)) {
            AELog.error("Batch disconnect received a foreign or null connection: %s", candidate);
            throw new IllegalArgumentException("Batch disconnect requires AE grid connections");
        }
        boolean attachedA = connection.sideA.connections.get(connection.sideB) == connection;
        boolean attachedB = connection.sideB.connections.get(connection.sideA) == connection;
        if (attachedA != attachedB) {
            AELog.error("Grid connection is attached to only one endpoint: %s", connection);
            throw new IllegalStateException("Grid connection is attached to only one endpoint");
        }
        if (attachedA && unique.add(connection)) {
            detached.add(connection);
        }
    }

    /**
     * Will be modified during pathing and should not be exposed outside of that purpose.
     */
    int usedChannels = 0;
    /**
     * Finalized version of {@link #usedChannels} once pathing is done.
     */
    private int lastUsedChannels = 0;
    private Object visitorIterationNumber = null;
    /**
     * Note that in grids with a controller, following this side will always lead down the closest path towards the
     * controller.
     */
    private GridNode sideA;
    @Nullable
    private EnumFacing fromAtoB;
    private GridNode sideB;

    private GridConnection(GridNode aNode, GridNode bNode, @Nullable EnumFacing fromAtoB) {
        this.sideA = aNode;
        this.fromAtoB = fromAtoB;
        this.sideB = bNode;
    }

    /**
     * @throws IllegalStateException If the nodes are already connected.
     */
    public static GridConnection create(IGridNode aNode, IGridNode bNode,
                                        @Nullable EnumFacing fromAtoB) {
        Objects.requireNonNull(aNode, "aNode");
        Objects.requireNonNull(bNode, "bNode");
        Preconditions.checkArgument(aNode != bNode, "Cannot connect node to itself");

        var a = (GridNode) aNode;
        var b = (GridNode) bNode;

        if (a.hasConnection(b) || b.hasConnection(a)) {
            throw new IllegalStateException(String.format(
                "Connection between node [%s] and [%s] on [%s] already exists.", a, b, fromAtoB));
        }

        // Create the actual connection
        var connection = new GridConnection(a, b, fromAtoB);

        mergeGrids(a, b);

        // a connection was destroyed RE-PATH!!
        var p = connection.sideA.getInternalGrid().getPathingService();
        p.repath();

        connection.sideA.addConnection(connection);
        connection.sideB.addConnection(connection);

        return connection;
    }

    /**
     * Merge the grids of two grid nodes based on both becoming connected. This method assumes that the new connection
     * is NOT yet created, otherwise grid propagation will do more work than needed.
     */
    private static void mergeGrids(GridNode a, GridNode b) {
        // Update both nodes with the new connection.
        var gridA = a.getMyGrid();
        var gridB = b.getMyGrid();
        if (gridA == null && gridB == null) {
            // Neither A nor B has a grid, create a new grid spanning both
            assertNodeIsStandalone(a);
            assertNodeIsStandalone(b);
            var grid = Grid.create(a);
            a.setGrid(grid);
            b.setGrid(grid);
        } else if (gridA == null) {
            // Only node B has a grid, propagate it to A
            assertNodeIsStandalone(a);
            a.setGrid(gridB);
        } else if (gridB == null) {
            // Only node A has a grid, propagate it to B
            assertNodeIsStandalone(b);
            b.setGrid(gridA);
        } else if (gridA != gridB) {
            if (isGridABetterThanGridB(gridA, gridB)) {
                // Both A and B have grids, but A's grid is "better" -> propagate it to B and all its connected nodes
                var gp = new GridPropagator(a.getInternalGrid());
                b.beginVisit(gp);
            } else {
                // Both A and B have grids, but B's grid is "better" -> propagate it to A and all its connected nodes
                var gp = new GridPropagator(b.getInternalGrid());
                a.beginVisit(gp);
            }
        }
    }

    private static boolean isGridABetterThanGridB(Grid gridA, Grid gridB) {
        if (gridA.getPriority() != gridB.getPriority()) {
            return gridA.getPriority() > gridB.getPriority();
        }
        return gridA.size() >= gridB.size();
    }

    private static void assertNodeIsStandalone(GridNode node) {
        if (!node.hasNoConnections()) {
            throw new IllegalStateException("Grid node " + node + " has no grid, but is connected: "
                + node.getConnections());
        }
    }

    @Override
    public IGridNode getOtherSide(IGridNode gridNode) {
        if (gridNode == this.sideA) {
            return this.sideB;
        }
        if (gridNode == this.sideB) {
            return this.sideA;
        }

        throw new IllegalArgumentException("The given grid node does not participate in this connection.");
    }

    @Override
    public EnumFacing getDirection(IGridNode side) {
        if (this.fromAtoB == null) {
            return null;
        }

        if (this.sideA == side) {
            return this.fromAtoB;
        } else {
            return this.fromAtoB.getOpposite();
        }
    }

    @Override
    public void destroy() {
        boolean attachedA = this.sideA.connections.get(this.sideB) == this;
        boolean attachedB = this.sideB.connections.get(this.sideA) == this;
        Preconditions.checkState(attachedA == attachedB, "Grid connection is attached to only one endpoint");
        if (!attachedA) {
            return;
        }
        this.sideA.getInternalGrid().getPathingService().repath();
        this.sideA.detachConnection(this);
        this.sideB.detachConnection(this);
        notifyDetached();
        // Single-edge removal retains the existing early-exit pivot search.
        this.sideA.validateGrid();
        this.sideB.validateGrid();
    }

    private void notifyDetached() {
        notifyDetached(this.sideA);
        notifyDetached(this.sideB);
    }

    private void notifyDetached(GridNode node) {
        try {
            node.notifyConnectionRemoved(this);
        } catch (RuntimeException exception) {
            AELog.error(exception, "Grid connection removal callback failed for " + node);
        }
    }

    @Override
    public GridNode a() {
        return this.sideA;
    }

    @Override
    public GridNode b() {
        return this.sideB;
    }

    @Override
    public boolean isInWorld() {
        return this.fromAtoB != null;
    }

    @Override
    public int getUsedChannels() {
        return lastUsedChannels;
    }

    @Override
    public void setAdHocChannels(int channels) {
        this.usedChannels = channels;
    }

    @Override
    public GridNode getControllerRoute() {
        return this.sideA;
    }

    @Override
    public void setControllerRoute(IPathItem fast) {
        this.usedChannels = 0;

        // If the shortest route to the controller is via side B, we need to flip the
        // connections sides because side A should be the closest route to the controller.
        if (this.sideB == fast) {
            var tmp = this.sideA;
            this.sideA = this.sideB;
            this.sideB = tmp;
            if (this.fromAtoB != null) {
                this.fromAtoB = this.fromAtoB.getOpposite();
            }
        }
    }

    @Override
    public int getMaxChannels() {
        var mode = sideB.grid().getPathingService().channelMode();
        if (mode == ChannelMode.INFINITE) {
            return Integer.MAX_VALUE;
        }
        return 32 * mode.getCableCapacityFactor();
    }

    @Override
    public int getPossibleOptionCount() {
        return 2;
    }

    @Override
    public IPathItem getPossibleOption(int index) {
        return switch (index) {
            case 0 -> this.sideA;
            case 1 -> this.sideB;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public boolean hasFlag(GridFlags flag) {
        return false;
    }

    public int propagateChannelsUpwards() {
        if (this.sideB.getControllerRoute() == this) { // Check that we are in B's route
            this.usedChannels = this.sideB.usedChannels;
        } else {
            this.usedChannels = 0;
        }
        this.sideA.incrementChannelCount(this.usedChannels);
        return this.usedChannels;
    }

    @Override
    public boolean finalizeChannels() {
        if (this.lastUsedChannels != this.usedChannels) {
            this.lastUsedChannels = this.usedChannels;
            return true;
        }
        return false;
    }

    Object getVisitorIterationNumber() {
        return this.visitorIterationNumber;
    }

    void setVisitorIterationNumber(Object visitorIterationNumber) {
        this.visitorIterationNumber = visitorIterationNumber;
    }
}
