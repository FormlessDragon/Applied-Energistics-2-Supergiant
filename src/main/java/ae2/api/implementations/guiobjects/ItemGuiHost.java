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

package ae2.api.implementations.guiobjects;

import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.energy.IEnergySource;
import ae2.api.stacks.AEKey;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.core.gui.locator.ItemGuiHostLocator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for an adapter that connects an item stack in a player inventory with a container that is opened by it.
 */
public class ItemGuiHost<T extends Item> implements IUpgradeableObject {

    /**
     * To avoid changing the item stack once every tick, we consume idle power for more than just one tick at a time.
     * The default is to consume power twice per second.
     */
    private static final int BUFFER_ENERGY_TICKS = 10;

    private final T item;
    private final EntityPlayer player;
    private final ItemGuiHostLocator locator;
    private final IUpgradeInventory upgrades;
    private int remainingEnergyTicks = 0;

    public ItemGuiHost(T item, EntityPlayer player, ItemGuiHostLocator locator) {
        this.player = player;
        this.locator = locator;
        this.item = item;
        var currentStack = getItemStack();
        if (currentStack.isEmpty() || currentStack.getItem() != item) {
            throw new IllegalArgumentException("The current item in-slot is " + currentStack.getItem() + " but " +
                "this container requires " + item);
        }
        this.upgrades = new DelegateItemUpgradeInventory(this::getItemStack);
    }

    /**
     * @return The player holding the item.
     */
    public EntityPlayer getPlayer() {
        return player;
    }

    /**
     * @return The index of the item hosting the container in the {@link #getPlayer() players} inventory. Null if the item is
     * not directly accessible via the inventory.
     */
    @Nullable
    public Integer getPlayerInventorySlot() {
        return locator.getPlayerInventorySlot();
    }

    @Nullable
    public ItemGuiHostLocator getLocator() {
        return locator;
    }

    public T getItem() {
        return item;
    }

    /**
     * @return The item stack hosting the container. This can change.
     */
    public ItemStack getItemStack() {
        return locator.locateItem(player);
    }

    /**
     * @return True if this host is on the client-side.
     */
    public boolean isClientSide() {
        return player.world.isRemote;
    }

    /**
     * Gives the item hosting the GUI a chance to do periodic actions when the container is being ticked.
     */
    public void tick() {
    }

    /**
     * Checks if the item underlying this host is still in place.
     */
    public boolean isValid() {
        var currentItem = getItemStack();
        return !currentItem.isEmpty() && currentItem.getItem() == item;
    }

    private static int calculateRemainingEnergyTicks(double actualExtracted, double powerDrainPerTick) {
        var ticks = Math.ceil(actualExtracted / powerDrainPerTick);
        if (!Double.isFinite(ticks)) {
            return ticks > 0 ? Integer.MAX_VALUE : 0;
        }
        if (ticks <= 0) {
            return 0;
        }
        return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    /**
     * Can only be used with a host that implements {@link IEnergySource}.
     */
    public boolean consumeIdlePower(Actionable action) {
        // Do not drain power for creative players
        if (player.capabilities.isCreativeMode) {
            return true;
        }

        // Remaining charge
        if (remainingEnergyTicks > 0) {
            if (action == Actionable.MODULATE) {
                remainingEnergyTicks--;
            }
            return true;
        }

        var powerDrainPerTick = getPowerDrainPerTick();
        if (powerDrainPerTick > 0 && this instanceof IEnergySource energySource) {
            var amt = BUFFER_ENERGY_TICKS * powerDrainPerTick;
            var actualExtracted = energySource.extractAEPower(amt, action, PowerMultiplier.CONFIG);
            var remainingEnergyTicks = calculateRemainingEnergyTicks(actualExtracted, powerDrainPerTick);
            if (action == Actionable.MODULATE) {
                this.remainingEnergyTicks = remainingEnergyTicks;
            }

            // Return true if we drained enough energy to last one tick
            return remainingEnergyTicks > 0;
        }

        // If no power is being drained, we're never out of power
        return true;
    }

    /**
     * Get power drain per tick.
     */
    protected double getPowerDrainPerTick() {
        return 0.5;
    }

    @Override
    public final IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    /**
     * Insert something into the host of this container (i.e. by dropping onto the hosting item in the player inventory or by
     * similar mechanisms).
     *
     * @return The amount that was inserted.
     */
    public long insert(EntityPlayer player, AEKey what, long amount, Actionable mode) {
        return 0;
    }
}
