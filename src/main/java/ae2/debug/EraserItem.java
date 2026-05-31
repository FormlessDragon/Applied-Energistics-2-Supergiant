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

package ae2.debug;

import ae2.core.AELog;
import ae2.items.AEBaseItem;
import ae2.util.InteractionUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

public class EraserItem extends AEBaseItem {

    private static final int BOX_SIZE = 48;
    private static final int BLOCK_ERASE_LIMIT = BOX_SIZE * BOX_SIZE * BOX_SIZE;
    private static final Set<Block> COMMON_BLOCKS = new ObjectOpenHashSet<>();

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }

        Block state = world.getBlockState(pos).getBlock();
        boolean bulk = InteractionUtil.isInAlternateUseMode(player);
        Queue<BlockPos> next = new ArrayDeque<>();
        Set<BlockPos> closed = new ObjectOpenHashSet<>();
        Set<Block> commonBlocks = this.getCommonBlocks();

        next.add(pos);
        int blocks = 0;

        while (blocks < BLOCK_ERASE_LIMIT && next.peek() != null) {
            BlockPos currentPos = next.poll();
            Block currentState = world.getBlockState(currentPos).getBlock();
            boolean contains = state == currentState || bulk && commonBlocks.contains(currentState);

            closed.add(currentPos);

            if (contains) {
                blocks++;
                world.setBlockToAir(currentPos);

                if (isInsideBox(currentPos, pos)) {
                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -1; z <= 1; z++) {
                                if (x == 0 && y == 0 && z == 0) {
                                    continue;
                                }
                                BlockPos nextPos = currentPos.add(x, y, z);
                                if (!closed.contains(nextPos)) {
                                    next.add(nextPos);
                                }
                            }
                        }
                    }
                }
            }
        }

        AELog.info("Delete " + blocks + " blocks");
        return EnumActionResult.SUCCESS;
    }

    private boolean isInsideBox(BlockPos pos, BlockPos origin) {
        return pos.getX() <= origin.getX() + BOX_SIZE && pos.getX() >= origin.getX() - BOX_SIZE
            && pos.getY() <= origin.getY() + BOX_SIZE && pos.getY() >= origin.getY() - BOX_SIZE
            && pos.getZ() <= origin.getZ() + BOX_SIZE && pos.getZ() >= origin.getZ() - BOX_SIZE;
    }

    private Set<Block> getCommonBlocks() {
        if (COMMON_BLOCKS.isEmpty()) {
            COMMON_BLOCKS.add(Blocks.STONE);
            COMMON_BLOCKS.add(Blocks.DIRT);
            COMMON_BLOCKS.add(Blocks.GRASS);
            COMMON_BLOCKS.add(Blocks.COBBLESTONE);
            COMMON_BLOCKS.add(Blocks.GRAVEL);
            COMMON_BLOCKS.add(Blocks.SANDSTONE);
            COMMON_BLOCKS.add(Blocks.NETHERRACK);
            COMMON_BLOCKS.add(Blocks.WATER);
            COMMON_BLOCKS.add(Blocks.LAVA);
        }

        return COMMON_BLOCKS;
    }
}
