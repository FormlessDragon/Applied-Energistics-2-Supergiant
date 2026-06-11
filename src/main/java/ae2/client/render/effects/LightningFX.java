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

public class LightningFX extends Particle {

    private static final int STEPS = 5;
    private static final int BRIGHTNESS = 13 << 4;

    private final float[][] precomputedSteps;
    private final float[] previousVertex = new float[3];
    private final float[] previousAnchor = new float[3];
    private boolean hasPreviousSegment;

    public LightningFX(World world, double x, double y, double z, double red, double green, double blue,
                       TextureAtlasSprite sprite) {
        this(world, x, y, z, red, green, blue, 6, sprite);
        this.regen();
    }

    protected LightningFX(World world, double x, double y, double z, double red, double green, double blue,
                          int maxAge, TextureAtlasSprite sprite) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.precomputedSteps = new float[STEPS][3];
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.particleGravity = 0.0f;
        this.canCollide = false;
        this.particleMaxAge = maxAge;
        this.particleRed = (float) (red <= 0.0D ? 1.0D : red);
        this.particleGreen = (float) (green <= 0.0D ? 1.0D : green);
        this.particleBlue = (float) (blue <= 0.0D ? 1.0D : blue);
        this.particleAlpha = 1.0f;
        if (sprite != null) {
            this.setParticleTexture(sprite);
        }
    }

    protected void regen() {
        float lastDirectionX = (this.rand.nextFloat() - 0.5f) * 0.9f;
        float lastDirectionY = (this.rand.nextFloat() - 0.5f) * 0.9f;
        float lastDirectionZ = (this.rand.nextFloat() - 0.5f) * 0.9f;
        for (int step = 0; step < STEPS; step++) {
            this.precomputedSteps[step][0] = lastDirectionX = (lastDirectionX + (this.rand.nextFloat() - 0.5f) * 0.9f)
                / 2.0f;
            this.precomputedSteps[step][1] = lastDirectionY = (lastDirectionY + (this.rand.nextFloat() - 0.5f) * 0.9f)
                / 2.0f;
            this.precomputedSteps[step][2] = lastDirectionZ = (lastDirectionZ + (this.rand.nextFloat() - 0.5f) * 0.9f)
                / 2.0f;
        }
    }

    protected int getSteps() {
        return STEPS;
    }

    protected float[][] getPrecomputedSteps() {
        return this.precomputedSteps;
    }

    @Override
    public int getFXLayer() {
        return 1;
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
    }

    @Override
    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks,
                               float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float centerX = (float) (this.prevPosX + (this.posX - this.prevPosX) * partialTicks - Particle.interpPosX);
        float centerY = (float) (this.prevPosY + (this.posY - this.prevPosY) * partialTicks - Particle.interpPosY);
        float centerZ = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * partialTicks - Particle.interpPosZ);

        if (this.particleAge == 3) {
            this.regen();
        }

        float u = this.particleTexture.getMinU() + (this.particleTexture.getMaxU() - this.particleTexture.getMinU()) / 2.0f;
        float v = this.particleTexture.getMinV() + (this.particleTexture.getMaxV() - this.particleTexture.getMinV()) / 2.0f;

        float[] a = new float[3];
        float[] b = new float[3];

        for (int layer = 0; layer < 2; layer++) {
            float scale = layer == 0 ? 0.04f : 0.02f;
            float red = layer == 0 ? this.particleRed * 0.4f : this.particleRed * 0.9f;
            float green = layer == 0 ? this.particleGreen * 0.25f : this.particleGreen * 0.65f;
            float blue = layer == 0 ? this.particleBlue * 0.45f : this.particleBlue * 0.85f;

            for (int cycle = 0; cycle < 3; cycle++) {
                this.hasPreviousSegment = false;

                float x = centerX;
                float y = centerY;
                float z = centerZ;

                for (int step = 0; step < this.getSteps(); step++) {
                    float xN = x + this.precomputedSteps[step][0];
                    float yN = y + this.precomputedSteps[step][1];
                    float zN = z + this.precomputedSteps[step][2];

                    float xD = xN - x;
                    float yD = yN - y;
                    float zD = zN - z;

                    float ox;
                    float oy;
                    float oz;

                    if (cycle == 0) {
                        ox = -zD;
                        oy = 0.0f;
                        oz = xD;
                    } else if (cycle == 1) {
                        ox = yD;
                        oy = -xD;
                        oz = 0.0f;
                    } else {
                        ox = 0.0f;
                        oy = zD;
                        oz = -yD;
                    }

                    float length = MathHelper.sqrt(ox * ox + oy * oy + oz * oz);
                    float divisor = (((float) this.getSteps() - (float) step) / (float) this.getSteps()) * scale;
                    if (length > 0.0001f && divisor > 0.0001f) {
                        float normalize = length / divisor;
                        ox /= normalize;
                        oy /= normalize;
                        oz /= normalize;
                    }

                    a[0] = x + ox;
                    a[1] = y + oy;
                    a[2] = z + oz;

                    b[0] = x;
                    b[1] = y;
                    b[2] = z;

                    draw(red, green, blue, buffer, a, b, u, v);

                    x = xN;
                    y = yN;
                    z = zN;
                }
            }
        }
    }

    private void draw(float red, float green, float blue, BufferBuilder buffer, float[] currentVertex,
                      float[] currentAnchor, float u, float v) {
        if (this.hasPreviousSegment) {
            buffer.pos(currentVertex[0], currentVertex[1], currentVertex[2])
                  .tex(u, v)
                  .color(red, green, blue, this.particleAlpha)
                  .lightmap(BRIGHTNESS, BRIGHTNESS)
                  .endVertex();
            buffer.pos(this.previousVertex[0], this.previousVertex[1], this.previousVertex[2])
                  .tex(u, v)
                  .color(red, green, blue, this.particleAlpha)
                  .lightmap(BRIGHTNESS, BRIGHTNESS)
                  .endVertex();
            buffer.pos(this.previousAnchor[0], this.previousAnchor[1], this.previousAnchor[2])
                  .tex(u, v)
                  .color(red, green, blue, this.particleAlpha)
                  .lightmap(BRIGHTNESS, BRIGHTNESS)
                  .endVertex();
            buffer.pos(currentAnchor[0], currentAnchor[1], currentAnchor[2])
                  .tex(u, v)
                  .color(red, green, blue, this.particleAlpha)
                  .lightmap(BRIGHTNESS, BRIGHTNESS)
                  .endVertex();
        }

        this.hasPreviousSegment = true;
        System.arraycopy(currentVertex, 0, this.previousVertex, 0, 3);
        System.arraycopy(currentAnchor, 0, this.previousAnchor, 0, 3);
    }
}
