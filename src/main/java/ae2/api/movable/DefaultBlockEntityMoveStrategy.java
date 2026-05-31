/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
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
 * The default strategy for moving tile entities in/out of spatial storage. Can be extended to create custom logic that
 * runs after {@link #completeMove} or prevents moving specific entities in {@link IBlockEntityMoveStrategy#beginMove}
 * by returning null.
 * <p/>
 * The default strategy uses {@link TileEntity#writeToNBT(NBTTagCompound)} in
 * {@link IBlockEntityMoveStrategy#beginMove} to persist the tile entity data before it is removed, and then creates a
 * new tile entity at the target position using
 * {@link TileEntity#create(World, NBTTagCompound)} in {@link #completeMove}.
 */
public abstract class DefaultBlockEntityMoveStrategy implements IBlockEntityMoveStrategy {

    @Nullable
    @Override
    public NBTTagCompound beginMove(TileEntity tileEntity) {
        return tileEntity.writeToNBT(new NBTTagCompound());
    }

    @Override
    public boolean completeMove(TileEntity blockEntity, IBlockState state, NBTTagCompound savedData, World newLevel,
                                BlockPos newPosition) {
        savedData.setInteger("x", newPosition.getX());
        savedData.setInteger("y", newPosition.getY());
        savedData.setInteger("z", newPosition.getZ());
        var tileEntity = TileEntity.create(newLevel, savedData);
        if (tileEntity == null) {
            return false;
        }

        newLevel.setTileEntity(newPosition, tileEntity);
        return true;
    }

}
