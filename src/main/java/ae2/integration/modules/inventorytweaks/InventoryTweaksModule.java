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

package ae2.integration.modules.inventorytweaks;

import ae2.integration.abstraction.IInvTweaks;
import invtweaks.forge.InvTweaksMod;
import net.minecraft.item.ItemStack;

public class InventoryTweaksModule implements IInvTweaks {

    @Override
    public boolean isEnabled() {
        return InvTweaksMod.instance != null;
    }

    @Override
    public int compareItems(ItemStack first, ItemStack second) {
        return InvTweaksMod.instance.compareItems(first, second);
    }
}
