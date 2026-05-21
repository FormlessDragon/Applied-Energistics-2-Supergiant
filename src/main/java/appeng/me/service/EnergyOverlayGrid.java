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

package appeng.me.service;

import appeng.api.networking.energy.IPassiveEnergyGenerator;
import appeng.core.AELog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;

/**
 * This class caches all energy services that are part of the overlay energy grid. This overlay grid can span multiple
 * normal {@linkplain appeng.me.Grid grids} if they are connected by {@link appeng.parts.networking.QuartzFiberPart
 * quartz fibers}.
 */
class EnergyOverlayGrid {
    /**
     * Prefer grids with high energy storage for operations by sorting them to the front of the list.
     */
    private static final Comparator<EnergyService> SERVICE_COMPARATOR = Comparator
        .comparingDouble(EnergyService::getMaxStoredPower)
        .reversed();

    final EnergyService[] energyServices;

    /**
     * Which passive energy generator is currently active.
     */
    @Nullable
    private IPassiveEnergyGenerator currentPassiveGenerator;

    private EnergyOverlayGrid(EnergyService[] energyServices) {
        this.energyServices = energyServices;
    }

    /**
     * Build a new overlay energy grid by discovering all accessible {@linkplain EnergyService energy services} starting
     * with the given grid.
     */
    static void buildCache(EnergyService startingService) {
        var connectedServices = new ReferenceOpenHashSet<EnergyService>();

        // Walk graph
        var services = new ObjectArrayList<EnergyService>();
        services.add(startingService);

        while (!services.isEmpty()) {
            var service = services.pop();

            // If the service was not already processed, add it and also discover other services
            // reachable via its grid.
            if (connectedServices.add(service)) {
                for (var provider : service.getOverlayGridConnections()) {
                    services.addAll(provider.connectedEnergyServices());
                }
            }
        }

        // Sort services by capacity
        EnergyService[] sortedServices = connectedServices.toArray(EnergyService[]::new);
        Arrays.sort(sortedServices, SERVICE_COMPARATOR);
        var overlayGrid = new EnergyOverlayGrid(sortedServices);

        // Associate all grids that are part of the overlay grid with this instance
        for (var service : sortedServices) {
            // A previous overlay grid should have been invalidated before building a new one
            if (service.overlayGrid != null) {
                AELog.error("Grid %s energy service already has a power graph assigned to it!", service.grid);
            }

            service.overlayGrid = overlayGrid;
        }
    }

    void invalidate() {
        currentPassiveGenerator = null;
        for (var service : energyServices) {
            service.overlayGrid = null;
        }
    }

    @Nullable
    public IPassiveEnergyGenerator getCurrentPassiveGenerator() {
        return currentPassiveGenerator;
    }

    public void setCurrentPassiveGenerator(@Nullable IPassiveEnergyGenerator currentPassiveGenerator) {
        this.currentPassiveGenerator = currentPassiveGenerator;
    }

    public boolean hasCreativePowerSource() {
        for (var service : energyServices) {
            if (service.hasCreativeEnergyCell()) {
                return true;
            }
        }
        return false;
    }
}
