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

package ae2.parts.networking;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.PowerUnit;
import ae2.api.parts.IPartCollisionHelper;
import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.util.AECableType;
import ae2.core.AppEng;
import ae2.items.parts.PartModels;
import ae2.parts.AEBasePart;
import ae2.parts.PartModel;
import ae2.tile.powersink.ForgeEnergyAdapter;
import ae2.tile.powersink.IExternalPowerSink;
import net.minecraftforge.energy.IEnergyStorage;

public class EnergyAcceptorPart extends AEBasePart implements IExternalPowerSink {

    @PartModels
    private static final IPartModel MODELS = new PartModel(AppEng.makeId("part/energy_acceptor"));

    private final ForgeEnergyAdapter forgeEnergyAdapter;

    public EnergyAcceptorPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode().setIdlePowerUsage(0);
        this.forgeEnergyAdapter = new ForgeEnergyAdapter(this);
    }

    public IEnergyStorage getEnergyStorage() {
        return this.forgeEnergyAdapter;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(4, 4, 12, 12, 12, 14);
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 2;
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS;
    }

    @Override
    public final double getExternalPowerDemand(PowerUnit externalUnit, double maxPowerRequired) {
        return PowerUnit.AE.convertTo(externalUnit,
            Math.max(0.0, this.getFunnelPowerDemand(externalUnit.convertTo(PowerUnit.AE, maxPowerRequired))));
    }

    protected double getFunnelPowerDemand(double maxRequired) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            return grid.getEnergyService().getEnergyDemand(maxRequired);
        }
        return 0;
    }

    @Override
    public final double injectExternalPower(PowerUnit input, double amount, Actionable mode) {
        return PowerUnit.AE.convertTo(input, this.funnelPowerIntoStorage(input.convertTo(PowerUnit.AE, amount), mode));
    }

    protected double funnelPowerIntoStorage(double power, Actionable mode) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            return grid.getEnergyService().injectPower(power, mode);
        }
        return power;
    }

    @Override
    public final double injectAEPower(double amount, Actionable mode) {
        return amount;
    }

    @Override
    public final double getAEMaxPower() {
        return 0;
    }

    @Override
    public final double getAECurrentPower() {
        return 0;
    }

    @Override
    public final boolean isAEPublicPowerStorage() {
        return false;
    }

    @Override
    public final AccessRestriction getPowerFlow() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public final double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        return 0;
    }
}
