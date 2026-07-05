/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package ae2.me.cells;

import ae2.api.stacks.GenericStack;
import ae2.api.storage.cells.IBasicCellItem;
import ae2.api.storage.cells.ICellHandler;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.ISaveProvider;
import ae2.core.AEConfig;
import ae2.core.definitions.AEItems;
import ae2.core.localization.GuiText;
import ae2.core.localization.Tooltips;
import ae2.items.storage.StorageCellTooltipComponent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class BasicCellHandler implements ICellHandler {
    public static final BasicCellHandler INSTANCE = new BasicCellHandler();

    @Override
    public boolean isCell(ItemStack stack) {
        return BasicCellInventory.isCell(stack);
    }

    @Nullable
    @Override
    public BasicCellInventory getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        return BasicCellInventory.createInventory(stack, host);
    }

    public void addCellInformationToTooltip(ItemStack stack, List<String> lines) {
        var inventory = BasicCellInventory.createInventory(stack, null);
        if (inventory == null) {
            return;
        }

        lines.add(Tooltips.bytesUsed(inventory.getUsedBytes(), inventory.getTotalBytes()).getFormattedText());
        lines.add(Tooltips.typesUsed(inventory.getStoredItemTypes(), inventory.getTotalItemTypes()).getFormattedText());

        if (stack.getItem() instanceof ICellWorkbenchItem workbenchItem) {
            if (workbenchItem instanceof IBasicCellItem basicCellItem) {
                IBasicCellItem.CellRestriction restriction = basicCellItem.getCellRestrictionOrNull(stack);
                if (restriction != null) {
                    lines.add(GuiText.CellRestrictionAmount.getLocal(restriction.amount()));
                    lines.add(GuiText.CellRestrictionTypes.getLocal(restriction.types()));
                }
            }
            addPartitionInformation(stack, workbenchItem, lines);
        }
    }

    @Nullable
    public Optional<StorageCellTooltipComponent> getTooltipData(ItemStack stack) {
        var inventory = BasicCellInventory.createInventory(stack, null);
        if (inventory == null) {
            return Optional.empty();
        }

        var upgrades = new ObjectArrayList<ItemStack>();
        if (AEConfig.instance().isTooltipShowCellUpgrades()) {
            if (stack.getItem() instanceof ICellWorkbenchItem workbenchItem) {
                for (var upgrade : workbenchItem.getUpgrades(stack)) {
                    if (!upgrade.isEmpty()) {
                        upgrades.add(upgrade.copy());
                    }
                }
            }
        }

        List<GenericStack> content;
        boolean hasMoreContent;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ObjectArrayList<>();
            int maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();
            for (var entry : inventory.getAvailableStacks()) {
                content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
            content.sort(Comparator.comparingLong(GenericStack::amount).reversed()
                .thenComparing(entry -> entry.what().getDisplayName().getFormattedText()));

            hasMoreContent = content.size() > maxCountShown;
            if (content.size() > maxCountShown) {
                content = new ObjectArrayList<>(content.subList(0, maxCountShown));
            }
        } else {
            content = Collections.emptyList();
            hasMoreContent = false;
        }

        return Optional.of(new StorageCellTooltipComponent(upgrades, content, hasMoreContent, true));
    }

    private void addPartitionInformation(ItemStack stack, ICellWorkbenchItem workbenchItem, List<String> lines) {
        var config = workbenchItem.getConfigInventory(stack);
        if (config.isEmpty()) {
            return;
        }

        var upgrades = workbenchItem.getUpgrades(stack);
        var includeMode = upgrades.isInstalled(AEItems.INVERTER_CARD.item()) ? GuiText.Excluded : GuiText.Included;
        var precisionMode = upgrades.isInstalled(AEItems.FUZZY_CARD.item()) ? GuiText.Fuzzy : GuiText.Precise;

        lines.add(GuiText.Partitioned.getLocal() +
            " - " +
            includeMode.getLocal() +
            " " +
            precisionMode.getLocal());
    }
}
