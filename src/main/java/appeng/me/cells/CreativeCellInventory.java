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

package appeng.me.cells;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.items.contents.CellConfig;
import appeng.text.TextComponentItemStack;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;

public class CreativeCellInventory implements StorageCell {
    private final ObjectSet<AEKey> configured;
    private final ItemStack stack;

    protected CreativeCellInventory(ItemStack stack) {
        this.stack = stack;
        this.configured = new ObjectOpenHashSet<>(CellConfig.create(stack).keySet());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return configured.contains(what) ? amount : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return configured.contains(what) ? amount : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (AEKey key : this.configured) {
            out.add(key, 1L << 53);
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
        return this.configured.contains(input);
    }

    @Override
    public CellState getStatus() {
        return CellState.TYPES_FULL;
    }

    @Override
    public double getIdleDrain() {
        return 0.0d;
    }

    @Override
    public boolean canFitInsideCell() {
        return configured.isEmpty();
    }

    @Override
    public ITextComponent getDescription() {
        return TextComponentItemStack.of(stack);
    }

    @Override
    public void persist() {
    }
}
