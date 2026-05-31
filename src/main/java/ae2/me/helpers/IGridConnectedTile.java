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

package ae2.me.helpers;

import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridNodeListener;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.orientation.BlockOrientation;
import ae2.block.IOwnerAwareTile;
import ae2.me.InWorldGridNode;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interface implemented by the various AE2 tile entities that connect to the grid, to support callbacks from the tile
 * entities main grid node.
 */
public interface IGridConnectedTile extends IActionHost, IOwnerAwareTile, IInWorldGridNodeHost {

    /**
     * @return The main node that the tile entity uses to connect to the grid.
     */
    IManagedGridNode getMainNode();

    /**
     * @param orientation The current orientation of the block.
     * @return The sides of this block that are exposed for ME grid connections.
     */
    default Set<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(EnumFacing.class);
    }

    @Nullable
    default IGridNode getGridNode() {
        return getMainNode().getNode();
    }

    @Override
    default IGridNode getGridNode(EnumFacing dir) {
        var node = this.getMainNode().getNode();

        // We use the node rather than getGridConnectableSides since the node is already using absolute sides
        if (node instanceof InWorldGridNode inWorldGridNode
            && inWorldGridNode.isExposedOnSide(dir)) {
            return node;
        }

        return null;
    }

    /**
     * @see IManagedGridNode#ifPresent(Consumer)
     */
    default void ifGridPresent(Consumer<IGrid> action) {
        getMainNode().ifPresent(action);
    }

    /**
     * Used to save changes in the grid nodes contained in the tile entity to disk.
     */
    void saveChanges();

    /**
     * Called when the tile entities main grid nodes power or channel assignment state changes. Primarily used to send
     * rendering updates to the client.
     */
    default void onMainNodeStateChanged(IGridNodeListener.State reason) {
    }

    @Override
    default IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    default void setOwner(EntityPlayer owner) {
        getMainNode().setOwningPlayer(owner);
    }
}
