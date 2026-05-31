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

package ae2.core;

import ae2.api.ids.AECreativeTabIds;
import ae2.core.definitions.AEItems;
import ae2.items.parts.FacadeItem;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public final class FacadeCreativeTab extends CreativeTabs {

    public static final FacadeCreativeTab INSTANCE = new FacadeCreativeTab();

    private FacadeCreativeTab() {
        super(AECreativeTabIds.FACADES.toString());
    }

    @Override
    public ItemStack createIcon() {
        FacadeItem itemFacade = AEItems.FACADE.asItem();
        return itemFacade == null ? ItemStack.EMPTY : itemFacade.getCreativeTabIcon();
    }

    @Override
    public void displayAllRelevantItems(NonNullList<ItemStack> itemStacks) {
        FacadeItem itemFacade = AEItems.FACADE.asItem();
        if (itemFacade == null) {
            return;
        }

        itemStacks.addAll(itemFacade.getFacades());
    }
}
