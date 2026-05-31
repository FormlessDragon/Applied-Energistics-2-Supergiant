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

package ae2.util.fluid;

import ae2.api.stacks.AEFluidKey;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Helps with playing fill/empty sounds for fluids to players.
 */
public final class FluidSoundHelper {

    private FluidSoundHelper() {
    }

    public static void playFillSound(EntityPlayer player, @Nullable AEFluidKey fluid) {
        playSound(player, fluid, true);
    }

    public static void playEmptySound(EntityPlayer player, @Nullable AEFluidKey fluid) {
        playSound(player, fluid, false);
    }

    private static void playSound(EntityPlayer player, @Nullable AEFluidKey fluid, boolean fill) {
        if (fluid == null) {
            return;
        }

        FluidStack stack = fluid.toStack(1);
        Fluid rawFluid = stack.getFluid();
        SoundEvent sound = fill ? rawFluid.getFillSound(stack) : rawFluid.getEmptySound(stack);
        if (sound == null) {
            sound = fill ? SoundEvents.ITEM_BUCKET_FILL : SoundEvents.ITEM_BUCKET_EMPTY;
        }

        player.playSound(sound, 1.0F, 1.0F);
    }
}
