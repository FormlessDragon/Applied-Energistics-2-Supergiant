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

package ae2.init.client;

import ae2.client.render.effects.ParticleTypes;
import ae2.core.AppEng;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;

public final class InitParticleTypes {
    public static final ResourceLocation CRAFTING_ID = id("crafting_fx");
    public static final ResourceLocation ENERGY_ID = id("energy_fx");
    public static final ResourceLocation LIGHTNING_ARC_ID = id("lightning_arc_fx");
    public static final ResourceLocation LIGHTNING_ID = id("lightning_fx");
    public static final ResourceLocation MATTER_CANNON_ID = id("matter_cannon_fx");
    public static final ResourceLocation VIBRANT_ID = id("vibrant_fx");

    private InitParticleTypes() {
    }

    public static void init() {
        ParticleTypes.clearCachedSprites();
    }

    public static void registerTextures(TextureMap textureMap) {
        ParticleTypes.clearCachedSprites();
        ParticleTypes.registerTextures(textureMap);
    }

    private static ResourceLocation id(String name) {
        return AppEng.makeId(name);
    }
}

