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

package ae2.me.helpers;

import ae2.api.stacks.AEKey;
import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;

import java.util.Collection;

public class InterestManager<T> {

    private final Multimap<AEKey, T> container;
    private final ReferenceSet<T> allStacksWatchers = new ReferenceOpenHashSet<>();

    public InterestManager(Multimap<AEKey, T> interests) {
        this.container = interests;
    }

    public void put(AEKey stack, T iw) {
        this.container.put(stack, iw);
    }

    public void remove(AEKey stack, T iw) {
        this.container.remove(stack, iw);
    }

    public void setWatchAll(boolean watchAll, T watcher) {
        if (watchAll) {
            allStacksWatchers.add(watcher);
        } else {
            allStacksWatchers.remove(watcher);
        }
    }

    public boolean containsKey(AEKey stack) {
        return this.container.containsKey(stack);
    }

    public Collection<T> get(AEKey stack) {
        return this.container.get(stack);
    }

    public Collection<T> getAllStacksWatchers() {
        return this.allStacksWatchers;
    }

    public boolean isEmpty() {
        return allStacksWatchers.isEmpty() && container.isEmpty();
    }
}
