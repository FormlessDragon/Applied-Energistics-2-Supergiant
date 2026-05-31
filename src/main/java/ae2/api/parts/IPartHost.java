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

package ae2.api.parts;

import ae2.api.util.AEColor;
import ae2.api.util.DimensionalBlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public interface IPartHost extends ICustomCableConnection {

    IFacadeContainer getFacadeContainer();

    @Nullable
    IPart getPart(@Nullable EnumFacing side);

    boolean canAddPart(ItemStack part, @Nullable EnumFacing side);

    @Nullable
    <T extends IPart> T addPart(IPartItem<T> partItem, @Nullable EnumFacing side, @Nullable EntityPlayer owner);

    @Nullable
    <T extends IPart> T replacePart(IPartItem<T> partItem, @Nullable EnumFacing side, @Nullable EntityPlayer owner,
                                    @Nullable EnumHand hand);

    void removePartFromSide(@Nullable EnumFacing side);

    void markForUpdate();

    DimensionalBlockPos getLocation();

    TileEntity getTileEntity();

    AEColor getColor();

    void clearContainer();

    boolean isBlocked(EnumFacing side);

    SelectedPart selectPartLocal(Vec3d pos);

    Iterable<AxisAlignedBB> getCollisionShape(@Nullable Entity entity);

    boolean removePart(IPart part);

    default SelectedPart selectPartWorld(Vec3d pos) {
        DimensionalBlockPos worldPos = getLocation();
        return selectPartLocal(pos.subtract(
            worldPos.getPos().getX(),
            worldPos.getPos().getY(),
            worldPos.getPos().getZ()));
    }

    void markForSave();

    void partChanged();

    boolean hasRedstone();

    boolean isEmpty();

    void cleanup();

    void notifyNeighbors();

    void notifyNeighborNow(EnumFacing side);

    boolean isInWorld();
}


