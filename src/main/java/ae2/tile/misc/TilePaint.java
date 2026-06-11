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

package ae2.tile.misc;

import ae2.api.util.AEColor;
import ae2.helpers.Splotch;
import ae2.tile.AEBaseTile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.Constants;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TilePaint extends AEBaseTile {
    private static final int LIGHT_PER_DOT = 12;
    private static final int MAX_DOTS = 21;
    private static final int BYTES_PER_DOT = 2;

    private int isLit = 0;
    private List<Splotch> dots = null;

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        final ByteBuf myDat = Unpooled.buffer();
        this.writeBuffer(myDat);
        if (myDat.hasArray()) {
            data.setByteArray("dots", myDat.array());
        }
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        if (data.hasKey("dots", Constants.NBT.TAG_BYTE_ARRAY)) {
            this.readBuffer(Unpooled.copiedBuffer(data.getByteArray("dots")));
        }
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        this.writeBuffer(data);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        super.readFromStream(data);
        this.readBuffer(data);
        return true;
    }

    private void writeBuffer(ByteBuf out) {
        if (this.dots == null) {
            out.writeByte(0);
            return;
        }

        out.writeByte(this.dots.size());
        for (final Splotch s : this.dots) {
            s.writeToStream(out);
        }
    }

    private void readBuffer(ByteBuf in) {
        if (!in.isReadable()) {
            this.isLit = 0;
            this.dots = null;
            return;
        }

        final int howMany = in.readUnsignedByte();
        if (howMany == 0) {
            this.isLit = 0;
            this.dots = null;
            return;
        }

        final int readableDots = Math.min(Math.min(howMany, MAX_DOTS), in.readableBytes() / BYTES_PER_DOT);
        if (readableDots == 0) {
            this.isLit = 0;
            this.dots = null;
            return;
        }

        this.dots = new ObjectArrayList<>(readableDots);
        for (int x = 0; x < readableDots; x++) {
            this.dots.add(new Splotch(in));
        }
        updateLight();
    }

    public void neighborChanged() {
        if (this.dots == null) {
            return;
        }

        for (final EnumFacing side : EnumFacing.VALUES) {
            if (!this.isSideValid(side)) {
                this.removeSide(side);
            }
        }

        this.updateData();
    }

    @SuppressWarnings("deprecation")
    public boolean isSideValid(final EnumFacing side) {
        final BlockPos p = this.pos.offset(side);
        final IBlockState blk = this.world.getBlockState(p);
        return blk.getBlock().isSideSolid(blk, this.world, p, side.getOpposite());
    }

    private void removeSide(final EnumFacing side) {
        this.dots.removeIf(s -> s.getSide() == side);

        this.markForUpdate();
        this.saveChanges();
    }

    private void updateData() {
        if (this.dots != null && this.dots.isEmpty()) {
            this.dots = null;
        }

        updateLight();

        if (this.dots == null) {
            this.world.setBlockToAir(this.pos);
        }
    }

    @SuppressWarnings("deprecation")
    public void addBlot(AEColor color, boolean lit, EnumFacing side, Vec3d hitVec) {
        final BlockPos p = this.pos.offset(side);
        final IBlockState blk = this.world.getBlockState(p);
        if (blk.getBlock().isSideSolid(blk, this.world, p, side.getOpposite())) {
            if (this.dots == null) {
                this.dots = new ObjectArrayList<>();
            }

            if (this.dots.size() >= MAX_DOTS) {
                this.dots.removeFirst();
            }

            this.dots.add(new Splotch(color, lit, side, hitVec));
            updateLight();
            this.markForUpdate();
            this.saveChanges();
        }
    }

    public int getLightLevel() {
        return this.isLit;
    }

    public Collection<Splotch> getDots() {
        if (this.dots == null) {
            return Collections.emptyList();
        }

        return this.dots;
    }

    private void updateLight() {
        this.isLit = 0;
        if (this.dots != null) {
            for (final Splotch s : this.dots) {
                if (s.isLumen()) {
                    this.isLit += LIGHT_PER_DOT;
                }
            }
        }
        if (this.isLit > 15) {
            this.isLit = 15;
        }
    }
}
