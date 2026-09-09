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

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridConnection;
import ae2.api.networking.IGridConnectionVisitor;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.me.GridConnection;
import ae2.me.GridNode;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

public class ChannelFinalizer implements IGridConnectionVisitor {

    private final IGrid grid;
    private final ObjectArrayList<GridNode> changedNodes = new ObjectArrayList<>();
    private final ReferenceSet<GridNode> changedNodeSet = new ReferenceOpenHashSet<>();

    public ChannelFinalizer(IGrid grid) {
        this.grid = grid;
    }

    @Override
    public boolean visitNode(IGridNode n) {
        finalizeItem((GridNode) n);
        return true;
    }

    @Override
    public void visitConnection(IGridConnection gcc) {
        finalizeItem((GridConnection) gcc);
    }

    public void finalizeItem(IPathItem item) {
        if (!item.finalizeChannels()) {
            return;
        }

        if (item instanceof GridNode node) {
            addChangedNode(node);
        } else {
            var connection = (GridConnection) item;
            addChangedNode(connection.a());
            addChangedNode(connection.b());
        }
    }

    private void addChangedNode(GridNode node) {
        if (changedNodeSet.add(node)) {
            changedNodes.add(node);
        }
    }

    /**
     * Notifies each affected node once, after every path item has committed its final channel count.
     */
    public void notifyChangedNodes() {
        try {
            for (int i = 0, size = changedNodes.size(); i < size; i++) {
                var node = changedNodes.get(i);
                if (node.isAttachedToGrid(grid)) {
                    node.notifyStatusChange(IGridNodeListener.State.CHANNEL);
                }
            }
        } finally {
            clear();
        }
    }

    public void clear() {
        changedNodes.clear();
        changedNodeSet.clear();
    }
}
