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

package ae2.container.me.crafting;

import ae2.api.networking.crafting.ICraftingCPU;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

public class CraftingCPURecord implements Comparable<CraftingCPURecord> {
    private final ICraftingCPU cpu;
    private final long size;
    private final int processors;
    @Nullable
    private final ITextComponent rawName;
    @Nullable
    private ITextComponent name;

    public CraftingCPURecord(long size, int processors, ICraftingCPU cpu) {
        this.size = size;
        this.processors = processors;
        this.cpu = cpu;
        this.rawName = cpu.getName();
        this.name = this.rawName;
    }

    @Override
    public int compareTo(CraftingCPURecord other) {
        int byProcessors = Integer.compare(other.getProcessors(), this.getProcessors());
        if (byProcessors != 0) {
            return byProcessors;
        }
        return Long.compare(other.getSize(), this.getSize());
    }

    public ICraftingCPU getCpu() {
        return this.cpu;
    }

    public int getProcessors() {
        return this.processors;
    }

    public long getSize() {
        return this.size;
    }

    @Nullable
    public ITextComponent getName() {
        return this.name;
    }

    @Nullable
    public ITextComponent getRawName() {
        return this.rawName;
    }

    public void setName(@Nullable ITextComponent name) {
        this.name = name;
    }
}
