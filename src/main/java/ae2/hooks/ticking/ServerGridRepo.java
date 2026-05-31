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
package ae2.hooks.ticking;

import ae2.me.Grid;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Collections;
import java.util.Set;

class ServerGridRepo {
    private final ObjectSet<Grid> networks = new ObjectOpenHashSet<>();
    private final ObjectSet<Grid> toAdd = new ObjectOpenHashSet<>();
    private final ObjectSet<Grid> toRemove = new ObjectOpenHashSet<>();

    void clear() {
        this.networks.clear();
        this.toAdd.clear();
        this.toRemove.clear();
    }

    synchronized void addNetwork(Grid grid) {
        this.toAdd.add(grid);
        this.toRemove.remove(grid);
    }

    synchronized void removeNetwork(Grid grid) {
        this.toRemove.add(grid);
        this.toAdd.remove(grid);
    }

    synchronized void updateNetworks() {
        this.networks.removeAll(this.toRemove);
        this.toRemove.clear();

        this.networks.addAll(this.toAdd);
        this.toAdd.clear();
    }

    public Set<Grid> getNetworks() {
        return Collections.unmodifiableSet(this.networks);
    }
}
