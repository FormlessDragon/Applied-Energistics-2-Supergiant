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

package ae2.container.implementations;

import ae2.api.config.FullnessMode;
import ae2.api.config.OperationMode;
import ae2.api.config.Settings;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.util.IConfigManager;
import ae2.container.SlotSemantics;
import ae2.container.guisync.GuiSync;
import ae2.container.slot.OutputSlot;
import ae2.container.slot.RestrictedInputSlot;
import ae2.tile.storage.TileIOPort;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerIOPort extends UpgradeableContainer<TileIOPort> {

    @GuiSync(2)
    public FullnessMode fMode = FullnessMode.EMPTY;

    @GuiSync(3)
    public OperationMode opMode = OperationMode.EMPTY;

    public ContainerIOPort(InventoryPlayer ip, TileIOPort host) {
        super(ip, host);
    }

    @Override
    protected void setupConfig() {
        InternalInventory cells = this.getHost().getSubInventory(ISegmentedInventory.CELLS);

        for (int i = 0; i < 6; i++) {
            this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_CELLS, cells, i),
                SlotSemantics.MACHINE_INPUT);
        }

        for (int i = 0; i < 6; i++) {
            this.addSlot(new OutputSlot(cells, 6 + i, 0, 0,
                    RestrictedInputSlot.PlacableItemType.STORAGE_CELLS.backgroundIcon),
                SlotSemantics.MACHINE_OUTPUT);
        }
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager cm) {
        this.setOperationMode(cm.getSetting(Settings.OPERATION_MODE));
        this.setFullMode(cm.getSetting(Settings.FULLNESS_MODE));
        this.setRedStoneMode(cm.getSetting(Settings.REDSTONE_CONTROLLED));
    }

    public FullnessMode getFullMode() {
        return this.fMode;
    }

    private void setFullMode(FullnessMode fMode) {
        this.fMode = fMode;
    }

    public OperationMode getOperationMode() {
        return this.opMode;
    }

    private void setOperationMode(OperationMode opMode) {
        this.opMode = opMode;
    }
}
