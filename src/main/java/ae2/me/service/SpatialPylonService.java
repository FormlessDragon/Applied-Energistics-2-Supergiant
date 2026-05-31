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

package ae2.me.service;

import ae2.api.networking.GridHelper;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.events.GridBootingStatusChange;
import ae2.api.networking.spatial.ISpatialService;
import ae2.core.AEConfig;
import ae2.me.cluster.implementations.SpatialPylonCluster;
import ae2.tile.spatial.TileSpatialPylon;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;

public class SpatialPylonService implements ISpatialService, IGridServiceProvider {
    static {
        GridHelper.addGridServiceEventHandler(GridBootingStatusChange.class, ISpatialService.class,
            (service, event) -> ((SpatialPylonService) service).bootingRender(event));
    }

    private final IGrid myGrid;
    private long powerRequired;
    private double efficiency;
    private World captureLevel;
    private BlockPos captureMin;
    private BlockPos captureMax;
    private boolean isValid;

    public SpatialPylonService(IGrid grid) {
        this.myGrid = grid;
    }

    public void bootingRender(GridBootingStatusChange ignored) {
        this.reset(this.myGrid);
    }

    private void reset(IGrid grid) {
        var clusters = new Reference2ObjectOpenHashMap<SpatialPylonCluster, SpatialPylonCluster>();

        for (var gm : grid.getMachineNodes(TileSpatialPylon.class)) {
            if (gm.meetsChannelRequirements()) {
                final SpatialPylonCluster c = ((TileSpatialPylon) gm.getOwner()).getCluster();
                if (c != null) {
                    clusters.put(c, c);
                }
            }
        }

        this.captureLevel = null;
        this.isValid = true;

        MutableBlockPos minPoint = null;
        MutableBlockPos maxPoint = null;
        int pylonBlocks = 0;

        for (SpatialPylonCluster cl : clusters.values()) {
            if (this.captureLevel == null) {
                this.captureLevel = cl.getLevel();
            } else if (this.captureLevel != cl.getLevel()) {
                continue;
            }

            if (maxPoint == null) {
                maxPoint = new MutableBlockPos(cl.getBoundsMax());
            } else {
                maxPoint.setPos(
                    Math.max(maxPoint.getX(), cl.getBoundsMax().getX()),
                    Math.max(maxPoint.getY(), cl.getBoundsMax().getY()),
                    Math.max(maxPoint.getZ(), cl.getBoundsMax().getZ()));
            }

            if (minPoint == null) {
                minPoint = new MutableBlockPos(cl.getBoundsMin());
            } else {
                minPoint.setPos(
                    Math.min(minPoint.getX(), cl.getBoundsMin().getX()),
                    Math.min(minPoint.getY(), cl.getBoundsMin().getY()),
                    Math.min(minPoint.getZ(), cl.getBoundsMin().getZ()));
            }

            pylonBlocks += cl.size();
        }

        this.captureMin = minPoint == null ? null : minPoint.toImmutable();
        this.captureMax = maxPoint == null ? null : maxPoint.toImmutable();

        double minPower = 0;
        if (this.hasRegion()) {
            this.isValid = this.captureMax.getX() - this.captureMin.getX() > 1
                && this.captureMax.getY() - this.captureMin.getY() > 1
                && this.captureMax.getZ() - this.captureMin.getZ() > 1;

            for (SpatialPylonCluster cl : clusters.values()) {
                switch (cl.getCurrentAxis()) {
                    case X -> this.isValid = this.isValid
                        && (this.captureMax.getY() == cl.getBoundsMin().getY()
                        || this.captureMin.getY() == cl.getBoundsMax().getY()
                        || this.captureMax.getZ() == cl.getBoundsMin().getZ()
                        || this.captureMin.getZ() == cl.getBoundsMax().getZ())
                        && (this.captureMax.getY() == cl.getBoundsMax().getY()
                        || this.captureMin.getY() == cl.getBoundsMin().getY()
                        || this.captureMax.getZ() == cl.getBoundsMax().getZ()
                        || this.captureMin.getZ() == cl.getBoundsMin().getZ());
                    case Y -> this.isValid = this.isValid
                        && (this.captureMax.getX() == cl.getBoundsMin().getX()
                        || this.captureMin.getX() == cl.getBoundsMax().getX()
                        || this.captureMax.getZ() == cl.getBoundsMin().getZ()
                        || this.captureMin.getZ() == cl.getBoundsMax().getZ())
                        && (this.captureMax.getX() == cl.getBoundsMax().getX()
                        || this.captureMin.getX() == cl.getBoundsMin().getX()
                        || this.captureMax.getZ() == cl.getBoundsMax().getZ()
                        || this.captureMin.getZ() == cl.getBoundsMin().getZ());
                    case Z -> this.isValid = this.isValid
                        && (this.captureMax.getY() == cl.getBoundsMin().getY()
                        || this.captureMin.getY() == cl.getBoundsMax().getY()
                        || this.captureMax.getX() == cl.getBoundsMin().getX()
                        || this.captureMin.getX() == cl.getBoundsMax().getX())
                        && (this.captureMax.getY() == cl.getBoundsMax().getY()
                        || this.captureMin.getY() == cl.getBoundsMin().getY()
                        || this.captureMax.getX() == cl.getBoundsMax().getX()
                        || this.captureMin.getX() == cl.getBoundsMin().getX());
                    case UNFORMED -> this.isValid = false;
                }
            }

            int reqX = this.captureMax.getX() - this.captureMin.getX();
            int reqY = this.captureMax.getY() - this.captureMin.getY();
            int reqZ = this.captureMax.getZ() - this.captureMin.getZ();
            int requiredPylonBlocks = Math.max(6, (reqX * reqZ + reqX * reqY + reqY * reqZ) * 3 / 8);

            this.efficiency = (double) pylonBlocks / (double) requiredPylonBlocks;
            if (this.efficiency > 1.0) {
                this.efficiency = 1.0;
            }
            if (this.efficiency < 0.0) {
                this.efficiency = 0.0;
            }

            minPower = (double) reqX * reqY * reqZ * AEConfig.instance().getSpatialPowerMultiplier();
        }

        this.powerRequired = (long) Math.pow(minPower,
            1 + (AEConfig.instance().getSpatialPowerExponent() - 1) * (1 - this.efficiency));

        for (SpatialPylonCluster cl : clusters.values()) {
            final boolean myWasValid = cl.isValid();
            cl.setValid(this.isValid);
            if (myWasValid != this.isValid) {
                cl.updateStatus(false);
            }
        }
    }

    @Override
    public boolean hasRegion() {
        return this.captureLevel != null && this.captureMin != null && this.captureMax != null;
    }

    @Override
    public boolean isValidRegion() {
        return this.hasRegion() && this.isValid;
    }

    @Override
    public World getLevel() {
        return this.captureLevel;
    }

    @Override
    public BlockPos getMin() {
        return this.captureMin;
    }

    @Override
    public BlockPos getMax() {
        return this.captureMax;
    }

    @Override
    public long requiredPower() {
        return this.powerRequired;
    }

    @Override
    public float currentEfficiency() {
        return (float) this.efficiency * 100;
    }
}
