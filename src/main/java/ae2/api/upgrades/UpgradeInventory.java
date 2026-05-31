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

package ae2.api.upgrades;

import ae2.api.inventories.InternalInventory;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import ae2.util.inv.filter.IAEItemFilter;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

abstract class UpgradeInventory extends AppEngInternalInventory implements InternalInventoryHost, IUpgradeInventory {
    private final Item item;

    // Cache of which upgrades are installed
    @Nullable
    private Reference2IntMap<Item> installed = null;

    public UpgradeInventory(Item item, int slots) {
        super(null, slots, 1);
        this.item = item;
        this.setHost(this);
        this.setFilter(new UpgradeInvFilter());
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    protected boolean eventsEnabled() {
        return true;
    }

    @Override
    public int getMaxInstalled(Item upgradeCard) {
        return Upgrades.getMaxInstallable(upgradeCard, item);
    }

    @Override
    public Item getUpgradableItem() {
        return item;
    }

    @Override
    public int getInstalledUpgrades(Item upgradeCard) {
        if (installed == null) {
            this.updateUpgradeInfo();
        }

        return installed.getOrDefault(upgradeCard, 0);
    }

    private void updateUpgradeInfo() {
        this.installed = new Reference2IntArrayMap<>(size());

        for (var is : this) {
            var maxInstalled = getMaxInstalled(is.getItem());
            if (maxInstalled > 0) {
                this.installed.merge(is.getItem(), 1, (a, b) -> Math.min(maxInstalled, a + b));
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data, String name) {
        super.readFromNBT(data, name);
        this.updateUpgradeInfo();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.installed = null;
    }

    @Override
    public void sendChangeNotification(int slot) {
        this.installed = null;
        super.sendChangeNotification(slot);
    }

    private class UpgradeInvFilter implements IAEItemFilter {

        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return IAEItemFilter.super.allowExtract(inv, slot, amount);
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack itemstack) {
            var cardItem = itemstack.getItem();
            return getInstalledUpgrades(cardItem) < getMaxInstalled(cardItem);
        }
    }
}
