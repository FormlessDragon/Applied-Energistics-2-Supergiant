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

package ae2.me.energy;

import ae2.api.networking.energy.IEnergyWatcher;
import ae2.api.networking.energy.IEnergyWatcherNode;
import ae2.me.service.EnergyService;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Iterator;

/**
 * Maintain my interests, and a global watch list, they should always be fully synchronized.
 */
public class EnergyWatcher implements IEnergyWatcher {

    private final EnergyService service;
    private final IEnergyWatcherNode watcherHost;
    private final ObjectSet<EnergyThreshold> myInterests = new ObjectOpenHashSet<>();

    public EnergyWatcher(EnergyService service, IEnergyWatcherNode host) {
        this.service = service;
        this.watcherHost = host;
    }

    public void post(EnergyService service) {
        this.watcherHost.onThresholdPass(service);
    }

    public IEnergyWatcherNode getHost() {
        return this.watcherHost;
    }

    @Override
    public void add(double amount) {
        Preconditions.checkArgument(amount >= 0, "amount must be >= 0");

        final EnergyThreshold eh = new EnergyThreshold(amount, this);

        if (this.myInterests.contains(eh)) {
            return;
        }

        if (this.service.registerEnergyInterest(eh)) {
            this.myInterests.add(eh);
        }
    }

    @Override
    public boolean remove(double amount) {
        Preconditions.checkArgument(amount >= 0, "amount must be >= 0");

        final EnergyThreshold eh = new EnergyThreshold(amount, this);

        return this.myInterests.remove(eh) && this.service.unregisterEnergyInterest(eh);
    }

    @Override
    public void reset() {
        for (Iterator<EnergyThreshold> iterator = this.myInterests.iterator(); iterator.hasNext(); ) {
            final EnergyThreshold threshold = iterator.next();

            this.service.unregisterEnergyInterest(threshold);
            iterator.remove();
        }
    }

}
