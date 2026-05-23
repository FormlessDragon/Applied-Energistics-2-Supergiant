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

package appeng.items.storage;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.container.AEBaseContainer;
import appeng.container.SlotSemantics;
import appeng.container.slot.AppEngSlot;
import appeng.core.definitions.AEItems;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellConfig;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.MergedPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Collection;
import java.util.List;

public class ViewCellItem extends AEBaseItem implements ICellWorkbenchItem {
    private static final String STORAGE_CELL_FUZZY_MODE = "storage_cell_fuzzy_mode";
    private static final String ENABLED_TAG = "view_cell_enabled";

    public ViewCellItem() {
        this.setMaxStackSize(1);
        if (FMLCommonHandler.instance().getSide().isClient()) {
            this.addPropertyOverride(new ResourceLocation("close"), (item, world, player) -> isEnabled(item) ? 0 : 1);
        }
    }

    public static IPartitionList createItemFilter(Collection<ItemStack> list) {
        return createFilter(AEItemKey.filter(), list);
    }

    public static IPartitionList createFilter(AEKeyFilter filter, Collection<ItemStack> list) {
        IPartitionList partitionList = null;
        MergedPriorityList mergedList = new MergedPriorityList();

        for (ItemStack currentViewCell : list) {
            if (currentViewCell == null) {
                continue;
            }

            if (currentViewCell.getItem() instanceof ViewCellItem vc) {
                if (!vc.isEnabled(currentViewCell)) {
                    continue;
                }

                KeyCounter priorityList = new KeyCounter();

                ConfigInventory config = vc.getConfigInventory(currentViewCell);
                FuzzyMode fuzzyMode = vc.getFuzzyMode(currentViewCell);

                for (int i = 0; i < config.size(); i++) {
                    var what = config.getKey(i);
                    if (what != null && filter.matches(what)) {
                        priorityList.add(what, 1);
                    }
                }

                if (!priorityList.isEmpty()) {
                    IUpgradeInventory upgrades = vc.getUpgrades(currentViewCell);
                    boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD.asItem());
                    if (upgrades.isInstalled(AEItems.FUZZY_CARD.asItem())) {
                        mergedList.addNewList(new FuzzyPriorityList(priorityList, fuzzyMode), !hasInverter);
                    } else {
                        mergedList.addNewList(new PrecisePriorityList(priorityList), !hasInverter);
                    }

                    partitionList = mergedList;
                }
            }
        }

        return partitionList;
    }

    public boolean isEnabled(ItemStack is) {
        NBTTagCompound tag = is.getTagCompound();
        return tag == null || !tag.hasKey(ENABLED_TAG) || tag.getBoolean(ENABLED_TAG);
    }

    public void setEnabled(ItemStack is, boolean enabled) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            is.setTagCompound(tag);
        }
        tag.setBoolean(ENABLED_TAG, enabled);
    }

    public void toggle(ItemStack is) {
        setEnabled(is, !isEnabled(is));
    }

    @Override
    public boolean onOtherStackedOnMe(ItemStack stack, ItemStack otherStack, Slot slot, EntityPlayer player) {
        if (!otherStack.isEmpty() || !(slot instanceof AppEngSlot appEngSlot)) {
            return false;
        }
        if (!(appEngSlot.getContainer() instanceof AEBaseContainer container)) {
            return false;
        }
        if (container.getSlotSemantic(slot) != SlotSemantics.VIEW_CELL) {
            return false;
        }

        toggle(stack);
        slot.onSlotChanged();
        return true;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 2);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag != null && tag.hasKey(STORAGE_CELL_FUZZY_MODE, 8)) {
            try {
                return FuzzyMode.valueOf(tag.getString(STORAGE_CELL_FUZZY_MODE));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        NBTTagCompound tag = is.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            is.setTagCompound(tag);
        }
        tag.setString(STORAGE_CELL_FUZZY_MODE, fzMode.name());
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, World world, List<String> lines,
                                         ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        String status = isEnabled(stack)
            ? TextFormatting.GREEN + I18n.format("gui.ae2.Yes")
            : TextFormatting.RED + I18n.format("gui.ae2.No");
        lines.add(TextFormatting.GRAY + I18n.format("item.ae2.view_cell.tooltip.enabled", status));
        lines.add(TextFormatting.DARK_GRAY + I18n.format("item.ae2.view_cell.tooltip.toggle_hint"));
    }
}
