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
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.core.localization.GuiText;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class NetworkStorage implements MEStorage {
    private static final IntComparator PRIORITY_SORTER = (first, second) -> Integer.compare(second, first);
    private final Int2ObjectSortedMap<List<MEStorage>> priorityInventory = new Int2ObjectRBTreeMap<>(PRIORITY_SORTER);
    private final ObjectList<MEStorage> secondPassInventories = new ObjectArrayList<>();
    private boolean mountsInUse;
    @Nullable
    private List<QueuedOperation> queuedOperations;

    public void mount(int priority, MEStorage inventory) {
        if (this.mountsInUse) {
            if (this.queuedOperations == null) {
                this.queuedOperations = new ObjectArrayList<>();
            }
            this.queuedOperations.add(new QueuedOperation(true, priority, inventory));
            return;
        }

        this.priorityInventory.computeIfAbsent(priority, ignored -> new ObjectArrayList<>()).add(inventory);
    }

    public void unmount(MEStorage inventory) {
        if (this.mountsInUse) {
            if (this.queuedOperations == null) {
                this.queuedOperations = new ObjectArrayList<>();
            }
            this.queuedOperations.add(new QueuedOperation(false, 0, inventory));
            return;
        }

        var iterator = this.priorityInventory.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            List<MEStorage> inventories = entry.getValue();
            if (inventories.remove(inventory) && inventories.isEmpty()) {
                iterator.remove();
            }
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (this.mountsInUse) {
            return 0;
        }

        long remaining = amount;
        boolean stickyStorageFound = false;

        this.mountsInUse = true;
        try {
            for (List<MEStorage> inventories : this.priorityInventory.values()) {
                for (MEStorage inventory : inventories) {
                    if (remaining <= 0) {
                        break;
                    }
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    if (inventory.isStickyStorageFor(what, source)) {
                        remaining -= inventory.insert(what, remaining, mode, source);
                        stickyStorageFound = true;
                    }
                }
            }

            if (!stickyStorageFound) {
                for (List<MEStorage> inventories : this.priorityInventory.values()) {
                    this.secondPassInventories.clear();
                    for (MEStorage inventory : inventories) {
                        if (remaining <= 0) {
                            break;
                        }
                        if (isQueuedForRemoval(inventory)) {
                            continue;
                        }
                        if (inventory.isPreferredStorageFor(what, source)) {
                            remaining -= inventory.insert(what, remaining, mode, source);
                        } else {
                            this.secondPassInventories.add(inventory);
                        }
                    }

                    for (MEStorage inventory : this.secondPassInventories) {
                        if (remaining <= 0) {
                            break;
                        }
                        if (isQueuedForRemoval(inventory)) {
                            continue;
                        }
                        remaining -= inventory.insert(what, remaining, mode, source);
                    }
                }
            }
        } finally {
            this.mountsInUse = false;
        }

        flushQueued();
        return amount - remaining;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (this.mountsInUse) {
            return 0;
        }

        long extracted = 0;

        this.mountsInUse = true;
        try {
            for (List<MEStorage> inventories : this.priorityInventory.values()) {
                for (MEStorage inventory : inventories) {
                    if (extracted >= amount) {
                        break;
                    }
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    extracted += inventory.extract(what, amount - extracted, mode, source);
                }
            }
        } finally {
            this.mountsInUse = false;
        }

        flushQueued();
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        this.mountsInUse = true;
        try {
            for (List<MEStorage> inventories : this.priorityInventory.values()) {
                for (MEStorage inventory : inventories) {
                    inventory.getAvailableStacks(out);
                }
            }
        } finally {
            this.mountsInUse = false;
            flushQueued();
        }
    }

    @Override
    public ITextComponent getDescription() {
        return GuiText.MENetworkStorage.text();
    }

    private void flushQueued() {
        Preconditions.checkState(!this.mountsInUse);
        if (this.queuedOperations == null) {
            return;
        }

        List<QueuedOperation> queued = this.queuedOperations;
        this.queuedOperations = null;
        for (QueuedOperation operation : queued) {
            if (operation.mount()) {
                mount(operation.priority(), operation.inventory());
            } else {
                unmount(operation.inventory());
            }
        }
    }

    private boolean isQueuedForRemoval(MEStorage inventory) {
        if (this.queuedOperations != null) {
            for (QueuedOperation operation : this.queuedOperations) {
                if (!operation.mount() && operation.inventory() == inventory) {
                    return true;
                }
            }
        }
        return false;
    }

    private record QueuedOperation(boolean mount, int priority, MEStorage inventory) {
    }
}
