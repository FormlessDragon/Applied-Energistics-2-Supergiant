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
package ae2.client.render.effects;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.World;

public class VibrantFX extends Particle {
    private final boolean usesAtlasSprite;

    public VibrantFX(World world, double x, double y, double z, double motionX,
                     double motionY, double motionZ, TextureAtlasSprite sprite) {
        super(world, x, y, z, motionX, motionY, motionZ);
        final float f = this.rand.nextFloat() * 0.1F + 0.8F;
        this.particleRed = f * 0.7f;
        this.particleGreen = f * 0.89f;
        this.particleBlue = f * 0.9f;
        this.setSize(0.04F, 0.04F);
        this.particleScale *= this.rand.nextFloat() * 0.6F + 1.9F;
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.particleMaxAge = (int) (20.0D / (Math.random() * 0.8D + 0.1D));
        this.usesAtlasSprite = sprite != null;
        if (sprite != null) {
            this.setParticleTexture(sprite);
        } else {
            this.setParticleTextureIndex(0);
        }
    }

    @Override
    public int getFXLayer() {
        return this.usesAtlasSprite ? 1 : 0;
    }

    @Override
    public int getBrightnessForRender(float partialTick) {
        return 15 << 20 | 15 << 4;
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.particleScale *= 0.95f;

        if (this.particleMaxAge <= 0 || this.particleScale < 0.1f) {
            this.setExpired();
            return;
        }

        this.particleMaxAge--;
    }
}
