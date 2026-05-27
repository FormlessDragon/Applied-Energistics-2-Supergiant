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

import appeng.api.config.CondenserOutput;
import appeng.api.config.Settings;
import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IProgressProvider;
import appeng.container.slot.OutputSlot;
import appeng.container.slot.RestrictedInputSlot;
import appeng.tile.misc.TileCondenser;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerCondenser extends AEBaseContainer implements IProgressProvider {

    private final TileCondenser condenser;

    @GuiSync(0)
    public int requiredEnergy;

    @GuiSync(1)
    public int storedPower;

    @GuiSync(2)
    public CondenserOutput output = CondenserOutput.TRASH;

    public ContainerCondenser(InventoryPlayer ip, TileCondenser condenser) {
        super(ip, condenser);
        this.condenser = condenser;

        var inv = condenser.getInternalInventory();
        this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.TRASH, inv, 0),
            SlotSemantics.MACHINE_INPUT);
        this.addSlot(new OutputSlot(inv, 1, 0, 0), SlotSemantics.MACHINE_OUTPUT);
        this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.STORAGE_COMPONENT, inv, 2)
            .setStackLimit(1), SlotSemantics.STORAGE_CELL);

        this.addPlayerInventorySlots(8, 119);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            final double maxStorage = this.condenser.getStorage();
            final double requiredEnergy = this.condenser.getRequiredPower();

            this.requiredEnergy = requiredEnergy == 0 ? (int) maxStorage : (int) Math.min(requiredEnergy, maxStorage);
            this.storedPower = (int) this.condenser.getStoredPower();
            this.output = this.condenser.getConfigManager().getSetting(Settings.CONDENSER_OUTPUT);
        }

        super.broadcastChanges();
    }

    @Override
    public int getCurrentProgress() {
        return this.storedPower;
    }

    @Override
    public int getMaxProgress() {
        return this.requiredEnergy;
    }

    public CondenserOutput getOutput() {
        return this.output;
    }
}
