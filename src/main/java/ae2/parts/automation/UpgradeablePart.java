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

package ae2.parts.automation;

import ae2.api.config.RedstoneMode;
import ae2.api.config.Setting;
import ae2.api.inventories.InternalInventory;
import ae2.api.networking.GridFlags;
import ae2.api.parts.IPartItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.IUpgradeableObject;
import ae2.api.upgrades.UpgradeInventories;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.api.util.IConfigurableObject;
import ae2.core.definitions.AEItems;
import ae2.core.definitions.ItemDefinition;
import ae2.parts.AEBasePart;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class UpgradeablePart extends AEBasePart implements IConfigurableObject, IUpgradeableObject {
    private final IConfigManager config;
    private final IUpgradeInventory upgrades;

    public UpgradeablePart(IPartItem<?> partItem) {
        super(partItem);
        this.upgrades = UpgradeInventories.forMachine(partItem.asItem(), this.getUpgradeSlots(),
            this::onUpgradesChanged);
        var configBuilder = IConfigManager.builder((manager, setting) -> {
            onSettingChanged(manager, setting);
            getHost().markForSave();
        });
        registerSettings(configBuilder);
        this.config = configBuilder.build();
        this.getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    @MustBeInvokedByOverriders
    protected void registerSettings(IConfigManagerBuilder builder) {

    }

    private void onUpgradesChanged() {
        getHost().markForSave();
        upgradesChanged();
    }

    protected int getUpgradeSlots() {
        return 4;
    }

    public void upgradesChanged() {

    }

    protected boolean isSleeping() {
        if (isUpgradedWith(AEItems.REDSTONE_CARD)) {
            return switch (this.getRSMode()) {
                case IGNORE -> false;
                case HIGH_SIGNAL -> !this.getHost().hasRedstone();
                case LOW_SIGNAL -> this.getHost().hasRedstone();
                case SIGNAL_PULSE -> true;
            };
        }

        return false;
    }

    @Override
    public boolean canConnectRedstone() {
        return this.getMaxInstalled() > 0;
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.config.readFromNBT(extra);
        this.upgrades.readFromNBT(extra, "upgrades");
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.config.writeToNBT(extra);
        this.upgrades.writeToNBT(extra, "upgrades");
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (var is : this.upgrades) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.config;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(UPGRADES)) {
            return upgrades;
        } else {
            return super.getSubInventory(id);
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    protected boolean isUpgradedWith(ItemDefinition<?> upgradeCard) {
        return this.upgrades.isInstalled(upgradeCard.asItem());
    }

    protected int getInstalledUpgrades(ItemDefinition<?> upgradeCard) {
        return this.upgrades.getInstalledUpgrades(upgradeCard.asItem());
    }

    protected int getMaxInstalled() {
        return this.upgrades.getMaxInstalled(((ItemDefinition<?>) AEItems.REDSTONE_CARD).asItem());
    }

    public @org.jspecify.annotations.Nullable RedstoneMode getRSMode() {
        return null;
    }

    protected void onSettingChanged(IConfigManager manager, Setting<?> setting) {
    }
}
