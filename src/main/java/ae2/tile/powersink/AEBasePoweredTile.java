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

package ae2.tile.powersink;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.PowerUnit;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.api.networking.events.GridPowerStorageStateChanged.PowerEventType;
import ae2.integration.Integrations;
import ae2.integration.abstraction.IC2PowerSink;
import ae2.me.energy.StoredEnergyAmount;
import ae2.tile.AEBaseInvTile;
import com.google.common.collect.ImmutableSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import org.jetbrains.annotations.Nullable;
import java.util.EnumSet;
import java.util.Set;

public abstract class AEBasePoweredTile extends AEBaseInvTile
    implements IAEPowerStorage, IExternalPowerSink {

    private static final Set<EnumFacing> ALL_SIDES = ImmutableSet.copyOf(EnumSet.allOf(EnumFacing.class));
    // the current power buffer.
    private final StoredEnergyAmount stored = new StoredEnergyAmount(0, 10000, this::emitPowerStateEvent);
    private final IEnergyStorage forgeEnergyAdapter;
    private final IC2PowerSink ic2Sink;
    private boolean internalPublicPowerStorage = false;
    private AccessRestriction internalPowerFlow = AccessRestriction.READ_WRITE;
    private Set<EnumFacing> internalPowerSides = ALL_SIDES;

    public AEBasePoweredTile() {
        this.forgeEnergyAdapter = new ForgeEnergyAdapter(this);
        this.ic2Sink = Integrations.ic2().createPowerSink(this, this);
        this.ic2Sink.setValidFaces(this.internalPowerSides);
    }

    protected final Set<EnumFacing> getPowerSides() {
        return this.internalPowerSides;
    }

    protected void setPowerSides(Set<EnumFacing> sides) {
        this.internalPowerSides = ImmutableSet.copyOf(sides);
        this.ic2Sink.setValidFaces(this.internalPowerSides);
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setDouble("internalCurrentPower", this.getInternalCurrentPower());
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.setInternalCurrentPower(data.getDouble("internalCurrentPower"));
    }

    @Override
    public final double getExternalPowerDemand(PowerUnit externalUnit, double maxPowerRequired) {
        return PowerUnit.AE.convertTo(externalUnit,
            Math.max(0.0, this.getFunnelPowerDemand(externalUnit.convertTo(PowerUnit.AE, maxPowerRequired))));
    }

    protected double getFunnelPowerDemand(double maxRequired) {
        return this.getInternalMaxPower() - this.getInternalCurrentPower();
    }

    @Override
    public final double injectExternalPower(PowerUnit input, double amt, Actionable mode) {
        return PowerUnit.AE.convertTo(input, this.funnelPowerIntoStorage(input.convertTo(PowerUnit.AE, amt), mode));
    }

    protected double funnelPowerIntoStorage(double power, Actionable mode) {
        return this.injectAEPower(power, mode);
    }

    @Override
    public final double injectAEPower(double amt, Actionable mode) {
        return amt - stored.insert(amt, mode == Actionable.MODULATE);
    }

    protected void emitPowerStateEvent(PowerEventType x) {
    }

    @Override
    public void onReady() {
        super.onReady();
        this.ic2Sink.onLoad();
    }

    @Override
    protected void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.ic2Sink.onChunkUnload();
    }

    @Override
    protected void setRemoved() {
        super.setRemoved();
        this.ic2Sink.invalidate();
    }

    @Override
    public final double getAEMaxPower() {
        return this.getInternalMaxPower();
    }

    @Override
    public final double getAECurrentPower() {
        return this.getInternalCurrentPower();
    }

    @Override
    public final boolean isAEPublicPowerStorage() {
        return this.isInternalPublicPowerStorage();
    }

    @Override
    public final AccessRestriction getPowerFlow() {
        return this.getInternalPowerFlow();
    }

    @Override
    public final double extractAEPower(double amt, Actionable mode, PowerMultiplier multiplier) {
        return multiplier.divide(this.extractAEPower(multiplier.multiply(amt), mode));
    }

    protected double extractAEPower(double amt, Actionable mode) {
        return this.stored.extract(amt, mode == Actionable.MODULATE);
    }

    public double getInternalCurrentPower() {
        return this.stored.getAmount();
    }

    public void setInternalCurrentPower(double internalCurrentPower) {
        this.stored.setStored(internalCurrentPower);
    }

    public double getInternalMaxPower() {
        return stored.getMaximum();
    }

    public void setInternalMaxPower(double internalMaxPower) {
        this.stored.setMaximum(internalMaxPower);
    }

    private boolean isInternalPublicPowerStorage() {
        return this.internalPublicPowerStorage;
    }

    public void setInternalPublicPowerStorage(boolean internalPublicPowerStorage) {
        this.internalPublicPowerStorage = internalPublicPowerStorage;
    }

    private AccessRestriction getInternalPowerFlow() {
        return this.internalPowerFlow;
    }

    public void setInternalPowerFlow(AccessRestriction internalPowerFlow) {
        this.internalPowerFlow = internalPowerFlow;
    }


    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable EnumFacing side) {
        if (side == null && getPowerSides().equals(ALL_SIDES)) {
            return this.forgeEnergyAdapter;
        } else if (side != null && getPowerSides().contains(side)) {
            return this.forgeEnergyAdapter;
        } else {
            return null;
        }
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY && this.getEnergyStorage(facing) != null) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return (T) this.getEnergyStorage(facing);
        }
        return super.getCapability(capability, facing);
    }
}
