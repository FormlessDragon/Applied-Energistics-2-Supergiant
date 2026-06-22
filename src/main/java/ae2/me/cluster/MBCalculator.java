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

package ae2.me.cluster;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;

import java.lang.ref.WeakReference;

public abstract class MBCalculator<TBlockEntity extends IAEMultiBlock<TCluster>, TCluster extends IAECluster> {

    /**
     * To avoid recursive cluster rebuilds, we use a global field to prevent this from happening. This is set to the
     * cluster that is currently causing a Multiblock modification.
     */
    private static WeakReference<IAECluster> modificationInProgress = new WeakReference<>(null);

    protected final TBlockEntity target;

    public MBCalculator(TBlockEntity t) {
        this.target = t;
    }

    public static boolean isModificationInProgress() {
        return modificationInProgress.get() != null;
    }

    public static void setModificationInProgress(IAECluster cluster) {
        IAECluster inProgress = modificationInProgress.get();
        if (inProgress == cluster) {
            return;
        }
        if (inProgress != null && cluster != null) {
            throw new IllegalStateException("A modification is already in-progress for: " + inProgress);
        }
        modificationInProgress = new WeakReference<>(cluster);
    }

    private static boolean isWithinBounds(BlockPos pos, BlockPos boundsMin, BlockPos boundsMax) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= boundsMin.getX() && y >= boundsMin.getY() && z >= boundsMin.getZ() && x <= boundsMax.getX()
            && y <= boundsMax.getY() && z <= boundsMax.getZ();
    }

    public void updateMultiblockAfterNeighborUpdate(World level, BlockPos loc, BlockPos changedPos) {
        boolean recheck;

        TCluster cluster = target.getCluster();
        if (cluster != null) {
            if (isWithinBounds(changedPos, cluster.getBoundsMin(), cluster.getBoundsMax())) {
                // If the location is part of the current multiblock, always re-check
                recheck = true;
            } else {
                // If the location is outside, only re-check if it would now be considered part of it
                recheck = isValidBlockEntityAt(level, changedPos.getX(), changedPos.getY(), changedPos.getZ());
            }
        } else {
            // Always recheck if the tile entity is not part of a cluster, because the adjacent
            // block could have previously been a valid tile entity, but in a wrong placement,
            // or the other way around.
            recheck = true;
        }

        if (recheck) {
            calculateMultiblock(level, loc);
        }
    }

    public void calculateMultiblock(World level, BlockPos loc) {
        if (isModificationInProgress()) {
            return;
        }

        IAECluster currentCluster = target.getCluster();
        if (currentCluster != null && currentCluster.isDestroyed()) {
            return; // If we're still part of a cluster that is in the process of being destroyed,
            // don't recalc.
        }

        try {
            final MutableBlockPos min = new MutableBlockPos(loc);
            final MutableBlockPos max = new MutableBlockPos(loc);

            // find size of MB structure...
            while (this.isValidBlockEntityAt(level, min.getX() - 1, min.getY(), min.getZ())) {
                min.setPos(min.getX() - 1, min.getY(), min.getZ());
            }
            while (this.isValidBlockEntityAt(level, min.getX(), min.getY() - 1, min.getZ())) {
                min.setPos(min.getX(), min.getY() - 1, min.getZ());
            }
            while (this.isValidBlockEntityAt(level, min.getX(), min.getY(), min.getZ() - 1)) {
                min.setPos(min.getX(), min.getY(), min.getZ() - 1);
            }
            while (this.isValidBlockEntityAt(level, max.getX() + 1, max.getY(), max.getZ())) {
                max.setPos(max.getX() + 1, max.getY(), max.getZ());
            }
            while (this.isValidBlockEntityAt(level, max.getX(), max.getY() + 1, max.getZ())) {
                max.setPos(max.getX(), max.getY() + 1, max.getZ());
            }
            while (this.isValidBlockEntityAt(level, max.getX(), max.getY(), max.getZ() + 1)) {
                max.setPos(max.getX(), max.getY(), max.getZ() + 1);
            }

            BlockPos boundsMin = min.toImmutable();
            BlockPos boundsMax = max.toImmutable();

            if (this.checkMultiblockScale(boundsMin, boundsMax) && this.verifyUnownedRegion(level, boundsMin, boundsMax)) {
                try {
                    if (!this.verifyInternalStructure(level, boundsMin, boundsMax)) {
                        this.disconnect();
                        return;
                    }
                } catch (Exception err) {
                    this.disconnect();
                    return;
                }

                boolean updateGrid = false;
                TCluster cluster = this.target.getCluster();
                if (cluster == null || !cluster.getBoundsMin().equals(boundsMin) || !cluster.getBoundsMax().equals(boundsMax)) {
                    cluster = this.createCluster(level, boundsMin, boundsMax);
                    setModificationInProgress(cluster);
                    // NOTE: The following will break existing clusters within the bounds
                    this.updateBlockEntities(cluster, level, boundsMin, boundsMax);

                    updateGrid = true;
                } else {
                    setModificationInProgress(cluster);
                }

                cluster.updateStatus(updateGrid);
                return;
            }
        } finally {
            setModificationInProgress(null);
        }

        this.disconnect();
    }

    private boolean isValidBlockEntityAt(World level, int x, int y, int z) {
        return this.isValidBlockEntity(level.getTileEntity(new BlockPos(x, y, z)));
    }

    /**
     * verify if the structure is the correct dimensions, or size
     *
     * @param min min world coord
     * @param max max world coord
     * @return true if structure has correct dimensions or size
     */
    public abstract boolean checkMultiblockScale(BlockPos min, BlockPos max);

    private boolean verifyUnownedRegion(World level, BlockPos min, BlockPos max) {
        for (EnumFacing side : EnumFacing.VALUES) {
            if (this.verifyUnownedRegionInner(level, min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(),
                max.getZ(),
                side)) {
                return false;
            }
        }

        return true;
    }

    /**
     * construct the correct cluster, usually very simple.
     *
     * @param level level
     * @param min   min world coord
     * @param max   max world coord
     * @return created cluster
     */
    public abstract TCluster createCluster(World level, BlockPos min, BlockPos max);

    public abstract boolean verifyInternalStructure(World level, BlockPos min, BlockPos max);

    /**
     * disassembles the multi-block.
     */
    public void disconnect() {
        this.target.disconnect(true);
    }

    /**
     * configure the multi-block block entities, most of the important stuff is in here.
     *
     * @param c     updated cluster
     * @param level in level
     * @param min   min world coord
     * @param max   max world coord
     */
    public abstract void updateBlockEntities(TCluster c, World level, BlockPos min, BlockPos max);

    /**
     * check if the block entities are correct for the structure.
     *
     * @param te to be checked tile entity
     * @return true if tile entity is valid for structure
     */
    public abstract boolean isValidBlockEntity(TileEntity te);

    private boolean verifyUnownedRegionInner(World level, int minX, int minY, int minZ, int maxX, int maxY,
                                             int maxZ, EnumFacing side) {
        switch (side) {
            case WEST -> {
                minX -= 1;
                maxX = minX;
            }
            case EAST -> {
                maxX += 1;
                minX = maxX;
            }
            case DOWN -> {
                minY -= 1;
                maxY = minY;
            }
            case NORTH -> {
                maxZ += 1;
                minZ = maxZ;
            }
            case SOUTH -> {
                minZ -= 1;
                maxZ = minZ;
            }
            case UP -> {
                maxY += 1;
                minY = maxY;
            }
            default -> {
                return false;
            }
        }

        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ))) {
            final TileEntity te = level.getTileEntity(p);
            if (this.isValidBlockEntity(te)) {
                return true;
            }
        }

        return false;
    }
}
