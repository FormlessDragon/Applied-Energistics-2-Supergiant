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

package ae2.container.interfaces;

import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.ILinkStatus;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ICraftingGridContainer {
    /**
     * @return The current link status of the container, allowing for gating access to {@link #getGridNode()}.
     */
    default ILinkStatus getLinkStatus() {
        return ILinkStatus.ofConnected();
    }

    /**
     * @return gain access to network infrastructure.
     */
    @Nullable
    IGridNode getGridNode();

    /**
     * @return The energy source to use for grid operations (i.e. when transferring in / out from the network)
     */
    default IEnergySource getEnergySource() {
        IGridNode node = getGridNode();
        if (node == null) {
            return IEnergySource.empty();
        }
        return node.grid().getEnergyService();
    }

    /**
     * @return the inventory used for the crafting matrix.
     */
    InternalInventory getCraftingMatrix();

    /**
     * @return who are we?
     */
    IActionSource getActionSource();

    /**
     * @return list of view cells. can contain empty itemstacks.
     */
    List<ItemStack> getViewCells();

    /**
     * Autocraft the passed keys, in order. Will likely open the craft confirm container, so this container should not
     * be used afterward.
     */
    default void startAutoCrafting(List<AutoCraftEntry> toCraft) {
    }

    default void startTemporaryPseudoCrafting(List<GenericStack> inputs, List<GenericStack> outputs) {
    }

    /**
     * @return True if the given player inventory slot is locked by the current container and should not be used for
     * crafting. (i.e. the wireless terminal itself in case of a wireless crafting terminal).
     */
    boolean isPlayerInventorySlotLocked(int invSlot);

    record AutoCraftEntry(AEItemKey what, long amount, IntList slots) {
    }
}
