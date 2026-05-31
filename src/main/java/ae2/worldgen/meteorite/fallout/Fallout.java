/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
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

public class Fallout {
    protected final MeteoriteBlockPutter putter;
    protected final IBlockState skyStone;
    protected final Random random;

    public Fallout(MeteoriteBlockPutter putter, IBlockState skyStone, Random random) {
        this.putter = putter;
        this.skyStone = skyStone;
        this.random = random;
    }

    public int adjustCrater() {
        return 0;
    }

    public void getRandomFall(World world, BlockPos pos) {
        float a = random.nextFloat();
        if (a > 0.9f) {
            this.putter.put(world, pos, Blocks.STONE);
        } else if (a > 0.8f) {
            this.putter.put(world, pos, Blocks.COBBLESTONE);
        } else if (a > 0.7f) {
            this.putter.put(world, pos, Blocks.DIRT);
        } else {
            this.putter.put(world, pos, Blocks.GRAVEL);
        }
    }

    public void getRandomInset(World world, BlockPos pos) {
        float a = random.nextFloat();
        if (a > 0.9f) {
            this.putter.put(world, pos, Blocks.COBBLESTONE);
        } else if (a > 0.8f) {
            this.putter.put(world, pos, Blocks.STONE);
        } else if (a > 0.7f) {
            this.putter.put(world, pos, Blocks.GRASS);
        } else if (a > 0.6f) {
            this.putter.put(world, pos, this.skyStone);
        } else if (a > 0.5f) {
            this.putter.put(world, pos, Blocks.GRAVEL);
        } else {
            this.putter.put(world, pos, Blocks.AIR);
        }
    }
}
