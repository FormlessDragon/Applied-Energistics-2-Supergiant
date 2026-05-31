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

import ae2.api.parts.IPartItem;
import ae2.api.parts.IPartModel;
import ae2.api.stacks.AEItemKey;
import ae2.api.storage.ISubGuiHost;
import ae2.api.storage.StorageHelper;
import ae2.container.ISubGui;
import ae2.container.implementations.ContainerCraftAmount;
import ae2.core.AppEng;
import ae2.core.gui.locator.GuiHostLocators;
import ae2.items.parts.PartModels;
import ae2.me.helpers.PlayerSource;
import ae2.parts.PartModel;
import ae2.util.InteractionUtil;
import ae2.util.Platform;
import ae2.util.inv.PlayerInternalInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

public class ConversionMonitorPart extends AbstractMonitorPart implements ISubGuiHost {

    @PartModels
    public static final ResourceLocation MODEL_OFF = AppEng.makeId("part/conversion_monitor_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = AppEng.makeId("part/conversion_monitor_on");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_OFF = AppEng.makeId("part/conversion_monitor_locked_off");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_ON = AppEng.makeId("part/conversion_monitor_locked_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);
    public static final IPartModel MODELS_LOCKED_OFF = new PartModel(MODEL_BASE, MODEL_LOCKED_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_LOCKED_ON = new PartModel(MODEL_BASE, MODEL_LOCKED_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_LOCKED_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_LOCKED_ON,
        MODEL_STATUS_HAS_CHANNEL);

    public ConversionMonitorPart(IPartItem<?> partItem) {
        super(partItem, true);
    }

    @Override
    public boolean onUseItemOn(ItemStack heldItem, EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (isClientSide()) {
            return true;
        }

        if (!this.getMainNode().isActive()) {
            return false;
        }

        if (this.isLocked() && !InteractionUtil.isInAlternateUseMode(player)) {
            if (InteractionUtil.canWrenchRotate(heldItem)
                && (this.getDisplayed() == null || !AEItemKey.matches(this.getDisplayed(), heldItem))) {
                return super.onUseWithoutItem(player, pos);
            } else if (!heldItem.isEmpty()) {
                this.insertItem(player, heldItem);
                return true;
            }
        } else if (this.getDisplayed() != null && AEItemKey.matches(this.getDisplayed(), heldItem)) {
            this.insertItem(player, heldItem);
            return true;
        }

        return super.onUseItemOn(heldItem, player, hand, pos);
    }

    @Override
    public boolean onUseWithoutItem(EntityPlayer player, Vec3d pos) {
        if (this.isLocked() && !InteractionUtil.isInAlternateUseMode(player)) {
            if (isClientSide()) {
                return true;
            }
            if (!this.getMainNode().isActive()) {
                return false;
            }
            this.insertAllItem(player);
            return true;
        }

        return super.onUseWithoutItem(player, pos);
    }

    @Override
    public boolean onClicked(EntityPlayer player, Vec3d pos) {
        if (isClientSide()) {
            return this.isActive() && this.getDisplayed() instanceof AEItemKey;
        }

        if (!this.getMainNode().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getHost().getLocation(), player)) {
            return false;
        }

        if (this.getDisplayed() instanceof AEItemKey itemKey) {
            this.extractItem(player, itemKey.getMaxStackSize());
            return true;
        }

        return false;
    }

    @Override
    public boolean onShiftClicked(EntityPlayer player, Vec3d pos) {
        if (isClientSide()) {
            return this.isActive() && this.getDisplayed() instanceof AEItemKey;
        }

        if (!this.getMainNode().isActive()) {
            return false;
        }

        if (!Platform.hasPermissions(this.getHost().getLocation(), player)) {
            return false;
        }

        if (this.getDisplayed() instanceof AEItemKey) {
            this.extractItem(player, 1);
            return true;
        }

        return false;
    }

    private void insertAllItem(EntityPlayer player) {
        getMainNode().ifPresent(grid -> {
            var energy = grid.getEnergyService();
            var cell = grid.getStorageService().getInventory();

            if (getDisplayed() instanceof AEItemKey itemKey) {
                var inv = new PlayerInternalInventory(player.inventory);

                for (int x = 0; x < inv.size(); x++) {
                    var targetStack = inv.getStackInSlot(x);
                    if (itemKey.matches(targetStack)) {
                        var canExtract = inv.extractItem(x, targetStack.getCount(), true);
                        if (!canExtract.isEmpty()) {
                            var inserted = StorageHelper.poweredInsert(energy, cell, itemKey, canExtract.getCount(),
                                new PlayerSource(player, this));
                            inv.extractItem(x, (int) inserted, false);
                        }
                    }
                }
            }
        });
    }

    private void insertItem(EntityPlayer player, ItemStack heldItem) {
        getMainNode().ifPresent(grid -> {
            var energy = grid.getEnergyService();
            var cell = grid.getStorageService().getInventory();

            var inserted = StorageHelper.poweredInsert(energy, cell, AEItemKey.of(heldItem), heldItem.getCount(),
                new PlayerSource(player, this));
            heldItem.shrink((int) inserted);
        });
    }

    private void extractItem(EntityPlayer player, int count) {
        if (!(this.getDisplayed() instanceof AEItemKey itemKey)) {
            return;
        }

        if (!this.getMainNode().isActive()) {
            return;
        }

        if (getAmount() == 0 && canCraft()) {
            ContainerCraftAmount.open((EntityPlayerMP) player, GuiHostLocators.forPart(this), itemKey, itemKey.getAmountPerUnit());
            return;
        }

        getMainNode().ifPresent(grid -> {
            var energy = grid.getEnergyService();
            var cell = grid.getStorageService().getInventory();

            var retrieved = StorageHelper.poweredExtraction(energy, cell, itemKey, count, new PlayerSource(player, this));
            if (retrieved != 0) {
                var newItems = itemKey.toStack((int) retrieved);
                if (!player.inventory.addItemStackToInventory(newItems)) {
                    player.dropItem(newItems, false);
                }

                if (player.openContainer != null) {
                    player.openContainer.detectAndSendChanges();
                }
            }
        });
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL, MODELS_LOCKED_OFF, MODELS_LOCKED_ON,
            MODELS_LOCKED_HAS_CHANNEL);
    }

    @Override
    public void returnToMainContainer(EntityPlayer player, ISubGui subGui) {
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).closeContainer();
        } else {
            player.closeScreen();
        }
    }

    @Override
    public ItemStack getMainContainerIcon() {
        return getPartItem().asItemStack();
    }
}
