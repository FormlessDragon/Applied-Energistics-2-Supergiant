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

package ae2.tile.misc;

import ae2.api.config.Actionable;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.upgrades.Upgrades;
import ae2.api.util.AECableType;
import ae2.core.AEConfig;
import ae2.core.definitions.AEBlocks;
import ae2.core.definitions.AEItems;
import ae2.core.settings.TickRates;
import ae2.tile.grid.AENetworkedInvTile;
import ae2.util.Platform;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.FilteredInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class TileVibrationChamber extends AENetworkedInvTile implements IGridTickable, IUpgradeableObject {
    private static final double BASE_SPEED_SCALE_TICKS = 100.0;

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 1);
    private final InternalInventory exposedInventory = new FilteredInternalInventory(this.inv, new FuelSlotFilter());
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AEBlocks.VIBRATION_CHAMBER.item(), 3,
        this::saveChanges);
    public boolean isOn;
    private double currentFuelTicksPerTick;
    private double remainingFuelTicks;
    private double fuelItemFuelTicks;

    public TileVibrationChamber() {
        this.getMainNode().setIdlePowerUsage(0).setFlags().addService(IGridTickable.class, this);
        this.currentFuelTicksPerTick = this.getInitialFuelTicksPerTick();
    }

    private static int getBurnTime(ItemStack stack) {
        return TileEntityFurnace.getItemBurnTime(stack);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.VIBRATION_CHAMBER.stack();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(EnumFacing facing) {
        return this.exposedInventory;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Nullable
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.STORAGE.equals(id)) {
            return this.inv;
        }
        if (ISegmentedInventory.UPGRADES.equals(id)) {
            return this.upgrades;
        }
        return null;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (this.remainingFuelTicks <= 0 && this.canEatFuel()) {
            var grid = this.getMainNode().getGrid();
            if (grid != null) {
                grid.getTickManager().wakeDevice(this.getMainNode().getNode());
            }
        }
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.upgrades.writeToNBT(data, "upgrades");
        data.setDouble("burnTime", this.getRemainingFuelTicks());
        data.setDouble("maxBurnTime", this.getFuelItemFuelTicks());
        data.setIntArray("burnSpeed", new int[]{
            (int) (this.currentFuelTicksPerTick * 100 / this.getMaxFuelTicksPerTick())
        });
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.upgrades.readFromNBT(data, "upgrades");
        this.setRemainingFuelTicks(data.getDouble("burnTime"));
        this.setFuelItemFuelTicks(data.getDouble("maxBurnTime"));
        int[] burnSpeed = data.getIntArray("burnSpeed");
        this.setCurrentFuelTicksPerTick(burnSpeed.length > 0 ? burnSpeed[0] * this.getMaxFuelTicksPerTick() / 100.0
            : this.getInitialFuelTicksPerTick());
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        for (var upgrade : this.upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        new PacketBuffer(data).writeBoolean(this.getRemainingFuelTicks() > 0);
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean wasOn = this.isOn;
        this.isOn = new PacketBuffer(data).readBoolean();
        return wasOn != this.isOn || changed;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        if (this.getRemainingFuelTicks() <= 0) {
            this.eatFuel();
        }
        return new TickingRequest(TickRates.VibrationChamber, this.getRemainingFuelTicks() <= 0);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.getRemainingFuelTicks() <= 0) {
            this.eatFuel();
            if (this.getRemainingFuelTicks() > 0) {
                return TickRateModulation.URGENT;
            }
            this.setCurrentFuelTicksPerTick(this.getInitialFuelTicksPerTick());
            return TickRateModulation.SLEEP;
        }

        double fuelTicksConsumed = ticksSinceLastCall * this.currentFuelTicksPerTick;
        this.setRemainingFuelTicks(this.getRemainingFuelTicks() - fuelTicksConsumed);
        if (this.getRemainingFuelTicks() < 0) {
            fuelTicksConsumed += this.getRemainingFuelTicks();
            this.setRemainingFuelTicks(0);
        }

        double speedStep = ticksSinceLastCall * this.getSpeedScalingPerTick();
        var grid = node.grid();
        if (grid == null) {
            return TickRateModulation.IDLE;
        }

        var energy = grid.getEnergyService();
        if (Math.abs(fuelTicksConsumed) < 0.01) {
            if (energy.injectPower(1, Actionable.SIMULATE) == 0) {
                this.setCurrentFuelTicksPerTick(this.getCurrentFuelTicksPerTick() + speedStep);
                return TickRateModulation.FASTER;
            }
            return TickRateModulation.IDLE;
        }

        double overflow = energy.injectPower(fuelTicksConsumed * this.getEnergyPerFuelTick(), Actionable.MODULATE);
        if (overflow > 0) {
            this.setCurrentFuelTicksPerTick(this.getCurrentFuelTicksPerTick() - speedStep);
        } else {
            this.setCurrentFuelTicksPerTick(this.getCurrentFuelTicksPerTick() + speedStep);
        }

        return overflow > 0 ? TickRateModulation.SLOWER : TickRateModulation.FASTER;
    }

    private boolean canEatFuel() {
        ItemStack stack = this.inv.getStackInSlot(0);
        return !stack.isEmpty() && getBurnTime(stack) > 0 && stack.getCount() > 0;
    }

    private void eatFuel() {
        ItemStack stack = this.inv.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }

        int burnTime = getBurnTime(stack);
        if (burnTime <= 0 || stack.getCount() <= 0) {
            return;
        }

        this.setRemainingFuelTicks(this.getRemainingFuelTicks() + burnTime);
        this.setFuelItemFuelTicks(this.getRemainingFuelTicks());

        Item fuelItem = stack.getItem();
        if (stack.getCount() <= 1) {
            this.inv.setItemDirect(0, fuelItem.getContainerItem(stack));
        } else {
            stack.shrink(1);
            this.inv.setItemDirect(0, stack);
        }
        this.saveChanges();

        if (this.getRemainingFuelTicks() > 0) {
            var grid = this.getMainNode().getGrid();
            if (grid != null) {
                grid.getTickManager().wakeDevice(this.getMainNode().getNode());
            }
        }

        if ((!this.isOn && this.getRemainingFuelTicks() > 0) || (this.isOn && this.getRemainingFuelTicks() <= 0)) {
            this.isOn = this.getRemainingFuelTicks() > 0;
            this.markForUpdate();
            if (this.world != null) {
                Platform.notifyBlocksOfNeighbors(this.world, this.pos);
            }
        }
    }

    public double getCurrentFuelTicksPerTick() {
        return this.currentFuelTicksPerTick;
    }

    private void setCurrentFuelTicksPerTick(double currentFuelTicksPerTick) {
        double minFuelTicksPerTick = Math.min(this.getMinFuelTicksPerTick(), this.getMaxFuelTicksPerTick());
        double maxFuelTicksPerTick = Math.max(this.getMinFuelTicksPerTick(), this.getMaxFuelTicksPerTick());
        this.currentFuelTicksPerTick = Math.clamp(currentFuelTicksPerTick, minFuelTicksPerTick, maxFuelTicksPerTick);
    }

    public double getMaxFuelTicksPerTick() {
        return this.getMaxEnergyRate() / this.getEnergyPerFuelTick();
    }

    public double getEnergyPerFuelTick() {
        return AEConfig.instance().getVibrationChamberBaseEnergyPerFuelTick()
            * (1 + Upgrades.getEnergyCardMultiplier(this.upgrades) / 2.0f);
    }

    public double getMinFuelTicksPerTick() {
        return AEConfig.instance().getVibrationChamberMinEnergyPerGameTick() / this.getEnergyPerFuelTick();
    }

    public double getMaxEnergyRate() {
        double baseMax = AEConfig.instance().getVibrationChamberMaxEnergyPerGameTick();
        return baseMax + baseMax * this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.item()) / 2.0f;
    }

    private double getInitialFuelTicksPerTick() {
        double minFuelTicksPerTick = Math.min(this.getMinFuelTicksPerTick(), this.getMaxFuelTicksPerTick());
        double maxFuelTicksPerTick = Math.max(this.getMinFuelTicksPerTick(), this.getMaxFuelTicksPerTick());
        return Math.clamp(AEConfig.instance().getVibrationChamberBaseEnergyPerFuelTick() / this.getEnergyPerFuelTick(),
            minFuelTicksPerTick, maxFuelTicksPerTick);
    }

    private double getSpeedScalingPerTick() {
        return (this.getMaxFuelTicksPerTick() - this.getMinFuelTicksPerTick()) / BASE_SPEED_SCALE_TICKS;
    }

    public double getRemainingFuelTicks() {
        return this.remainingFuelTicks;
    }

    private void setRemainingFuelTicks(double remainingFuelTicks) {
        this.remainingFuelTicks = remainingFuelTicks;
    }

    public double getFuelItemFuelTicks() {
        return this.fuelItemFuelTicks;
    }

    private void setFuelItemFuelTicks(double fuelItemFuelTicks) {
        this.fuelItemFuelTicks = fuelItemFuelTicks;
    }

    private static final class FuelSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return getBurnTime(stack) > 0;
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return !canStillBurn(inv.getStackInSlot(slot));
        }

        private boolean canStillBurn(ItemStack stack) {
            return getBurnTime(stack) > 0;
        }
    }
}
