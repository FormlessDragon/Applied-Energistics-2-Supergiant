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

import appeng.container.SlotSemantics;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IProgressProvider;
import appeng.container.slot.RestrictedInputSlot;
import appeng.core.definitions.AEItems;
import appeng.tile.misc.TileVibrationChamber;
import net.minecraft.entity.player.InventoryPlayer;

public class ContainerVibrationChamber extends UpgradeableContainer<TileVibrationChamber> implements IProgressProvider {
    @GuiSync(2)
    public double currentFuelTicksPerTick = 0;

    @GuiSync(3)
    public int remainingBurnTime = 0;

    public ContainerVibrationChamber(InventoryPlayer ip, TileVibrationChamber host) {
        super(ip, host);
    }

    @Override
    protected void setupInventorySlots() {
        this.addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.FUEL,
            this.getHost().getInternalInventory(), 0), SlotSemantics.MACHINE_INPUT);
    }

    @Override
    public void broadcastChanges() {
        if (this.isServerSide()) {
            var chamber = this.getHost();
            this.remainingBurnTime = chamber.getFuelItemFuelTicks() <= 0 ? 0
                : (int) (100.0 * chamber.getRemainingFuelTicks() / chamber.getFuelItemFuelTicks());
            this.currentFuelTicksPerTick = this.remainingBurnTime <= 0 ? 0 : chamber.getCurrentFuelTicksPerTick();
        }

        super.broadcastChanges();
    }

    @Override
    public int getCurrentProgress() {
        return (int) (this.currentFuelTicksPerTick * 100);
    }

    @Override
    public int getMaxProgress() {
        return (int) (this.getHost().getMaxFuelTicksPerTick() * 100);
    }

    public int getRemainingBurnTime() {
        return this.remainingBurnTime;
    }

    public double getPowerPerTick() {
        return this.currentFuelTicksPerTick * this.getHost().getEnergyPerFuelTick();
    }

    public double getFuelEfficiency() {
        return 100 + this.getHost().getInstalledUpgrades(AEItems.ENERGY_CARD.item()) * 50;
    }
}
