/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2017, AlgorithmX2, All rights reserved.
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

import ae2.api.inventories.BaseInternalInventory;
import ae2.api.inventories.InternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import net.minecraft.item.ItemStack;

import java.util.Objects;

public class FilteredInternalInventory extends BaseInternalInventory {
    private final InternalInventory delegate;
    private final IAEItemFilter filter;

    public FilteredInternalInventory(InternalInventory delegate, IAEItemFilter filter) {
        this.delegate = Objects.requireNonNull(delegate);
        this.filter = Objects.requireNonNull(filter);
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        delegate.setItemDirect(slot, stack);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!this.filter.allowInsert(this.delegate, slot, stack)) {
            return stack;
        }
        return this.delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!this.filter.allowExtract(this.delegate, slot, amount)) {
            return ItemStack.EMPTY;
        }
        return this.delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!this.filter.allowInsert(this.delegate, slot, stack)) {
            return false;
        }
        return this.delegate.isItemValid(slot, stack);
    }

    @Override
    public void sendChangeNotification(int slot) {
        delegate.sendChangeNotification(slot);
    }
}
