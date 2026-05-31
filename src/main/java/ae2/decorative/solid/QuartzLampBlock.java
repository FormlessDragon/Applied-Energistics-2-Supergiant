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

package ae2.decorative.solid;

import ae2.client.EffectType;
import ae2.core.AEConfig;
import ae2.core.AppEngBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

public class QuartzLampBlock extends QuartzGlassBlock {

    public QuartzLampBlock() {
        super();
        this.setLightLevel(1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World level, BlockPos pos, Random random) {
        if (!AEConfig.instance().isEnableEffects()) {
            return;
        }

        if (AppEngBase.runtime().shouldAddParticles(random)) {
            final double d0 = (random.nextFloat() - 0.5F) * 0.96D;
            final double d1 = (random.nextFloat() - 0.5F) * 0.96D;
            final double d2 = (random.nextFloat() - 0.5F) * 0.96D;

            AppEngBase.runtime().spawnEffect(EffectType.Vibrant, level,
                0.5 + pos.getX() + d0,
                0.5 + pos.getY() + d1,
                0.5 + pos.getZ() + d2,
                null);
        }
    }
}
