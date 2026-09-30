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

package ae2.crafting;

import ae2.api.networking.crafting.ICraftingProvider;

import java.util.List;

/**
 * A crafting plan that carries transient crafting providers which are not part of the network's pattern registry.
 * <p>
 * A batch order of the crafting terminal puts the placeholder provider of its faked recipe output into the plan. The
 * crafting CPU has to register such providers before it can push their patterns, and the plan display has to know their
 * outputs to hide them. Plans without transient providers report an empty list.
 */
public interface TemporaryProviderCarrier {
    /**
     * The providers a calculation injected itself, in the order they should be registered.
     */
    List<ICraftingProvider> temporaryProviders();
}
