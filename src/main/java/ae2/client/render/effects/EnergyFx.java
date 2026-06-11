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
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class EnergyFx extends Particle {

    private final int startBlockX;
    private final int startBlockY;
    private final int startBlockZ;

    public EnergyFx(World world, double x, double y, double z, double motionX, double motionY, double motionZ,
                    TextureAtlasSprite sprite, EnergyParticleData data) {
        super(world, x, y, z);

        this.particleRed = 1.0f;
        this.particleGreen = 1.0f;
        this.particleBlue = 1.0f;
        this.particleAlpha = 1.4f;
        this.particleScale *= 3.5f;
        this.particleGravity = 0.0f;
        this.canCollide = false;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.particleMaxAge = Math.max(16, (int) (this.particleMaxAge * 1.8f));
        if (sprite != null) {
            this.setParticleTexture(sprite);
        }

        if (data.forItem()) {
            this.posX += -0.2 * data.direction().getXOffset();
            this.posY += -0.2 * data.direction().getYOffset();
            this.posZ += -0.2 * data.direction().getZOffset();
            this.particleScale *= 0.8f;
        }

        this.startBlockX = MathHelper.floor(this.posX);
        this.startBlockY = MathHelper.floor(this.posY);
        this.startBlockZ = MathHelper.floor(this.posZ);
    }

    @Override
    public int getFXLayer() {
        return 1;
    }

    @Override
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        int blockX = MathHelper.floor(this.posX);
        int blockY = MathHelper.floor(this.posY);
        int blockZ = MathHelper.floor(this.posZ);

        if (blockX == this.startBlockX && blockY == this.startBlockY && blockZ == this.startBlockZ) {
            super.renderParticle(buffer, entityIn, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY,
                rotationXZ);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.onGround = false;
        this.particleScale *= 0.94f;
        this.particleAlpha *= 0.94f;
    }
}
