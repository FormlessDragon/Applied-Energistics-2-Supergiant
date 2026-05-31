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
import ae2.container.AEBaseContainer;
import com.google.common.base.Preconditions;
import net.minecraft.item.ItemStack;

/**
 * Exposes the carried item stored in a container as an {@link InternalInventory}.
 */
public class CarriedItemInventory implements InternalInventory {
    private final AEBaseContainer container;

    public CarriedItemInventory(AEBaseContainer container) {
        this.container = container;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slotIndex) {
        Preconditions.checkArgument(slotIndex == 0);
        return this.container.getPlayerInventory().getItemStack();
    }

    @Override
    public void setItemDirect(int slotIndex, ItemStack stack) {
        Preconditions.checkArgument(slotIndex == 0);
        this.container.getPlayerInventory().setItemStack(stack);
    }
}
