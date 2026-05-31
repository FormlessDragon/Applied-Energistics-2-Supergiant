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

package ae2.client.gui.style;

import ae2.api.stacks.AEFluidKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Creates a {@link Blitter} to draw fluids into the user interface.
 */
public final class FluidBlitter {

    private FluidBlitter() {
    }

    public static Blitter create(AEFluidKey fluidKey) {
        return create(fluidKey.toStack(1));
    }

    public static Blitter create(FluidStack stack) {
        TextureAtlasSprite sprite = getStillSprite(stack);
        Fluid fluid = stack.getFluid();
        return Blitter.sprite(sprite)
                      .colorRgb(fluid.getColor(stack))
                      .blending(false);
    }

    public static TextureAtlasSprite getStillSprite(FluidStack stack) {
        if (stack == null || stack.getFluid() == null) {
            throw new IllegalArgumentException("stack");
        }

        if (stack.amount <= 0) {
            stack = stack.copy();
            stack.amount = 1;
        }

        Fluid fluid = stack.getFluid();
        var still = fluid.getStill(stack);
        if (still == null) {
            still = fluid.getStill();
        }

        return Minecraft.getMinecraft()
                        .getTextureMapBlocks()
                        .getAtlasSprite(still.toString());
    }
}
