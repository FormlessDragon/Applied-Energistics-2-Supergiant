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

package ae2.container.implementations;

import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.slot.RestrictedInputSlot;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.tile.storage.TileMEChest;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerMEChest extends AEBaseContainer {
    public ContainerMEChest(InventoryPlayer playerInventory, TileMEChest host) {
        super(playerInventory, host);
        setLocator(GuiHostLocators.forTile(host));

        addSlotToContainer(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS,
                host.getInternalInventory().getSlotInv(1), 0),
            SlotSemantics.STORAGE_CELL);
        addPlayerInventorySlots(8, 84);
    }
}
