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

package ae2.client.render.cablebus;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.particle.ParticleDigging;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CableBusBreakingParticle extends ParticleDigging {

    public CableBusBreakingParticle(World world, double x, double y, double z, double speedX, double speedY,
                                    double speedZ, IBlockState state, TextureAtlasSprite sprite) {
        super(world, x, y, z, speedX, speedY, speedZ, state);
        this.setParticleTexture(sprite);
        this.particleGravity = 1.0F;
        this.particleScale /= 2.0F;
    }

    public CableBusBreakingParticle(World world, double x, double y, double z, IBlockState state,
                                    TextureAtlasSprite sprite) {
        this(world, x, y, z, 0.0D, 0.0D, 0.0D, state, sprite);
    }

    public CableBusBreakingParticle scale(float scale) {
        this.particleScale *= scale;
        return this;
    }
}
