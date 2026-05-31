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

package ae2.parts.reporting;

import ae2.api.config.Settings;
import ae2.api.config.SortDir;
import ae2.api.config.SortOrder;
import ae2.api.config.ViewItems;
import ae2.api.implementations.blockentities.IViewCellStorage;
import ae2.api.inventories.InternalInventory;
import ae2.api.parts.IPartItem;
import ae2.api.storage.ILinkStatus;
import ae2.api.storage.ITerminalHost;
import ae2.api.storage.MEStorage;
import ae2.api.storage.SupplierStorage;
import ae2.api.util.IConfigManager;
import ae2.api.util.IConfigManagerBuilder;
import ae2.api.util.KeyTypeSelection;
import ae2.api.util.KeyTypeSelectionHost;
import ae2.container.GuiIds;
import ae2.container.ISubGui;
import ae2.core.gui.GuiOpener;
import ae2.util.inv.AppEngInternalInventory;
import ae2.util.inv.InternalInventoryHost;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

import java.util.List;

public abstract class AbstractTerminalPart extends AbstractDisplayPart
    implements ITerminalHost, IViewCellStorage, InternalInventoryHost, KeyTypeSelectionHost {

    private final IConfigManager cm;
    private final KeyTypeSelection keyTypeSelection = new KeyTypeSelection(this::saveChanges, keyType -> true);
    private final AppEngInternalInventory viewCell = new AppEngInternalInventory(this, 5);

    public AbstractTerminalPart(IPartItem<?> partItem) {
        super(partItem, true);

        IConfigManagerBuilder builder = IConfigManager.builder(this::saveChanges);
        registerSettings(builder);
        this.cm = builder.build();
    }

    @MustBeInvokedByOverriders
    protected void registerSettings(IConfigManagerBuilder builder) {
        builder.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        builder.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        builder.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack is : this.viewCell) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.viewCell.clear();
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    public void saveChanges() {
        this.getHost().markForSave();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.cm.readFromNBT(data);
        this.keyTypeSelection.readFromNBT(data);
        this.viewCell.readFromNBT(data, "viewCell");
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        this.cm.writeToNBT(data);
        this.keyTypeSelection.writeToNBT(data);
        this.viewCell.writeToNBT(data, "viewCell");
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.world.isRemote) {
            GuiOpener.openPartGui(player, getGuiKey(player), this);
        }
        return true;
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (super.onUseItemOn(heldItem, player, hand, pos)) {
            return true;
        }
        return this.onUseWithoutItem(player, pos);
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        GuiOpener.openPartGui(player, getGuiKey(player), this, true);
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return getPartItem().asItemStack();
    }

    public GuiIds.GuiKey getGuiKey(EntityPlayer player) {
        return GuiIds.GuiKey.ME_STORAGE_TERMINAL;
    }

    @Override
    public MEStorage getInventory() {
        return new SupplierStorage(() -> {
            var grid = getMainNode().getGrid();
            if (grid != null) {
                return grid.getStorageService().getInventory();
            }
            return null;
        });
    }

    @Override
    public ILinkStatus getLinkStatus() {
        return ILinkStatus.ofManagedNode(getMainNode());
    }

    @Override
    public InternalInventory getViewCellStorage() {
        return this.viewCell;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        this.getHost().markForSave();
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.keyTypeSelection;
    }
}
