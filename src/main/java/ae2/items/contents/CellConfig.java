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

package ae2.items.contents;

import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.AEKeyTypes;
import ae2.api.stacks.GenericStack;
import ae2.util.ConfigInventory;
import ae2.util.Platform;
import com.google.common.base.Preconditions;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CellConfig {
    private static final String STORAGE_CELL_CONFIG_INV_TAG = "storageCellConfigInv";
    private static final String STORAGE_CELL_CONFIG_INV = "storage_cell_config_inv";

    private CellConfig() {
    }

    public static ConfigInventory create(Set<AEKeyType> supportedTypes, ItemStack stack, int size) {
        Preconditions.checkArgument(size >= 1, "Config inventory must have at least one slot.");
        var holder = new Holder(stack);
        holder.inv = ConfigInventory.configTypes(size)
                                    .supportedTypes(supportedTypes)
                                    .changeListener(holder::save)
                                    .build();
        holder.load();
        return holder.inv;
    }

    public static ConfigInventory create(Set<AEKeyType> supportedTypes, ItemStack stack) {
        return create(supportedTypes, stack, 63);
    }

    public static ConfigInventory create(ItemStack stack) {
        var holder = new Holder(stack);
        holder.inv = ConfigInventory.configTypes(63)
                                    .supportedTypes(AEKeyTypes.getAll())
                                    .changeListener(holder::save)
                                    .build();
        holder.load();
        return holder.inv;
    }

    private static class Holder {
        private final ItemStack stack;
        private ConfigInventory inv;

        private Holder(ItemStack stack) {
            this.stack = stack;
        }

        private void load() {
            if (!stack.hasTagCompound()) {
                return;
            }

            var tag = stack.getTagCompound();
            if (tag == null) {
                return;
            }

            inv.beginBatch();
            try {
                if (tag.hasKey(STORAGE_CELL_CONFIG_INV, 9)) {
                    var componentData = tag.getTagList(STORAGE_CELL_CONFIG_INV, 10);
                    inv.readFromList(GenericStack.readList(componentData));
                } else {
                    inv.readFromChildTag(tag, STORAGE_CELL_CONFIG_INV_TAG);
                }
            } finally {
                inv.endBatchSuppressed();
            }
        }

        private void save() {
            var tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
            }

            List<GenericStack> list = inv.toList();
            if (list.stream().allMatch(Objects::isNull)) {
                tag.removeTag(STORAGE_CELL_CONFIG_INV);
            } else {
                tag.setTag(STORAGE_CELL_CONFIG_INV, GenericStack.writeList(list));
            }

            inv.writeToChildTag(tag, STORAGE_CELL_CONFIG_INV_TAG);
            stack.setTagCompound(Platform.isNbtEmpty(tag) ? null : tag);
        }
    }
}
