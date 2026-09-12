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

package ae2.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Reorders an already sorted list so that elements sharing a group id become contiguous.
 * <p/>
 * This exists so the crafting status CPU list can be grouped identically on the server and on the client. Both sides
 * sort first and then call this, which keeps grouping orthogonal to the active sort mode.
 */
public final class ContiguousGrouping {

    private ContiguousGrouping() {
    }

    /**
     * Collapses each group of elements to the list position of its first member, keeping ungrouped elements where
     * they are.
     * <p/>
     * Grouping is applied on top of the incoming order rather than replacing it: a group takes the slot of whichever
     * member came first, and members are then ordered by {@code memberOrdinalOf}. Elements whose group id is null are
     * left untouched. The sort within a group is stable, so members with equal ordinals keep their incoming relative
     * order.
     *
     * @param sorted           the list in its already sorted order. Not modified.
     * @param groupIdOf        returns the group id of an element, or null if it is ungrouped.
     * @param memberOrdinalOf  returns the intra-group ordering key of an element.
     * @return a new list with the same elements, grouped. Returns {@code sorted} itself when nothing is grouped.
     */
    public static <T> List<T> groupContiguously(List<T> sorted,
                                                Function<T, ResourceLocation> groupIdOf,
                                                ToIntFunction<T> memberOrdinalOf) {
        if (sorted.size() < 2) {
            return sorted;
        }

        // Bucket members by group, preserving the order in which groups were first seen.
        Map<ResourceLocation, ObjectArrayList<T>> groups = null;
        for (T element : sorted) {
            ResourceLocation groupId = groupIdOf.apply(element);
            if (groupId == null) {
                continue;
            }
            if (groups == null) {
                groups = new Object2ObjectLinkedOpenHashMap<>();
            }
            groups.computeIfAbsent(groupId, ignored -> new ObjectArrayList<>()).add(element);
        }

        // Nothing grouped, or every grouped element is alone in its group: the incoming order already satisfies us.
        if (groups == null || groups.values().stream().allMatch(members -> members.size() < 2)) {
            return sorted;
        }

        Comparator<T> memberComparator = Comparator.comparingInt(memberOrdinalOf);
        for (ObjectArrayList<T> members : groups.values()) {
            if (members.size() > 1) {
                members.sort(memberComparator);
            }
        }

        ObjectArrayList<T> result = new ObjectArrayList<>(sorted.size());
        for (T element : sorted) {
            ResourceLocation groupId = groupIdOf.apply(element);
            if (groupId == null) {
                result.add(element);
                continue;
            }

            // Emit the whole group at the position of its first member, then skip its remaining members.
            List<T> members = groups.remove(groupId);
            if (members != null) {
                result.addAll(members);
            }
        }
        return result;
    }
}
