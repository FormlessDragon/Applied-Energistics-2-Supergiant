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

import ae2.api.config.CopyMode;
import ae2.api.config.Settings;
import ae2.api.inventories.ISegmentedInventory;
import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigurableObject;
import ae2.helpers.externalstorage.GenericStackInv;
import ae2.tile.AEBaseTile;
import ae2.util.ConfigInventory;
import ae2.util.ConfigManager;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

public class TileCellWorkbench extends AEBaseTile
    implements IUpgradeableObject, IConfigurableObject, InternalInventoryHost {

    private final AppEngInternalInventory cell = new AppEngInternalInventory(this, 1);
    private final ConfigManager manager = new ConfigManager(this::saveChanges);
    @Nullable
    private IUpgradeInventory cacheUpgrades;
    @Nullable
    private ConfigInventory cacheConfig;
    private boolean locked;
    private ConfigInventory config = createConfigInventory(63);

    public TileCellWorkbench() {
        this.manager.registerSetting(Settings.COPY_MODE, CopyMode.CLEAR_ON_REMOVE);
        this.cell.setEnableClientEvents(true);
    }

    public static void copy(GenericStackInv from, GenericStackInv to) {
        for (int i = 0; i < Math.min(from.size(), to.size()); ++i) {
            GenericStack stack = from.getStack(i);
            if (stack != null && !to.isAllowedIn(i, stack.what())) {
                stack = null;
            }
            to.setStack(i, stack);
        }
        for (int i = from.size(); i < to.size(); i++) {
            to.setStack(i, null);
        }
    }

    private ConfigInventory createConfigInventory(int size) {
        return ConfigInventory.configTypes(Math.max(0, size))
                              .changeListener(this::configChanged)
                              .build();
    }

    @Nullable
    public ICellWorkbenchItem getCell() {
        ItemStack stack = this.cell.getStackInSlot(0);
        return !stack.isEmpty() && stack.getItem() instanceof ICellWorkbenchItem
            ? (ICellWorkbenchItem) stack.getItem()
            : null;
    }

    public GenericStackInv getConfig() {
        ensureConfigSizeForCurrentCell();
        return this.config;
    }

    @Override
    public void saveAdditional(NBTTagCompound data) {
        super.saveAdditional(data);
        this.cell.writeToNBT(data, "cell");
        this.config.writeToChildTag(data, "config");
        this.manager.writeToNBT(data);
    }

    @Override
    public void loadTag(NBTTagCompound data) {
        super.loadTag(data);
        this.cell.readFromNBT(data, "cell");
        this.manager.readFromNBT(data);
        ensureConfigSizeForCurrentCell();
        this.config.readFromChildTag(data, "config");
    }

    @Nullable
    public ae2.api.inventories.InternalInventory getSubInventory(ResourceLocation id) {
        if (ISegmentedInventory.CELLS.equals(id)) {
            return this.cell;
        }
        return null;
    }

    @Override
    public boolean isClientSide() {
        return this.getWorld() != null && this.getWorld().isRemote;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (inv == this.cell && !this.locked) {
            this.locked = true;
            try {
                this.cacheUpgrades = null;
                this.cacheConfig = null;

                ConfigInventory configInventory = this.getCellConfigInventory();
                this.resizeConfig(configInventory != null ? configInventory.size() : 63);
                if (configInventory != null) {
                    if (!configInventory.isEmpty()) {
                        copy(configInventory, this.config);
                    } else {
                        copy(this.config, configInventory);
                        copy(configInventory, this.config);
                    }
                } else if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.CLEAR_ON_REMOVE) {
                    this.config.clear();
                    saveChanges();
                }
            } finally {
                this.locked = false;
            }
        }
    }

    private void configChanged() {
        if (locked) {
            return;
        }

        this.locked = true;
        try {
            ConfigInventory c = this.getCellConfigInventory();
            if (c != null) {
                copy(this.config, c);
                copy(c, this.config);
            }
        } finally {
            this.locked = false;
        }
    }

    private void ensureConfigSizeForCurrentCell() {
        ConfigInventory configInventory = this.getCellConfigInventory();
        resizeConfig(configInventory != null ? configInventory.size() : 63);
    }

    private void resizeConfig(int size) {
        size = Math.max(0, size);
        if (this.config.size() == size) {
            return;
        }

        ConfigInventory oldConfig = this.config;
        this.config = createConfigInventory(size);
        boolean wasLocked = this.locked;
        this.locked = true;
        try {
            copy(oldConfig, this.config);
        } finally {
            this.locked = wasLocked;
        }
    }

    @Nullable
    private ConfigInventory getCellConfigInventory() {
        if (this.cacheConfig == null) {
            ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return null;
            }

            ItemStack stack = this.cell.getStackInSlot(0);
            if (stack.isEmpty()) {
                return null;
            }

            ConfigInventory inv = cell.getConfigInventory(stack);
            if (inv == null) {
                return null;
            }

            this.cacheConfig = inv;
        }
        return this.cacheConfig;
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops) {
        super.addAdditionalDrops(drops);
        ItemStack stack = this.cell.getStackInSlot(0);
        if (!stack.isEmpty()) {
            drops.add(stack.copy());
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.cell.clear();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        if (this.cacheUpgrades == null) {
            ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return UpgradeInventories.empty();
            }

            ItemStack stack = this.cell.getStackInSlot(0);
            if (stack.isEmpty()) {
                return UpgradeInventories.empty();
            }

            IUpgradeInventory upgrades = cell.getUpgrades(stack);
            if (upgrades == null) {
                return UpgradeInventories.empty();
            }

            this.cacheUpgrades = upgrades;
        }
        return this.cacheUpgrades;
    }




}
