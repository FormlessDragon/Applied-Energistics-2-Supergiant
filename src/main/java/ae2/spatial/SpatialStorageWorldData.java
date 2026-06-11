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

import ae2.core.AELog;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants.NBT;

import java.util.List;

public class SpatialStorageWorldData extends WorldSavedData {

    public static final String ID = "ae2_spatial_storage";
    public static final String LEGACY_ID = "ae2_spatial_plots";
    private static final int CURRENT_FORMAT = 2;
    private static final String TAG_FORMAT = "format";
    private static final String TAG_PLOTS = "plots";

    private final Int2ObjectOpenHashMap<SpatialStoragePlot> plots = new Int2ObjectOpenHashMap<>();

    public SpatialStorageWorldData(String name) {
        super(name);
    }

    public SpatialStoragePlot getPlotById(int id) {
        return plots.get(id);
    }

    public List<SpatialStoragePlot> getPlots() {
        return ImmutableList.copyOf(plots.values());
    }

    public SpatialStoragePlot allocatePlot(BlockPos size, int owner) {
        int nextId = 1;
        for (int id : plots.keySet()) {
            if (id >= nextId) {
                nextId = id + 1;
            }
        }
        SpatialStoragePlot plot = new SpatialStoragePlot(nextId, size, owner);
        plots.put(nextId, plot);
        markDirty();
        return plot;
    }

    public void removePlot(int plotId) {
        plots.remove(plotId);
        markDirty();
    }

    public void setLastTransition(int plotId, TransitionInfo info) {
        SpatialStoragePlot plot = plots.get(plotId);
        if (plot != null) {
            plot.setLastTransition(info);
            markDirty();
        }
    }

    void copyFrom(SpatialStorageWorldData other) {
        this.plots.clear();
        this.plots.putAll(other.plots);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.plots.clear();
        int version = nbt.getInteger(TAG_FORMAT);
        if (version != 0 && version != CURRENT_FORMAT) {
            throw new IllegalStateException("Invalid AE2 spatial info version: " + version);
        }

        NBTTagList plotTags = nbt.getTagList(TAG_PLOTS, NBT.TAG_COMPOUND);
        for (int i = 0; i < plotTags.tagCount(); i++) {
            SpatialStoragePlot plot = SpatialStoragePlot.fromTag(plotTags.getCompoundTagAt(i));
            if (plot == null) {
                AELog.warn("Skipping invalid spatial storage plot entry %s", i);
                continue;
            }
            if (this.plots.containsKey(plot.getId())) {
                AELog.warn("Overwriting duplicate plot id %s", plot.getId());
            }
            this.plots.put(plot.getId(), plot);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger(TAG_FORMAT, CURRENT_FORMAT);
        NBTTagList plotTags = new NBTTagList();
        for (SpatialStoragePlot plot : this.plots.values()) {
            plotTags.appendTag(plot.toTag());
        }
        compound.setTag(TAG_PLOTS, plotTags);
        return compound;
    }
}
