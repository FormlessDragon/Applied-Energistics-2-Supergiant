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

package ae2.tile.networking;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.api.util.AECableType;
import ae2.core.definitions.AEBlocks;
import ae2.tile.grid.AENetworkedTile;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

public class TileCreativeEnergyCell extends AENetworkedTile implements IAEPowerStorage {

    public TileCreativeEnergyCell() {
        super();
        this.getMainNode()
            .setIdlePowerUsage(0)
            .addService(IAEPowerStorage.class, this);
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.CREATIVE_ENERGY_CELL.stack();
    }

    @Override
    public double injectAEPower(double amt, Actionable mode) {
        return 0;
    }

    @Override
    public double getAEMaxPower() {
        return (double) Long.MAX_VALUE / 10000;
    }

    @Override
    public double getAECurrentPower() {
        return (double) Long.MAX_VALUE / 10000;
    }

    @Override
    public boolean isAEPublicPowerStorage() {
        return true;
    }

    @Override
    public AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public double extractAEPower(double amt, Actionable mode, PowerMultiplier pm) {
        return amt;
    }

    @Override
    public int getPriority() {
        // MAX_VALUE to move creative cells to the front.
        return Integer.MAX_VALUE;
    }
}
