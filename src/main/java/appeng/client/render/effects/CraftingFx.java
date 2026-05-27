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
package appeng.client.render.effects;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class CraftingFx extends Particle {

    private final int startBlockX;
    private final int startBlockY;
    private final int startBlockZ;

    public CraftingFx(World world, double x, double y, double z, double motionX, double motionY, double motionZ,
                      TextureAtlasSprite sprite) {
        super(world, x, y, z);
        this.particleRed = 1.0f;
        this.particleGreen = 0.9f;
        this.particleBlue = 1.0f;
        this.particleAlpha = 1.3f;
        this.particleScale = 1.5f;
        this.particleGravity = 0.0f;
        this.particleMaxAge = Math.max(1, (int) (this.particleMaxAge / 1.2D));
        this.canCollide = false;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        if (sprite != null) {
            this.setParticleTexture(sprite);
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
    public void renderParticle(BufferBuilder buffer, net.minecraft.entity.Entity entityIn, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        int blockX = MathHelper.floor(this.posX);
        int blockY = MathHelper.floor(this.posY);
        int blockZ = MathHelper.floor(this.posZ);
        if (blockX != this.startBlockX || blockY != this.startBlockY || blockZ != this.startBlockZ) {
            return;
        }

        float x = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - Particle.interpPosX);
        float y = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - Particle.interpPosY);
        float z = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - Particle.interpPosZ);
        float scale = 0.1F * this.particleScale;

        float minU = this.particleTexture.getMinU();
        float maxU = this.particleTexture.getMaxU();
        float minV = this.particleTexture.getMinV();
        float maxV = this.particleTexture.getMaxV();
        int lightmap = this.getBrightnessForRender(partialTicks);
        int sky = lightmap >> 16 & 0xFFFF;
        int block = lightmap & 0xFFFF;

        buffer.pos(x - rotationX * scale - rotationXY * scale, y - rotationZ * scale,
                  z - rotationYZ * scale - rotationXZ * scale)
              .tex(maxU, maxV)
              .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
              .lightmap(sky, block)
              .endVertex();
        buffer.pos(x - rotationX * scale + rotationXY * scale, y + rotationZ * scale,
                  z - rotationYZ * scale + rotationXZ * scale)
              .tex(maxU, minV)
              .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
              .lightmap(sky, block)
              .endVertex();
        buffer.pos(x + rotationX * scale + rotationXY * scale, y + rotationZ * scale,
                  z + rotationYZ * scale + rotationXZ * scale)
              .tex(minU, minV)
              .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
              .lightmap(sky, block)
              .endVertex();
        buffer.pos(x + rotationX * scale - rotationXY * scale, y - rotationZ * scale,
                  z + rotationYZ * scale - rotationXZ * scale)
              .tex(minU, maxV)
              .color(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha)
              .lightmap(sky, block)
              .endVertex();
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.particleAge++ >= this.particleMaxAge) {
            this.setExpired();
        }

        this.motionY -= 0.04D * this.particleGravity;
        this.move(this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.9800000190734863D;
        this.motionY *= 0.9800000190734863D;
        this.motionZ *= 0.9800000190734863D;
        this.particleScale *= 0.51f;
        this.particleAlpha *= 0.51f;
    }
}
