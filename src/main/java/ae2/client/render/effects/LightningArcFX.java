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

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class LightningArcFX extends LightningFX {

    private final double rx;
    private final double ry;
    private final double rz;

    public LightningArcFX(World world, double x, double y, double z, double targetX, double targetY, double targetZ,
                          double red, double green, double blue, TextureAtlasSprite sprite) {
        super(world, x, y, z, red, green, blue, 6, sprite);
        this.rx = targetX - x;
        this.ry = targetY - y;
        this.rz = targetZ - z;
        this.regen();
    }

    @Override
    protected void regen() {
        float stepScale = 1.0f / (this.getSteps() - 1);
        float stepX = (float) this.rx * stepScale;
        float stepY = (float) this.ry * stepScale;
        float stepZ = (float) this.rz * stepScale;

        float len = MathHelper.sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ);
        float[][] steps = this.getPrecomputedSteps();

        for (int step = 0; step < this.getSteps(); step++) {
            steps[step][0] = (stepX + (this.rand.nextFloat() - 0.5f) * len * 1.2f) / 2.0f;
            steps[step][1] = (stepY + (this.rand.nextFloat() - 0.5f) * len * 1.2f) / 2.0f;
            steps[step][2] = (stepZ + (this.rand.nextFloat() - 0.5f) * len * 1.2f) / 2.0f;
        }
    }
}
