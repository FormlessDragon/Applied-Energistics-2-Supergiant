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

package ae2.util.prioritylist;

import ae2.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

import java.util.Collection;

public final class MergedPriorityList implements IPartitionList {

    private final Collection<IPartitionList> positive = new ObjectArrayList<>();
    private final Collection<IPartitionList> negative = new ObjectArrayList<>();

    public void addNewList(IPartitionList list, boolean isWhitelist) {
        if (isWhitelist) {
            this.positive.add(list);
        } else {
            this.negative.add(list);
        }
    }

    @Override
    public boolean isListed(AEKey input) {
        for (IPartitionList list : this.negative) {
            if (list.isListed(input)) {
                return false;
            }
        }

        if (!this.positive.isEmpty()) {
            for (IPartitionList list : this.positive) {
                if (list.isListed(input)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    @Override
    public boolean isEmpty() {
        return this.positive.isEmpty() && this.negative.isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        ObjectLinkedOpenHashSet<AEKey> items = new ObjectLinkedOpenHashSet<>();

        for (IPartitionList list : this.positive) {
            for (AEKey item : list.getItems()) {
                items.add(item);
            }
        }

        for (IPartitionList list : this.negative) {
            for (AEKey item : list.getItems()) {
                items.add(item);
            }
        }

        return items;
    }
}
