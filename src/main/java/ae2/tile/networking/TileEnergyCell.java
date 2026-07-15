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
import ae2.api.networking.IGridNode;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.api.networking.events.GridPowerStorageChanged;
import ae2.api.networking.events.GridPowerStorageChanged.ChangeType;
import ae2.api.networking.events.GridPowerStorageStateChanged;
import ae2.api.networking.events.GridPowerStorageStateChanged.PowerEventType;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.util.AECableType;
import ae2.block.networking.EnergyCellBlock;
import ae2.core.definitions.AEBlocks;
import ae2.me.energy.StoredEnergyAmount;
import ae2.tile.grid.AENetworkedTile;
import ae2.util.Platform;
import ae2.util.SettingsFrom;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

public class TileEnergyCell extends AENetworkedTile implements IAEPowerStorage, IGridTickable {

    private static final String INTERNAL_CURRENT_POWER_TAG = "internalCurrentPower";
    private static final String INTERNAL_MAX_POWER_TAG = "internalMaxPower";
    private static final String STORED_ENERGY = "stored_energy";

    private final StoredEnergyAmount stored;
    private byte currentDisplayLevel;
    private boolean neighborChangePending;
    private boolean suppressPowerStateEvent;

    public TileEnergyCell() {
        super();
        this.getMainNode()
            .setIdlePowerUsage(0)
            .addService(IAEPowerStorage.class, this)
            .addService(IGridTickable.class, this);
        this.stored = new StoredEnergyAmount(0, getConfiguredMaxPower(), this::emitPowerEvent);
    }

    public static int getStorageLevelFromFillFactor(double fillFactor) {
        return (int) Math.floor(EnergyCellBlock.MAX_FULLNESS * MathHelper.clamp(fillFactor + 0.01, 0, 1));
    }

    private static double readSavedMaxPower(NBTTagCompound data, double defaultValue) {
        return data.hasKey(INTERNAL_MAX_POWER_TAG) ? data.getDouble(INTERNAL_MAX_POWER_TAG) : defaultValue;
    }

    private static double readStoredEnergy(NBTTagCompound input) {
        NBTBase storedEnergy = input.hasKey(STORED_ENERGY, 99) ? input.getTag(STORED_ENERGY) : null;
        if (storedEnergy instanceof NBTPrimitive storedEnergyNumber) {
            return storedEnergyNumber.getDouble();
        }

        NBTTagCompound blockEntityTag = input.getCompoundTag("BlockEntityTag");
        if (blockEntityTag.hasKey(INTERNAL_CURRENT_POWER_TAG)) {
            return blockEntityTag.getDouble(INTERNAL_CURRENT_POWER_TAG);
        }

        if (input.hasKey(INTERNAL_CURRENT_POWER_TAG)) {
            return input.getDouble(INTERNAL_CURRENT_POWER_TAG);
        }

        return 0;
    }

    private static double readMaxPower(NBTTagCompound input, double defaultValue) {
        NBTTagCompound blockEntityTag = input.getCompoundTag("BlockEntityTag");
        if (blockEntityTag.hasKey(INTERNAL_MAX_POWER_TAG)) {
            return blockEntityTag.getDouble(INTERNAL_MAX_POWER_TAG);
        }

        if (input.hasKey(INTERNAL_MAX_POWER_TAG)) {
            return input.getDouble(INTERNAL_MAX_POWER_TAG);
        }

        return defaultValue;
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public void onReady() {
        super.onReady();
        this.getMainNode().setVisualRepresentation(getItemFromTile());
        double previousMaximum = this.stored.getMaximum();
        boolean previouslySuppressed = this.suppressPowerStateEvent;
        this.suppressPowerStateEvent = true;
        try {
            this.stored.setMaximum(this.getConfiguredMaxPower());
        } finally {
            this.suppressPowerStateEvent = previouslySuppressed;
        }
        if (Double.compare(previousMaximum, this.stored.getMaximum()) != 0) {
            emitPowerStorageChanged(ChangeType.ROUTING_CHANGED);
        }
        IBlockState state = this.world.getBlockState(this.pos);
        if (state.getBlock() instanceof EnergyCellBlock) {
            this.currentDisplayLevel = (byte) state.getValue(EnergyCellBlock.ENERGY_STORAGE).intValue();
        } else {
            this.currentDisplayLevel = (byte) getStorageLevelFromFillFactor(
                this.stored.getAmount() / this.stored.getMaximum());
        }
        this.updateStateForPowerLevel();
    }

    @Override
    public ItemStack getItemFromTile() {
        IBlockState state = this.getBlockState();
        if (state != null && state.getBlock() == AEBlocks.DENSE_ENERGY_CELL.block()) {
            return AEBlocks.DENSE_ENERGY_CELL.stack();
        }
        return AEBlocks.ENERGY_CELL.stack();
    }

    private void updateStateForPowerLevel() {
        if (this.isInvalid()) {
            return;
        }

        int storageLevel = getStorageLevelFromFillFactor(this.stored.getAmount() / this.stored.getMaximum());
        if (this.currentDisplayLevel != storageLevel) {
            this.currentDisplayLevel = (byte) storageLevel;
            this.markForUpdate();
        }
    }

    private double getConfiguredMaxPower() {
        if (this.world == null) {
            return 200000.0;
        }

        IBlockState state = this.world.getBlockState(this.pos);
        if (state.getBlock() instanceof EnergyCellBlock) {
            return ((EnergyCellBlock) state.getBlock()).getMaxPower();
        }
        return 200000.0;
    }

    private void setChangedNoTicketUpdate() {
        if (this.world != null) {
            this.world.getChunk(this.pos).markDirty();
        }
    }

    private void onAmountChanged() {
        setChangedNoTicketUpdate();

        if (!neighborChangePending) {
            neighborChangePending = true;
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    private void setStoredEnergy(double maximum, double amount) {
        double previousMaximum = this.stored.getMaximum();
        double previousAmount = this.stored.getAmount();
        boolean previouslySuppressed = this.suppressPowerStateEvent;
        this.suppressPowerStateEvent = true;
        try {
            this.stored.setMaximum(maximum);
            this.stored.setStored(amount);
        } finally {
            this.suppressPowerStateEvent = previouslySuppressed;
        }

        if (Double.compare(previousMaximum, this.stored.getMaximum()) != 0) {
            emitPowerStorageChanged(ChangeType.ROUTING_CHANGED);
        } else if (Double.compare(previousAmount, this.stored.getAmount()) != 0) {
            emitPowerStorageChanged(ChangeType.VALUES_CHANGED);
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        data.setDouble(INTERNAL_CURRENT_POWER_TAG, this.stored.getAmount());
        data.setDouble(INTERNAL_MAX_POWER_TAG, this.stored.getMaximum());
        data.setBoolean("neighborChangePending", this.neighborChangePending);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        double storedEnergy = data.getDouble(INTERNAL_CURRENT_POWER_TAG);
        setStoredEnergy(Math.max(readSavedMaxPower(data, this.stored.getMaximum()), storedEnergy), storedEnergy);
        this.neighborChangePending = data.getBoolean("neighborChangePending");
    }

    @Override
    public void importSettings(SettingsFrom mode, NBTTagCompound input, @Nullable EntityPlayer player) {
        super.importSettings(mode, input, player);

        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            double storedEnergy = readStoredEnergy(input);
            setStoredEnergy(Math.max(readMaxPower(input, this.getConfiguredMaxPower()), storedEnergy), storedEnergy);
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, NBTTagCompound output) {
        super.exportSettings(mode, output);

        if (mode == SettingsFrom.DISMANTLE_ITEM && this.stored.getAmount() > 0) {
            output.setDouble(STORED_ENERGY, this.stored.getAmount());
            output.setDouble(INTERNAL_CURRENT_POWER_TAG, this.stored.getAmount());
            output.setDouble(INTERNAL_MAX_POWER_TAG, this.stored.getMaximum());
        }
    }

    @Override
    public final double injectAEPower(double amt, Actionable mode) {
        var inserted = this.stored.insert(amt, mode == Actionable.MODULATE);
        if (mode == Actionable.MODULATE && inserted > 0) {
            this.onAmountChanged();
        }
        return amt - inserted;
    }

    @Override
    public final double extractAEPower(double amt, Actionable mode, PowerMultiplier pm) {
        return pm.divide(this.extractAEPower(pm.multiply(amt), mode));
    }

    private double extractAEPower(double amt, Actionable mode) {
        var extracted = this.stored.extract(amt, mode == Actionable.MODULATE);
        if (mode == Actionable.MODULATE && extracted > 0) {
            this.onAmountChanged();
        }
        return extracted;
    }

    @Override
    public double getAEMaxPower() {
        return this.stored.getMaximum();
    }

    @Override
    public double getAECurrentPower() {
        return this.stored.getAmount();
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
    public int getPriority() {
        if (this.world == null) {
            return 0;
        }
        IBlockState state = this.world.getBlockState(this.pos);
        if (state.getBlock() instanceof EnergyCellBlock) {
            return ((EnergyCellBlock) state.getBlock()).getPriority();
        }
        return 0;
    }

    private void emitPowerEvent(PowerEventType type) {
        if (!this.suppressPowerStateEvent) {
            getMainNode().ifPresent(grid -> grid.postEvent(new GridPowerStorageStateChanged(this, type)));
        }
    }

    private void emitPowerStorageChanged(ChangeType type) {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            grid.postEvent(new GridPowerStorageChanged(this, type));
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, !neighborChangePending);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (Platform.areBlockEntitiesTicking(getWorld(), getPos())) {
            if (neighborChangePending) {
                neighborChangePending = false;
                markDirty();
                updateStateForPowerLevel();
            }
            return TickRateModulation.SLEEP;
        }
        return TickRateModulation.IDLE;
    }
}
