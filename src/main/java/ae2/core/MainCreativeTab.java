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
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.ItemDefinition;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public final class MainCreativeTab extends CreativeTabs {

    public static final MainCreativeTab INSTANCE = new MainCreativeTab();

    private static final Multimap<CreativeTabs, ItemDefinition<?>> externalItemDefs = HashMultimap.create();

    private MainCreativeTab() {
        super(AECreativeTabIds.MAIN.toString());
    }

    public static void addExternal(CreativeTabs tab, ItemDefinition<?> itemDef) {
        externalItemDefs.put(tab, itemDef);
    }

    public static void addExternalItems(CreativeTabs tab, NonNullList<ItemStack> itemStacks) {
        for (ItemDefinition<?> itemDefinition : externalItemDefs.get(tab)) {
            addToCreativeTab(tab, itemStacks, itemDefinition);
        }
    }

    private static void addToCreativeTab(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks,
                                         ItemDefinition<?> itemDef) {
        Item item = itemDef.asItem();
        if (item == null) {
            return;
        }

        CreativeTabs queryTab = creativeTab == INSTANCE ? item.getCreativeTab() : creativeTab;
        if (queryTab != null) {
            item.getSubItems(queryTab, itemStacks);
        }
    }

    @Override
    public ItemStack createIcon() {
        return AEBlocks.CONTROLLER.stack(1);
    }
}
