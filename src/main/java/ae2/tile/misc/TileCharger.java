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

import ae2.api.AECapabilities;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.config.PowerUnit;
import ae2.api.implementations.blockentities.ICrankable;
import ae2.api.implementations.items.IAEItemPowerStorage;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.IGridNode;
import ae2.api.networking.ticking.IGridTickable;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.networking.ticking.TickingRequest;
import ae2.api.orientation.BlockOrientation;
import ae2.api.orientation.RelativeSide;
import ae2.api.util.AECableType;
import ae2.core.AELog;
import ae2.core.definitions.AEBlocks;
import ae2.core.settings.TickRates;
import ae2.tile.grid.AENetworkedPoweredTile;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.filter.IAEItemFilter;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.util.EnumSet;

public class TileCharger extends AENetworkedPoweredTile implements IGridTickable {
    public static final int POWER_MAXIMUM_AMOUNT = 1600;
    private static final int POWER_THRESHOLD = POWER_MAXIMUM_AMOUNT - 1;
    private static final int POWER_PER_CRANK_TURN = 160;

    private final AppEngInternalInventory inv = new AppEngInternalInventory(this, 1, 1, new ChargerInvFilter());
    private final ICrankable crankable = new Crankable();
    private boolean working;
    private ItemStack clientItem = ItemStack.EMPTY;

    public TileCharger() {
        this.setInternalMaxPower(POWER_MAXIMUM_AMOUNT);
        this.setPowerSides(getGridConnectableSides(getOrientation()));
        this.getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class, this);
    }

    @Override
    public ItemStack getItemFromTile() {
        return AEBlocks.CHARGER.stack();
    }

    @Override
    public AECableType getCableConnectionType(EnumFacing dir) {
        return AECableType.COVERED;
    }

    @Override
    public EnumSet<EnumFacing> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT)));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        this.setPowerSides(getGridConnectableSides(orientation));
    }

    @Override
    public InternalInventory getInternalInventory() {
        return this.inv;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        this.markForUpdate();
    }

    @Override
    protected void writeToStream(ByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.working);
        new PacketBuffer(data).writeItemStack(this.inv.getStackInSlot(0));
    }

    @Override
    protected boolean readFromStream(ByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean wasWorking = this.working;
        this.working = data.readBoolean();
        ItemStack oldItem = this.clientItem;
        try {
            this.clientItem = new PacketBuffer(data).readItemStack();
        } catch (IOException e) {
            AELog.warn(e, "Failed to read charger item from update stream");
            this.clientItem = ItemStack.EMPTY;
        }
        return changed || wasWorking != this.working || !ItemStack.areItemStacksEqual(oldItem, this.clientItem);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.Charger, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        doWork(ticksSinceLastCall);
        return TickRateModulation.FASTER;
    }

    private void doWork(int ticksSinceLastCall) {
        boolean wasWorking = this.working;
        this.working = false;
        boolean changed = false;

        ItemStack stack = this.inv.getStackInSlot(0);
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof IAEItemPowerStorage powerStorage) {
                double currentPower = powerStorage.getAECurrentPower(stack);
                double maxPower = powerStorage.getAEMaxPower(stack);
                if (currentPower < maxPower) {
                    double chargeRate = powerStorage.getChargeRate(stack) * ticksSinceLastCall;
                    double extractedAmount = this.extractAEPower(chargeRate, Actionable.MODULATE, PowerMultiplier.CONFIG);

                    double missingChargeRate = chargeRate - extractedAmount;
                    double missingAEPower = maxPower - currentPower;
                    double toExtract = Math.min(missingChargeRate, missingAEPower);

                    var grid = this.getMainNode().getGrid();
                    if (grid != null) {
                        extractedAmount += grid.getEnergyService().extractAEPower(toExtract, Actionable.MODULATE,
                            PowerMultiplier.ONE);
                    }

                    if (extractedAmount > 0) {
                        double remainder = powerStorage.injectAEPower(stack, extractedAmount, Actionable.MODULATE);
                        this.setInternalCurrentPower(this.getInternalCurrentPower() + remainder);
                        this.working = true;
                        changed = true;
                    }
                }
            } else {
                var recipe = ChargerRecipes.findRecipe(stack);
                if (recipe != null && this.getInternalCurrentPower() >= POWER_THRESHOLD) {
                    this.working = true;
                    if (this.world != null && this.world.rand.nextFloat() > 0.8f) {
                        this.extractAEPower(this.getInternalMaxPower(), Actionable.MODULATE, PowerMultiplier.CONFIG);
                        this.inv.setItemDirect(0, recipe.getResultItem());
                        changed = true;
                    }
                }
            }
        }

        if (this.getInternalCurrentPower() < POWER_THRESHOLD) {
            var grid = this.getMainNode().getGrid();
            if (grid != null) {
                double toExtract = Math.min(800.0, this.getInternalMaxPower() - this.getInternalCurrentPower());
                double extracted = grid.getEnergyService().extractAEPower(toExtract, Actionable.MODULATE,
                    PowerMultiplier.ONE);
                this.injectExternalPower(PowerUnit.AE, extracted, Actionable.MODULATE);
                changed = changed || extracted > 0;
            }
        }

        if (changed) {
            this.saveChanges();
        }
        if (changed || this.working != wasWorking) {
            this.markForUpdate();
        }
    }

    public ItemStack getClientDisplayItem() {
        return this.isClientSide() ? this.clientItem : this.inv.getStackInSlot(0);
    }

    public boolean isWorking() {
        return this.working;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.CRANKABLE && getCrankable(facing) != null) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == AECapabilities.CRANKABLE) {
            return (T) getCrankable(facing);
        }
        return super.getCapability(capability, facing);
    }

    @Nullable
    protected ICrankable getCrankable(@Nullable EnumFacing side) {
        if (side != null && side != this.getOrientation().getSide(RelativeSide.FRONT)) {
            return this.crankable;
        }
        return null;
    }

    private static final class ChargerInvFilter implements IAEItemFilter {
        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return stack.getItem() instanceof IAEItemPowerStorage || ChargerRecipes.allowInsert(stack);
        }

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            ItemStack extractedItem = inv.getStackInSlot(slot);

            if (extractedItem.getItem() instanceof IAEItemPowerStorage powerStorage
                && powerStorage.getAECurrentPower(extractedItem) >= powerStorage.getAEMaxPower(extractedItem)) {
                return true;
            }

            return ChargerRecipes.allowExtract(extractedItem);
        }
    }

    private final class Crankable implements ICrankable {
        @Override
        public boolean canTurn() {
            return getInternalCurrentPower() < getInternalMaxPower();
        }

        @Override
        public void applyTurn() {
            injectExternalPower(PowerUnit.AE, POWER_PER_CRANK_TURN, Actionable.MODULATE);
        }
    }
}
