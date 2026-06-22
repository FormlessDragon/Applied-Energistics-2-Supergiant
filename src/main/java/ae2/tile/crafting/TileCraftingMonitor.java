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

package ae2.tile.crafting;

import ae2.api.implementations.blockentities.IColorableBlockEntity;
import ae2.api.stacks.GenericStack;
import ae2.api.util.AEColor;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class TileCraftingMonitor extends TileCraftingUnit implements IColorableBlockEntity {

    private static final AEColor[] COLORS = AEColor.values();

    private GenericStack display;
    private AEColor paintedColor = AEColor.TRANSPARENT;

    private static AEColor readColor(int colorIndex) {
        return colorIndex >= 0 && colorIndex < COLORS.length ? COLORS[colorIndex] : AEColor.TRANSPARENT;
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        var packetBuffer = new PacketBuffer(data);
        packetBuffer.writeByte(this.paintedColor.ordinal());
        GenericStack.writeBuffer(display, packetBuffer);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        final boolean changed = super.readFromStream(data);
        var packetBuffer = new PacketBuffer(data);
        final AEColor oldPaintedColor = this.paintedColor;
        this.paintedColor = readColor(packetBuffer.readByte());
        this.display = GenericStack.readBuffer(packetBuffer);
        return oldPaintedColor != this.paintedColor || changed;
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        if (data.hasKey("paintedColor")) {
            this.paintedColor = readColor(data.getByte("paintedColor"));
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setByte("paintedColor", (byte) this.paintedColor.ordinal());
    }

    public void setJob(@Nullable GenericStack stack) {
        if (!Objects.equals(this.display, stack)) {
            this.display = stack;
            this.markForUpdate();
        }
    }

    @Nullable
    public GenericStack getJobProgress() {
        return this.display;
    }

    @Override
    public AEColor getColor() {
        return this.paintedColor;
    }

    @Override
    public boolean recolourBlock(EnumFacing side, AEColor newPaintedColor, EntityPlayer who) {
        if (this.paintedColor == newPaintedColor) {
            return false;
        }

        this.paintedColor = newPaintedColor;
        this.saveChanges();
        this.markForUpdate();
        return true;
    }
}
