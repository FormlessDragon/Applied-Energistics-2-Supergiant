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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.worldgen.meteorite;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MeteoriteBlockPutter {
    public boolean put(final World world, final BlockPos pos, final BlockStateWrapper wrapper) {
        return this.put(world, pos, wrapper.state);
    }

    public void put(final World world, final BlockPos pos, final Block block) {
        final Block original = world.getBlockState(pos).getBlock();

        if (original == Blocks.BEDROCK || original == block) {
            return;
        }

        world.setBlockState(pos, block.getDefaultState());
    }

    public boolean put(final World world, final BlockPos pos, final IBlockState state) {
        if (world.getBlockState(pos).getBlock() == Blocks.BEDROCK) {
            return false;
        }

        world.setBlockState(pos, state, 3);
        return true;
    }

    public static final class BlockStateWrapper {
        private final IBlockState state;

        public BlockStateWrapper(IBlockState state) {
            this.state = state;
        }
    }
}
