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

package ae2.init.internal;

import ae2.api.networking.GridServices;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.pathing.IPathingService;
import ae2.api.networking.spatial.ISpatialService;
import ae2.api.networking.storage.IStorageService;
import ae2.api.networking.ticking.ITickManager;
import ae2.me.service.CraftingService;
import ae2.me.service.EnergyService;
import ae2.me.service.P2PService;
import ae2.me.service.PathingService;
import ae2.me.service.SpatialPylonService;
import ae2.me.service.StatisticsService;
import ae2.me.service.StorageService;
import ae2.me.service.TickManagerService;

public final class InitGridServices {

    private InitGridServices() {
    }

    public static void init() {
        GridServices.register(ITickManager.class, TickManagerService.class);
        GridServices.register(IPathingService.class, PathingService.class);
        GridServices.register(IEnergyService.class, EnergyService.class);
        GridServices.register(IStorageService.class, StorageService.class);
        GridServices.register(P2PService.class, P2PService.class);
        GridServices.register(ISpatialService.class, SpatialPylonService.class);
        GridServices.register(ICraftingService.class, CraftingService.class);
        GridServices.register(StatisticsService.class, StatisticsService.class);
    }
}
