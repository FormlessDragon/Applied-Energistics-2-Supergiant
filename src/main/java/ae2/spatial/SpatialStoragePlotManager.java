/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2017, AlgorithmX2, All rights reserved.
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
import ae2.core.definitions.AEBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SpatialStoragePlotManager {

    public static final SpatialStoragePlotManager INSTANCE = new SpatialStoragePlotManager();

    private SpatialStoragePlotManager() {
    }

    public WorldServer getLevel() {
        SpatialStorageDimensionIds.init();
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            throw new IllegalStateException("No server is currently running.");
        }

        int dimensionId = SpatialStorageDimensionIds.getDimensionId();
        WorldServer level = DimensionManager.getWorld(dimensionId);
        if (level == null) {
            DimensionManager.initDimension(dimensionId);
            level = DimensionManager.getWorld(dimensionId);
        }
        if (level == null) {
            throw new IllegalStateException("The storage cell level is missing.");
        }
        return level;
    }

    private SpatialStorageWorldData getWorldData() {
        MapStorage storage = getLevel().getPerWorldStorage();
        SpatialStorageWorldData result = (SpatialStorageWorldData) storage.getOrLoadData(
            SpatialStorageWorldData.class, SpatialStorageWorldData.ID);
        if (result == null) {
            SpatialStorageWorldData legacy = (SpatialStorageWorldData) storage.getOrLoadData(
                SpatialStorageWorldData.class, SpatialStorageWorldData.LEGACY_ID);
            result = new SpatialStorageWorldData(SpatialStorageWorldData.ID);
            if (legacy != null) {
                result.copyFrom(legacy);
                result.markDirty();
            }
            storage.setData(SpatialStorageWorldData.ID, result);
        }
        return result;
    }

    @Nullable
    public SpatialStoragePlot getPlot(int plotId) {
        if (plotId == -1) {
            return null;
        }
        return getWorldData().getPlotById(plotId);
    }

    public SpatialStoragePlot allocatePlot(BlockPos size, int ownerId) {
        SpatialStoragePlot plot = getWorldData().allocatePlot(size, ownerId);
        AELog.info("Allocating storage cell plot %d with size %s for %d", plot.getId(), size, ownerId);
        return plot;
    }

    public void setLastTransition(int plotId, TransitionInfo info) {
        getWorldData().setLastTransition(plotId, info);
    }

    public List<SpatialStoragePlot> getPlots() {
        return getWorldData().getPlots();
    }

    public void freePlot(int plotId, boolean resetBlocks) {
        SpatialStoragePlot plot = getPlot(plotId);
        if (plot == null) {
            return;
        }

        if (resetBlocks) {
            BlockPos from = plot.getOrigin();
            BlockPos to = from.add(plot.getSize()).add(-1, -1, -1);

            AELog.info("Clearing spatial storage plot %s (%s -> %s)", plotId, from, to);

            WorldServer level = getLevel();
            IBlockState matrixFrame = AEBlocks.MATRIX_FRAME.block().getDefaultState();
            for (BlockPos blockPos : BlockPos.getAllInBoxMutable(from, to)) {
                level.setBlockState(blockPos, matrixFrame, 2);
            }
        }

        getWorldData().removePlot(plotId);
    }
}
