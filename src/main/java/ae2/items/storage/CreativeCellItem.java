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
import ae2.api.storage.StorageCells;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.IStackTooltipDataProvider;
import ae2.core.localization.GuiText;
import ae2.items.AEBaseItem;
import ae2.items.contents.CellConfig;
import ae2.me.cells.CreativeCellHandler;
import ae2.me.cells.CreativeCellInventory;
import ae2.util.ConfigInventory;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

public class CreativeCellItem extends AEBaseItem implements ICellWorkbenchItem, IStackTooltipDataProvider {
    public CreativeCellItem() {
        this.setMaxStackSize(1);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
    }

    @Override
    protected void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                         final ITooltipFlag advancedTooltips) {
        addToTooltip(stack, lines);
    }

    @Override
    public Optional<StorageCellTooltipComponent> getStackTooltipData(ItemStack stack) {
        return CreativeCellHandler.INSTANCE.getTooltipData(stack);
    }

    @Override
    public void addToTooltip(ItemStack stack, List<String> lines) {
        var inventory = StorageCells.getCellInventory(stack, null);
        if (!(inventory instanceof CreativeCellInventory)) {
            return;
        }

        var cc = getConfigInventory(stack);
        if (cc.isEmpty()) {
            return;
        }

        if (GuiScreen.isShiftKeyDown()) {
            for (var key : cc.keySet()) {
                lines.add(key.getDisplayName().getFormattedText());
            }
        } else {
            lines.add(GuiText.PressShiftForFullList.getLocal());
        }
    }
}



