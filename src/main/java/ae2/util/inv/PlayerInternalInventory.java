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

package ae2.util.inv;

import ae2.api.inventories.InternalInventory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * Exposes the main player inventory and hotbar as an {@link InternalInventory}.
 */
public class PlayerInternalInventory implements InternalInventory {
    private final InventoryPlayer inventory;

    public PlayerInternalInventory(InventoryPlayer inventory) {
        this.inventory = inventory;
    }

    @Override
    public int size() {
        return this.inventory.mainInventory.size();
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        return this.inventory.mainInventory.get(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        this.inventory.mainInventory.set(slotIndex, stack);
        if (!stack.isEmpty()) {
            stack.setAnimationsToGo(5);
        }
    }
}
