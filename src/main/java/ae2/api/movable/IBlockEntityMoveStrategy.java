/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package ae2.api.movable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A strategy for moving tile entities in and out of spatial storage.
 */
public interface IBlockEntityMoveStrategy {

    /**
     * Tests if this strategy is capable of moving the given tile entity type.
     */
    boolean canHandle(Class<? extends TileEntity> type);

    /**
     * Called to begin moving a tile entity.
     *
     * @param tileEntity The tile entity to move.
     * @return The saved representation of the tile entity that can be used by this strategy to restore the tile
     * entity at the target position. Return null to prevent the tile entity from being moved.
     */
    @Nullable
    NBTTagCompound beginMove(TileEntity tileEntity);

    /**
     * Complete moving a tile entity for which a move was initiated successfully with
     * {@link #beginMove(TileEntity)}. The tile entity has already been invalidated, and the
     * blocks have already been fully moved.
     * <p/>
     * You are responsible for adding the new tile entity to the target level, i.e. using
     * {@link World#setTileEntity(BlockPos, TileEntity)}.
     *
     * @param entity      The tile entity being moved, which has already been removed from the original chunk and
     *                    should not be reused.
     * @param state       The original block state of the tile entity being moved.
     * @param savedData   Data saved by this strategy in {@link #beginMove(TileEntity)}.
     * @param newLevel    Level to moved to
     * @param newPosition Position to move to
     * @return True if moving succeeded. If false is returned, AE2 will attempt to recover the original entity.
     */
    boolean completeMove(TileEntity entity, IBlockState state, NBTTagCompound savedData, World newLevel,
                         BlockPos newPosition);

}
