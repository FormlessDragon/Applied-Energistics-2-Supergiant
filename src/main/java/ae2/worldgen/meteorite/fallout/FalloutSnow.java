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

package ae2.worldgen.meteorite.fallout;

import ae2.worldgen.meteorite.MeteoriteBlockPutter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

public class FalloutSnow extends FalloutCopy {
    private static final float SNOW_THRESHOLD = 0.7f;
    private static final float ICE_THRESHOLD = 0.5f;
    private final MeteoriteBlockPutter putter;

    public FalloutSnow(World world, BlockPos pos, MeteoriteBlockPutter putter, IBlockState skyStone,
                       Random random) {
        super(world, pos, putter, skyStone, random);
        this.putter = putter;
    }

    @Override
    public int adjustCrater() {
        return 2;
    }

    @Override
    public void getOther(World world, BlockPos pos, float a) {
        if (a > SNOW_THRESHOLD) {
            this.putter.put(world, pos, Blocks.SNOW.getDefaultState());
        } else if (a > ICE_THRESHOLD) {
            this.putter.put(world, pos, Blocks.ICE.getDefaultState());
        }
    }
}
