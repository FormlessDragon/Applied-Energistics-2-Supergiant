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

package appeng.container.implementations;

import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.OutputSlot;
import appeng.container.slot.RestrictedInputSlot;
import appeng.tile.spatial.TileSpatialIOPort;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerSpatialIOPort extends AEBaseContainer {

    @GuiSync(0)
    public long currentPower;
    @GuiSync(1)
    public long maxPower;
    @GuiSync(2)
    public long requiredPower;
    @GuiSync(3)
    public long efficiency;
    @GuiSync(31)
    public int xSize;
    @GuiSync(32)
    public int ySize;
    @GuiSync(33)
    public int zSize;

    private int delay = 40;

    public ContainerSpatialIOPort(InventoryPlayer ip, TileSpatialIOPort host) {
        super(ip, host);
        this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.SPATIAL_STORAGE_CELLS,
            host.getInternalInventory(), 0, 0, 0), SlotSemantics.MACHINE_INPUT);
        this.addSlot(new OutputSlot(host.getInternalInventory(), 1, 0, 0,
                RestrictedInputSlot.PlacableItemType.SPATIAL_STORAGE_CELLS_NO_SHADOW.backgroundIcon),
            SlotSemantics.MACHINE_OUTPUT);
        this.addPlayerInventorySlots(8, 117);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            this.delay++;
            TileSpatialIOPort port = (TileSpatialIOPort) this.getTileEntity();
            appeng.api.networking.IGridNode gridNode = port != null ? port.getGridNode() : null;
            appeng.api.networking.IGrid grid = gridNode != null ? gridNode.grid() : null;
            if (this.delay > 15 && grid != null) {
                this.delay = 0;
                appeng.api.networking.energy.IEnergyService energy = grid.getEnergyService();
                appeng.api.networking.spatial.ISpatialService spatial = grid.getSpatialService();
                this.currentPower = (long) (100.0 * energy.getStoredPower());
                this.maxPower = (long) (100.0 * energy.getMaxStoredPower());
                this.requiredPower = (long) (100.0 * spatial.requiredPower());
                this.efficiency = (long) (100.0f * spatial.currentEfficiency());

                net.minecraft.util.math.BlockPos min = spatial.getMin();
                net.minecraft.util.math.BlockPos max = spatial.getMax();
                if (min != null && max != null && spatial.isValidRegion()) {
                    this.xSize = max.getX() - min.getX() - 1;
                    this.ySize = max.getY() - min.getY() - 1;
                    this.zSize = max.getZ() - min.getZ() - 1;
                } else {
                    this.xSize = 0;
                    this.ySize = 0;
                    this.zSize = 0;
                }
            }
        }
        super.broadcastChanges();
    }
}
