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

package ae2.me.cluster.implementations;

import ae2.me.cluster.IAECluster;
import ae2.me.cluster.MBCalculator;
import ae2.tile.spatial.TileSpatialPylon;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;

public class SpatialPylonCluster implements IAECluster {

    private final World level;
    private final BlockPos min;
    private final BlockPos max;
    private final ObjectList<TileSpatialPylon> line = new ObjectArrayList<>();

    private boolean destroyed;
    private boolean valid;
    private Axis currentAxis = Axis.UNFORMED;

    public SpatialPylonCluster(World level, BlockPos min, BlockPos max) {
        this.level = level;
        this.min = min;
        this.max = max;

        if (this.min.getX() != this.max.getX()) {
            this.currentAxis = Axis.X;
        } else if (this.min.getY() != this.max.getY()) {
            this.currentAxis = Axis.Y;
        } else if (this.min.getZ() != this.max.getZ()) {
            this.currentAxis = Axis.Z;
        }
    }

    @Override
    public BlockPos getBoundsMin() {
        return this.min;
    }

    @Override
    public BlockPos getBoundsMax() {
        return this.max;
    }

    @Override
    public void updateStatus(boolean updateGrid) {
        for (var pylon : this.line) {
            pylon.recalculateDisplay();
        }
    }

    @Override
    public void destroy() {
        if (this.destroyed) {
            return;
        }

        this.destroyed = true;
        MBCalculator.setModificationInProgress(this);
        try {
            for (var pylon : this.line) {
                pylon.updateStatus(null);
            }
        } finally {
            MBCalculator.setModificationInProgress(null);
        }
    }

    @Override
    public boolean isDestroyed() {
        return this.destroyed;
    }

    @Override
    public Iterator<? extends TileEntity> getBlockEntities() {
        return this.line.iterator();
    }

    public int size() {
        return this.line.size();
    }

    public Axis getCurrentAxis() {
        return this.currentAxis;
    }

    public boolean isValid() {
        return this.valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public World getLevel() {
        return this.level;
    }

    public List<TileSpatialPylon> getLine() {
        return this.line;
    }

    public enum Axis {
        X,
        Y,
        Z,
        UNFORMED
    }
}
