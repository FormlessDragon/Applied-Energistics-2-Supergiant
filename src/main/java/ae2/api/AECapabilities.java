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

package ae2.api;

import ae2.api.behaviors.GenericInternalInventory;
import ae2.api.implementations.blockentities.ICraftingMachine;
import ae2.api.implementations.blockentities.ICrankable;
import ae2.api.networking.IInWorldGridNodeHost;
import ae2.api.storage.MEStorage;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;

public final class AECapabilities {
    @CapabilityInject(MEStorage.class)
    public static Capability<MEStorage> ME_STORAGE;
    @CapabilityInject(ICraftingMachine.class)
    public static Capability<ICraftingMachine> CRAFTING_MACHINE;
    @CapabilityInject(GenericInternalInventory.class)
    public static Capability<GenericInternalInventory> GENERIC_INTERNAL_INV;
    @CapabilityInject(ICrankable.class)
    public static Capability<ICrankable> CRANKABLE;
    @CapabilityInject(IInWorldGridNodeHost.class)
    public static Capability<IInWorldGridNodeHost> IN_WORLD_GRID_NODE_HOST;

    private AECapabilities() {
    }
}
