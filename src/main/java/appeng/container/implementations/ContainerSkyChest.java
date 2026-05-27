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
package appeng.container.implementations;

import appeng.api.inventories.InternalInventory;
import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.slot.AppEngSlot;
import appeng.tile.storage.TileSkyChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSkyChest extends AEBaseContainer {
    private final TileSkyChest chest;

    public ContainerSkyChest(InventoryPlayer ip, TileSkyChest chest) {
        super(ip, chest);
        this.chest = chest;
        chest.openInventory(ip.player);

        InternalInventory inv = chest.getInternalInventory();
        for (int i = 0; i < inv.size(); i++) {
            this.addSlot(new AppEngSlot(inv, i), SlotSemantics.STORAGE);
        }

        this.addPlayerInventorySlots(8, 105);
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        this.chest.closeInventory(player);
    }

    public TileSkyChest getChest() {
        return this.chest;
    }
}
