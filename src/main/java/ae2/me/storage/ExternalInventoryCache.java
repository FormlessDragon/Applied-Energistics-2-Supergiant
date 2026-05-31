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

package ae2.me.storage;

import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Arrays;
import java.util.Set;

class ExternalInventoryCache {
    private final ExternalStorageFacade facade;
    private GenericStack[] cached = new GenericStack[0];

    private ExternalInventoryCache(ExternalStorageFacade facade) {
        this.facade = facade;
    }

    public static ExternalInventoryCache of(ExternalStorageFacade facade) {
        return new ExternalInventoryCache(facade);
    }

    public void getAvailableItems(KeyCounter out) {
        for (GenericStack stack : cached) {
            if (stack != null) {
                out.add(stack.what(), stack.amount());
            }
        }
    }

    public Set<AEKey> update() {
        var changes = new ObjectOpenHashSet<AEKey>();
        final int slots = this.facade.getSlots();

        if (slots > this.cached.length) {
            this.cached = Arrays.copyOf(this.cached, slots);
        }

        for (int slot = 0; slot < slots; slot++) {
            var oldGenericStack = this.cached[slot];
            var newGenericStack = facade.getStackInSlot(slot);

            this.handlePossibleSlotChanges(slot, oldGenericStack, newGenericStack, changes);
        }

        if (slots < this.cached.length) {
            for (int slot = slots; slot < this.cached.length; slot++) {
                final GenericStack aeStack = this.cached[slot];

                if (aeStack != null) {
                    changes.add(aeStack.what());
                }
            }

            this.cached = Arrays.copyOf(this.cached, slots);
        }

        return changes;
    }

    private void handlePossibleSlotChanges(int slot, GenericStack oldStack, GenericStack newStack, Set<AEKey> changes) {
        if (oldStack != null && newStack != null && oldStack.what().equals(newStack.what())) {
            handleAmountChanged(slot, oldStack, newStack, changes);
        } else {
            handleItemChanged(slot, oldStack, newStack, changes);
        }
    }

    private void handleAmountChanged(int slot, GenericStack oldStack, GenericStack newStack, Set<AEKey> changes) {
// Still the same item, but amount might have changed
        if (newStack.amount() != oldStack.amount()) {
// Completely different item
            this.cached[slot] = newStack;
            changes.add(newStack.what());
        }
    }

    private void handleItemChanged(int slot, GenericStack oldStack, GenericStack newStack, Set<AEKey> changes) {
        this.cached[slot] = newStack;

        if (oldStack != null) {
            changes.add(oldStack.what());
        }

        if (newStack != null) {
            changes.add(newStack.what());
        }
    }

}
