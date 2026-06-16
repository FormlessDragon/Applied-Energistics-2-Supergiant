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

package ae2.client.gui.me.common;

import ae2.container.me.common.GridInventoryEntry;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class RepoSlot extends ClientReadOnlySlot {

    private final Repo repo;
    private final int offset;

    public RepoSlot(Repo repo, int offset, int displayX, int displayY) {
        super(displayX, displayY);
        this.repo = repo;
        this.offset = offset;
    }

    public int getRepoViewIndex() {
        return this.offset;
    }

    public @Nullable GridInventoryEntry getEntry() {
        if (this.repo.isEnabled()) {
            return this.repo.get(this.offset);
        }
        return null;
    }

    public boolean isUserPinSlot() {
        return this.repo.isEnabled() && this.repo.isUserPinSlot(this.offset);
    }

    public boolean isEmptyUserPinSlot() {
        return this.repo.isEnabled() && this.repo.isEmptyUserPinSlot(this.offset);
    }

    public int getUserPinSlotIndex() {
        return this.repo.isEnabled() ? this.repo.getUserPinSlotIndex(this.offset) : -1;
    }

    public long getStoredAmount() {
        GridInventoryEntry entry = getEntry();
        return entry != null ? entry.storedAmount() : 0;
    }

    public long getRequestableAmount() {
        GridInventoryEntry entry = getEntry();
        return entry != null ? entry.requestableAmount() : 0;
    }

    public boolean isCraftable() {
        GridInventoryEntry entry = getEntry();
        return entry != null && entry.craftable();
    }

    @Override
    public ItemStack getStack() {
        GridInventoryEntry entry = getEntry();
        if (entry != null && entry.what() != null) {
            return entry.what().wrapForDisplayOrFilter();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean getHasStack() {
        return getEntry() != null;
    }
}

