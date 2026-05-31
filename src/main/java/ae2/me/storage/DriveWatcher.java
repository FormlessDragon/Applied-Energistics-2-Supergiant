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

package ae2.me.storage;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.storage.cells.CellState;
import ae2.api.storage.cells.ICellWorkbenchItem;
import ae2.api.storage.cells.StorageCell;
import ae2.core.definitions.AEItems;
import net.minecraft.item.ItemStack;

public class DriveWatcher extends MEInventoryHandler {

    private final Runnable activityCallback;
    private CellState oldStatus = CellState.EMPTY;

    public DriveWatcher(StorageCell i, ItemStack cellItem, Runnable activityCallback) {
        super(i);
        this.activityCallback = activityCallback;
        this.oldStatus = getStatus();
        if (cellItem.getItem() instanceof ICellWorkbenchItem workbenchItem) {
            setSticky(workbenchItem.getUpgrades(cellItem).isInstalled(AEItems.STICKY_CARD.item()));
        }
    }

    public CellState getStatus() {
        return getCell().getStatus();
    }

    public StorageCell getCell() {
        return (StorageCell) getDelegate();
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var inserted = super.insert(what, amount, mode, source);

        if (mode == Actionable.MODULATE && inserted > 0) {
            var newStatus = this.getStatus();

            if (newStatus != this.oldStatus) {
                this.activityCallback.run();
                this.oldStatus = newStatus;
            }
        }

        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var extracted = super.extract(what, amount, mode, source);

        if (mode == Actionable.MODULATE && extracted > 0) {
            var newStatus = this.getStatus();

            if (newStatus != this.oldStatus) {
                this.activityCallback.run();
                this.oldStatus = newStatus;
            }
        }

        return extracted;
    }
}
