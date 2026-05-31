/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.core.definitions;

import ae2.core.AppEng;
import ae2.core.AppEngBase;
import ae2.entity.EntityChargedQuartz;
import ae2.entity.EntitySingularity;
import ae2.entity.TinyTNTPrimedEntity;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import java.util.Map;

@SuppressWarnings("deprecation")
public final class AEEntities {

    public static final Map<String, String> ENTITY_ENGLISH_NAMES = new Object2ObjectOpenHashMap<>();

    private AEEntities() {
    }

    public static void init() {
        register();
    }

    private static void register() {
        ENTITY_ENGLISH_NAMES.put("tiny_tnt_primed", "Tiny TNT Primed");
        EntityRegistry.registerModEntity(new ResourceLocation(AppEng.MOD_ID, "tiny_tnt_primed"), TinyTNTPrimedEntity.class, "tiny_tnt_primed", 0,
            AppEngBase.INSTANCE, 16, 4, true);
        ENTITY_ENGLISH_NAMES.put("charged_quartz", "Charged Quartz");
        EntityRegistry.registerModEntity(new ResourceLocation(AppEng.MOD_ID, "charged_quartz"),
            EntityChargedQuartz.class, "Charged Quartz", 1, AppEngBase.INSTANCE, 16, 4, true);
        ENTITY_ENGLISH_NAMES.put("singularity", "Singularity");
        EntityRegistry.registerModEntity(new ResourceLocation(AppEng.MOD_ID, "singularity"),
            EntitySingularity.class, "Singularity", 2, AppEngBase.INSTANCE, 16, 4, true);
    }
}
