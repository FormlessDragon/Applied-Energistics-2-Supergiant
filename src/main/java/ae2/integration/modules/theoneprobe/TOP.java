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

package ae2.integration.modules.theoneprobe;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInterModComms;

public final class TOP {
    private static final String MOD_ID = "theoneprobe";
    private static final String MODULE_CLASS = "ae2.integration.modules.theoneprobe.TheOneProbeModule";

    private TOP() {
    }

    public static void enqueueIMC() {
        if (Loader.isModLoaded(MOD_ID)) {
            FMLInterModComms.sendFunctionMessage(MOD_ID, "getTheOneProbe", MODULE_CLASS);
        }
    }
}
