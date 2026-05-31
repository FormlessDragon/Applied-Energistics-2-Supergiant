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
import ae2.api.config.IncludeExclude;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.util.prioritylist.DefaultPriorityList;
import ae2.util.prioritylist.IPartitionList;

public class MEInventoryHandler extends DelegatingMEInventory {

    private IPartitionList partitionList = DefaultPriorityList.INSTANCE;
    private IncludeExclude partitionListMode = IncludeExclude.WHITELIST;
    private boolean filterOnExtraction;
    private boolean filterAvailableContents;
    private boolean allowExtraction = true;
    private boolean allowInsertion = true;
    private boolean voidOverflow;
    private boolean sticky;

    private boolean gettingAvailableContent = false;

    public MEInventoryHandler(MEStorage inventory) {
        super(inventory);
    }

    public void setAllowExtraction(boolean allowExtraction) {
        this.allowExtraction = allowExtraction;
    }

    public void setAllowInsertion(boolean allowInsertion) {
        this.allowInsertion = allowInsertion;
    }

    public void setWhitelist(IncludeExclude myWhitelist) {
        this.partitionListMode = myWhitelist;
    }

    protected IPartitionList getPartitionList() {
        return this.partitionList;
    }

    public void setPartitionList(IPartitionList myPartitionList) {
        this.partitionList = myPartitionList;
    }

    public void setExtractFiltering(boolean filterOnExtraction, boolean filterAvailableContents) {
        this.filterOnExtraction = filterOnExtraction;
        this.filterAvailableContents = filterAvailableContents;
    }

    public void setVoidOverflow(boolean voidOverflow) {
        this.voidOverflow = voidOverflow;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
// Applies the black/whitelist, but only if any item is listed at all
        if (!this.allowInsertion || !passesBlackOrWhitelist(what)) {
            return 0;
        }

        final var inserted = super.insert(what, amount, mode, source);
        return this.voidOverflow ? amount : inserted;
// Inventories that already contain some equal stack are also preferred
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (this.filterOnExtraction && !canExtract(what)) {
            return 0;
// we use a copy of size 1 here to prevent inventories from attempting to query multiple sub-inventories
        }

        return super.extract(what, amount, mode, source);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (this.gettingAvailableContent) {
// Prevent recursion in case the internal inventory somehow calls this when the available items are queried.
            return;
        }

        this.gettingAvailableContent = true;
        try {
            if (!this.filterAvailableContents) {
                super.getAvailableStacks(out);
            } else {
                if (!this.allowExtraction) {
// This is handled by the NetworkInventoryHandler when the initial query is coming from the network.
                    return;
                }

                for (var entry : getDelegate().getAvailableStacks()) {
                    if (canExtract(entry.getKey())) {
                        out.add(entry.getKey(), entry.getLongValue());
                    }
                }
            }
        } finally {
            this.gettingAvailableContent = false;
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey input, IActionSource source) {
        if (this.partitionListMode == IncludeExclude.WHITELIST) {
            if (this.partitionList.isListed(input)) {
                return true;
            }
        }

        if (super.extract(input, 1, Actionable.SIMULATE, source) > 0) {
            return true;
        }

        return super.isPreferredStorageFor(input, source);
    }

    @Override
    public boolean isStickyStorageFor(AEKey input, IActionSource source) {
        if (this.partitionListMode != IncludeExclude.WHITELIST) {
            return false;
        }
        if (!this.partitionList.isEmpty()) {
            return this.sticky && this.partitionList.isListed(input);
        }
        if (this.sticky) {
            return super.extract(input, 1, Actionable.SIMULATE, source) > 0;
        }
        return super.isStickyStorageFor(input, source);
    }

    protected boolean canExtract(AEKey request) {
        return allowExtraction && passesBlackOrWhitelist(request);
    }

    private boolean passesBlackOrWhitelist(AEKey input) {
        return this.partitionList.matchesFilter(input, this.partitionListMode);
    }
}
