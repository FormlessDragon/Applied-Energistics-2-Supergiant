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

package ae2.util.helpers;

import ae2.api.config.FuzzyMode;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

public final class ItemComparisonHelper {

    private ItemComparisonHelper() {
    }

    public static boolean isEqualItemType(ItemStack that, ItemStack other) {
        if (!that.isEmpty() && !other.isEmpty() && that.getItem() == other.getItem()) {
            if (that.isItemStackDamageable()) {
                return true;
            }
            return that.getItemDamage() == other.getItemDamage();
        }
        return false;
    }

    public static boolean isSameItem(ItemStack left, ItemStack right) {
        return ItemStack.areItemsEqual(left, right) && isNbtTagEqual(left.getTagCompound(), right.getTagCompound());
    }

    public static boolean isFuzzyEqualItem(ItemStack a, ItemStack b, FuzzyMode mode) {
        if (a.isEmpty() && b.isEmpty()) {
            return true;
        }

        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }

        if (a.getItem() == b.getItem() && a.getItem().isDamageable()) {
            if (mode == FuzzyMode.IGNORE_ALL) {
                return true;
            } else if (mode == FuzzyMode.PERCENT_99) {
                return (a.getItemDamage() > 0) == (b.getItemDamage() > 0);
            } else {
                float percentDamagedOfA = (float) a.getItemDamage() / (float) a.getMaxDamage();
                float percentDamagedOfB = (float) b.getItemDamage() / (float) b.getMaxDamage();
                return (percentDamagedOfA > mode.breakPoint) == (percentDamagedOfB > mode.breakPoint);
            }
        }

        return a.isItemEqual(b);
    }

    public static boolean isNbtTagEqual(@Nullable NBTTagCompound left, @Nullable NBTTagCompound right) {
        if (left == right) {
            return true;
        }

        boolean isLeftEmpty = left == null || left.isEmpty();
        boolean isRightEmpty = right == null || right.isEmpty();

        if (isLeftEmpty && isRightEmpty) {
            return true;
        }

        if (isLeftEmpty != isRightEmpty) {
            return false;
        }

        return left.equals(right);
    }
}
