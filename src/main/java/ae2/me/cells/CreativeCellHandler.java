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
import ae2.api.storage.cells.ICellHandler;
import ae2.api.storage.cells.ISaveProvider;
import ae2.api.storage.cells.StorageCell;
import ae2.core.AEConfig;
import ae2.items.storage.CreativeCellItem;
import ae2.items.storage.StorageCellTooltipComponent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Cell handler for creative storage cells (both fluid and item), which do not allow item insertion.
 */
public class CreativeCellHandler implements ICellHandler {
    public static final CreativeCellHandler INSTANCE = new CreativeCellHandler();

    @Override
    public boolean isCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CreativeCellItem;
    }

    @Nullable
    @Override
    public StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (!stack.isEmpty() && stack.getItem() instanceof CreativeCellItem) {
            return new CreativeCellInventory(stack);
        }
        return null;
    }

    @Nullable
    public Optional<StorageCellTooltipComponent> getTooltipData(ItemStack stack) {
        var handler = getCellInventory(stack, null);
        if (handler == null || !(stack.getItem() instanceof CreativeCellItem cellItem)) {
            return Optional.empty();
        }

        List<GenericStack> content;
        boolean hasMoreContent;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ObjectArrayList<>();
            int maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();
            for (var key : cellItem.getConfigInventory(stack).keySet()) {
                content.add(new GenericStack(key, 1));
            }
            hasMoreContent = content.size() > maxCountShown;
            if (content.size() > maxCountShown) {
                content = new ObjectArrayList<>(content.subList(0, maxCountShown));
            }
        } else {
            content = Collections.emptyList();
            hasMoreContent = false;
        }

        return Optional.of(
            new StorageCellTooltipComponent(Collections.emptyList(), content, hasMoreContent, false));
    }
}
