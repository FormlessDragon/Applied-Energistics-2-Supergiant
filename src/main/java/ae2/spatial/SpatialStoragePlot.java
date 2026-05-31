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

package ae2.spatial;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class SpatialStoragePlot {

    public static final int MAX_SIZE = 128;
    private static final String TAG_ID = "id";
    private static final String TAG_SIZE = "size";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_LAST_TRANSITION = "last_transition";
    private static final int REGION_SIZE = 512;
    private final int id;
    private final BlockPos size;
    private final int owner;
    @Nullable
    private TransitionInfo lastTransition;

    public SpatialStoragePlot(int id, BlockPos size, int owner) {
        this.id = id;
        this.size = size.toImmutable();
        this.owner = owner;
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1) {
            throw new IllegalArgumentException("Plot size " + size + " is smaller than minimum size.");
        }
        if (size.getX() > MAX_SIZE || size.getY() > MAX_SIZE || size.getZ() > MAX_SIZE) {
            throw new IllegalArgumentException("Plot size " + size + " exceeds maximum size of " + MAX_SIZE);
        }
    }

    public static SpatialStoragePlot fromTag(NBTTagCompound tag) {
        SpatialStoragePlot plot = new SpatialStoragePlot(
            tag.getInteger(TAG_ID),
            readSizeTag(tag),
            tag.getInteger(TAG_OWNER));
        if (tag.hasKey(TAG_LAST_TRANSITION, 10)) {
            plot.lastTransition = TransitionInfo.fromTag(tag.getCompoundTag(TAG_LAST_TRANSITION));
        }
        return plot;
    }

    private static NBTTagCompound writeSizeTag(BlockPos size) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", size.getX());
        tag.setInteger("y", size.getY());
        tag.setInteger("z", size.getZ());
        return tag;
    }

    private static BlockPos readSizeTag(NBTTagCompound tag) {
        if (tag.hasKey(TAG_SIZE, 10)) {
            NBTTagCompound sizeTag = tag.getCompoundTag(TAG_SIZE);
            return new BlockPos(sizeTag.getInteger("x"), sizeTag.getInteger("y"), sizeTag.getInteger("z"));
        }
        return new BlockPos(tag.getInteger("sizeX"), tag.getInteger("sizeY"), tag.getInteger("sizeZ"));
    }

    public int getId() {
        return id;
    }

    public BlockPos getSize() {
        return size;
    }

    public int getOwner() {
        return owner;
    }

    @Nullable
    public TransitionInfo getLastTransition() {
        return lastTransition;
    }

    void setLastTransition(@org.jspecify.annotations.Nullable TransitionInfo info) {
        this.lastTransition = info;
    }

    public BlockPos getOrigin() {
        int signBits = id & 0b11;
        int offsetBits = id >> 2;
        int offsetScale = 1;
        int posx = REGION_SIZE / 2;
        int posz = REGION_SIZE / 2;

        while (offsetBits != 0) {
            posx += REGION_SIZE * offsetScale * (offsetBits & 0b01);
            posz += REGION_SIZE * offsetScale * (offsetBits >> 1 & 0b01);
            offsetBits >>= 2;
            offsetScale <<= 1;
        }

        if ((signBits & 0b01) != 0) {
            posz *= -1;
        }
        if ((signBits & 0b10) != 0) {
            posx *= -1;
        }

        posx -= 64;
        posz -= 64;

        return new BlockPos(posx, 64, posz);
    }

    public String getRegionFilename() {
        BlockPos origin = this.getOrigin();
        ChunkPos originChunk = new ChunkPos(origin);
        return String.format(Locale.ROOT, "r.%d.%d.mca", originChunk.x >> 5, originChunk.z >> 5);
    }

    public NBTTagCompound toTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_ID, this.id);
        tag.setTag(TAG_SIZE, writeSizeTag(this.size));
        tag.setInteger(TAG_OWNER, this.owner);
        if (this.lastTransition != null) {
            tag.setTag(TAG_LAST_TRANSITION, this.lastTransition.toTag());
        }
        return tag;
    }
}
