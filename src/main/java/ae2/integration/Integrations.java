/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2020, AlgorithmX2, All rights reserved.
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

package ae2.integration;

import ae2.integration.abstraction.HeiAdapter;
import ae2.integration.abstraction.IIC2;
import ae2.integration.abstraction.IInvTweaks;
import ae2.integration.modules.hei.HeiModule;
import ae2.integration.modules.ic2.IC2Module;
import ae2.integration.modules.inventorytweaks.InventoryTweaksModule;
import ae2.integration.modules.theoneprobe.TOP;
import ae2.integration.modules.waila.Waila;
import net.minecraftforge.fml.common.Loader;

public final class Integrations {
    private static final IInvTweaks NO_INV_TWEAKS = new IInvTweaks() {
    };
    private static final HeiAdapter NO_HEI = HeiAdapter.none();
    private static final IIC2 NO_IC2 = new IIC2() {
    };
    private static IInvTweaks invTweaks = NO_INV_TWEAKS;
    private static HeiAdapter hei = NO_HEI;
    private static IIC2 ic2 = NO_IC2;

    private Integrations() {
    }

    public static IInvTweaks invTweaks() {
        return invTweaks;
    }

    public static HeiAdapter hei() {
        return hei;
    }

    public static IIC2 ic2() {
        return ic2;
    }

    public static void enqueueIMC() {
        TOP.enqueueIMC();
        Waila.enqueueIMC();
    }

    public static void initOptionalIntegrations() {
        invTweaks = Loader.isModLoaded("inventorytweaks") ? new InventoryTweaksModule() : NO_INV_TWEAKS;
        hei = Loader.isModLoaded("jei") ? new HeiModule() : NO_HEI;
        ic2 = Loader.isModLoaded("ic2") ? new IC2Module() : NO_IC2;
    }

}
