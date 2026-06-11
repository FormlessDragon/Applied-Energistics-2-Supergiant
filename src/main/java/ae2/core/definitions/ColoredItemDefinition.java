/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.core.definitions;

import ae2.api.util.AEColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public final class ColoredItemDefinition<T extends Item> {

    private final Map<AEColor, ItemDefinition<T>> items = new EnumMap<>(AEColor.class);
    private final Map<AEColor, ResourceLocation> ids = new EnumMap<>(AEColor.class);

    void add(AEColor color, ResourceLocation id, ItemDefinition<T> itemDefinition) {
        this.ids.put(color, id);
        this.items.put(color, itemDefinition);
    }

    public ResourceLocation id(AEColor color) {
        return this.ids.get(color);
    }

    public @Nullable T item(AEColor color) {
        ItemDefinition<T> itemDefinition = this.items.get(color);
        return itemDefinition != null ? itemDefinition.asItem() : null;
    }

    public ItemStack stack(AEColor color) {
        return stack(color, 1);
    }

    public ItemStack stack(AEColor color, int stackSize) {
        T item = item(color);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, stackSize);
    }
}
