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

package ae2.items.storage;

import ae2.api.config.FuzzyMode;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.AEKeyFilter;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.upgrades.IUpgradeInventory;
import ae2.api.upgrades.UpgradeInventories;
import ae2.container.AEBaseContainer;
import ae2.container.SlotSemantics;
import ae2.container.slot.AppEngSlot;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.items.AEBaseItem;
import ae2.items.contents.CellConfig;
import ae2.util.ConfigInventory;
import ae2.util.prioritylist.FuzzyPriorityList;
import ae2.util.prioritylist.IPartitionList;
import ae2.util.prioritylist.MergedPriorityList;
import ae2.util.prioritylist.PrecisePriorityList;
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
            ? TextFormatting.GREEN + GuiText.Yes.getLocal()
            : TextFormatting.RED + GuiText.No.getLocal();
        lines.add(TextFormatting.GRAY + GuiText.ViewCellEnabled.getLocal(status));
        lines.add(TextFormatting.DARK_GRAY + GuiText.ViewCellToggleHint.getLocal());
    }
}
