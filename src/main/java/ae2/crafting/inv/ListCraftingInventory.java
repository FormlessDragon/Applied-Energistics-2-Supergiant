/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package ae2.crafting.inv;

import ae2.api.config.Actionable;
import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import ae2.api.stacks.KeyCounter;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.NBTTagList;

import java.util.List;
import java.util.Map;

public class ListCraftingInventory implements ICraftingInventory {
    public final KeyCounter list = new KeyCounter();

    private final ChangeListener listener;

    public ListCraftingInventory(ChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void insert(AEKey what, long amount, Actionable mode) {
        if (mode == Actionable.MODULATE) {
            list.add(what, amount);
            listener.onChange(what);
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode) {
        var available = list.get(what);
        var extracted = Math.min(available, amount);
        if (mode == Actionable.MODULATE) {
            if (available > extracted) {
                list.remove(what, extracted);
            } else {
                list.remove(what);
            }
            listener.onChange(what);
        }
        return extracted;
    }

    @Override
    public Iterable<AEKey> findFuzzyTemplates(AEKey what) {
        return Iterables.transform(list.findFuzzy(what, FuzzyMode.IGNORE_ALL), Map.Entry::getKey);
    }

    public void clear() {
        for (var stack : list) {
            // First clear and then notify, so that if the listener queries the new amount in the change notification,
            // it will be 0 as it should be.
            list.set(stack.getKey(), 0);
            listener.onChange(stack.getKey());
        }
        list.removeZeros();
    }

    public void readFromNBT(NBTTagList data) {
        list.clear();

        if (data != null) {
            for (var stack : GenericStack.readList(data)) {
                if (stack != null) {
                    insert(stack.what(), stack.amount(), Actionable.MODULATE);
                }
            }
        }
    }

    public NBTTagList writeToNBT() {
        List<GenericStack> stacks = new ObjectArrayList<>(list.size());

        for (var entry : list) {
            stacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }

        return GenericStack.writeList(stacks);
    }

    @FunctionalInterface
    public interface ChangeListener {
        void onChange(AEKey key);
    }
}
