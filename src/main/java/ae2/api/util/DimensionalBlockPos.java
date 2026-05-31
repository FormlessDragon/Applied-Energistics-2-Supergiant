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

package ae2.api.util;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * Represents a location in the Minecraft Universe
 */
public final class DimensionalBlockPos {

    private final World level;

    private final BlockPos pos;

    public DimensionalBlockPos(DimensionalBlockPos coordinate) {
        this(coordinate.getLevel(), coordinate.pos);
    }

    public DimensionalBlockPos(TileEntity tileEntity) {
        this(tileEntity.getWorld(), tileEntity.getPos());
    }

    public DimensionalBlockPos(World level, BlockPos pos) {
        this(level, pos.getX(), pos.getY(), pos.getZ());
    }

    public DimensionalBlockPos(World level, int x, int y, int z) {
        this.level = Objects.requireNonNull(level, "level");
        this.pos = new BlockPos(x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DimensionalBlockPos that = (DimensionalBlockPos) o;
        return level.equals(that.level) && pos.equals(that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, pos);
    }

    @Override
    public String toString() {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ() + " in " + getLevel().provider.getDimensionType();
    }

    public boolean isInWorld(World level) {
        return this.level == level;
    }

    public World getLevel() {
        return this.level;
    }

    public BlockPos getPos() {
        return pos;
    }
}
