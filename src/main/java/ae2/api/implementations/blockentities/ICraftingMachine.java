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

package ae2.api.implementations.blockentities;

import ae2.api.AECapabilities;
import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.KeyCounter;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public interface ICraftingMachine extends IPatternProviderBatchTarget {

    @Nullable
    static ICraftingMachine of(@Nullable TileEntity blockEntity, EnumFacing side) {
        if (blockEntity == null) {
            return null;
        }

        if (blockEntity instanceof ICraftingMachine icm) {
            return icm;
        }

        return blockEntity.getCapability(AECapabilities.CRAFTING_MACHINE, side);
    }

    @Nullable
    static ICraftingMachine of(World level, BlockPos pos, EnumFacing side) {
        return of(level.getTileEntity(pos), side);
    }

    @Nullable
    PatternContainerGroup getCraftingMachineInfo();

    boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, int multiplier, EnumFacing ejectionDirection);

    boolean acceptsPlans();
}
