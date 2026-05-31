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

package ae2.init;

import ae2.core.AELog;
import ae2.core.AppEngBase;
import ae2.core.stats.AdvancementTriggers;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("deprecation")
public final class InitAdvancementTriggers {
    private static boolean initialized;

    private InitAdvancementTriggers() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }

        Method register = ReflectionHelper.findMethod(CriteriaTriggers.class, "register", "func_192118_a",
            ICriterionTrigger.class);
        register.setAccessible(true);

        AdvancementTriggers triggers = new AdvancementTriggers(trigger -> {
            try {
                register.invoke(null, trigger);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                AELog.debug("Failed to register advancement trigger: %s", e);
            }
        });
        AppEngBase.runtime().setAdvancementTriggers(triggers);
        initialized = true;
    }
}
