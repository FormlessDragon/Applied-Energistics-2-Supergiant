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
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

public class NetworkStorage implements MEStorageMonitor {
    private static final IntComparator PRIORITY_SORTER = (first, second) -> Integer.compare(second, first);
    private final Int2ObjectSortedMap<ObjectList<MEStorage>> priorityInventory =
        new Int2ObjectRBTreeMap<>(PRIORITY_SORTER);
    private final ObjectList<MEStorage> secondPassInventories = new ObjectArrayList<>();
    private final ObjectList<ListenerRegistration> listeners = new ObjectArrayList<>();
    private final ObjectList<ListenerRegistration> listenerDispatchBuffer = new ObjectArrayList<>();
    private final Supplier<KeyCounter> cachedContents;
    private final Runnable legacyMutationCallback;
    private boolean mountsInUse;
    @Nullable
    private ObjectList<QueuedOperation> queuedOperations;
    @Nullable
    private Set<MEStorage> queuedRemovals;
    private boolean dispatchingListeners;
    private boolean dispatchingListUpdate;
    private boolean listUpdatePending;

    public NetworkStorage(Supplier<KeyCounter> cachedContents, Runnable legacyMutationCallback) {
        this.cachedContents = cachedContents;
        this.legacyMutationCallback = legacyMutationCallback;
    }

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
            if (this.queuedRemovals == null) {
                this.queuedRemovals = Collections.newSetFromMap(new IdentityHashMap<>());
            }
            this.queuedRemovals.add(inventory);
            return;
        }

        var iterator = this.priorityInventory.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var inventories = entry.getValue();
            boolean removed = false;
            for (int i = inventories.size() - 1; i >= 0; i--) {
                if (inventories.get(i) == inventory) {
                    inventories.remove(i);
                    removed = true;
                }
            }
            if (removed && inventories.isEmpty()) {
                iterator.remove();
            }
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0) {
            return 0;
        }
        if (this.mountsInUse) {
            return 0;
        }

        long remaining = amount;
        boolean stickyStorageFound = false;

        this.mountsInUse = true;
        try {
            for (var inventories : this.priorityInventory.values()) {
                for (int i = 0; i < inventories.size(); i++) {
                    var inventory = inventories.get(i);
                    if (remaining <= 0) {
                        break;
                    }
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    if (inventory.isStickyStorageFor(what, source)) {
                        long inserted = inventory.insert(what, remaining, mode, source);
                        remaining -= inserted;
                        legacyStorageChanged(inventory, inserted, mode);
                        stickyStorageFound = true;
                    }
                }
            }

            if (!stickyStorageFound) {
                for (var inventories : this.priorityInventory.values()) {
                    this.secondPassInventories.clear();
                    for (int i = 0; i < inventories.size(); i++) {
                        var inventory = inventories.get(i);
                        if (remaining <= 0) {
                            break;
                        }
                        if (isQueuedForRemoval(inventory)) {
                            continue;
                        }
                        if (inventory.isPreferredStorageFor(what, source)) {
                            long inserted = inventory.insert(what, remaining, mode, source);
                            remaining -= inserted;
                            legacyStorageChanged(inventory, inserted, mode);
                        } else {
                            this.secondPassInventories.add(inventory);
                        }
                    }

                    for (int i = 0; i < this.secondPassInventories.size(); i++) {
                        var inventory = this.secondPassInventories.get(i);
                        if (remaining <= 0) {
                            break;
                        }
                        if (isQueuedForRemoval(inventory)) {
                            continue;
                        }
                        long inserted = inventory.insert(what, remaining, mode, source);
                        remaining -= inserted;
                        legacyStorageChanged(inventory, inserted, mode);
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
        if (amount <= 0) {
            return 0;
        }
        if (this.mountsInUse) {
            return 0;
        }

        long extracted = 0;

        this.mountsInUse = true;
        try {
            for (var inventories : this.priorityInventory.values()) {
                for (int i = 0; i < inventories.size(); i++) {
                    var inventory = inventories.get(i);
                    if (extracted >= amount) {
                        break;
                    }
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    long extractedNow = inventory.extract(what, amount - extracted, mode, source);
                    extracted += extractedNow;
                    legacyStorageChanged(inventory, extractedNow, mode);
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
        out.addAll(this.cachedContents.get());
    }

    /**
     * Enumerates mounted storage directly. Only the owning storage service uses this to rebuild its aggregate cache.
     */
    public void getAvailableStacksRaw(KeyCounter out) {
        this.mountsInUse = true;
        try {
            for (var inventories : this.priorityInventory.values()) {
                for (int i = 0; i < inventories.size(); i++) {
                    var inventory = inventories.get(i);
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    inventory.getAvailableStacks(out);
                }
            }
        } finally {
            this.mountsInUse = false;
            flushQueued();
        }
    }

    @Override
    public KeyCounter getAvailableStacks() {
        var result = KeyCounter.saturating();
        getAvailableStacks(result);
        return result;
    }

    @Override
    public void addListener(MEStorageChangeListener listener, Object verificationToken) {
        for (int i = 0; i < this.listeners.size(); i++) {
            var registration = this.listeners.get(i);
            if (registration.listener == listener) {
                throw new IllegalStateException("The storage listener is already registered.");
            }
        }
        this.listeners.add(new ListenerRegistration(listener, verificationToken));
    }

    @Override
    public void removeListener(MEStorageChangeListener listener) {
        for (int i = this.listeners.size() - 1; i >= 0; i--) {
            var registration = this.listeners.get(i);
            if (registration.listener == listener) {
                registration.active = false;
                this.listeners.remove(i);
            }
        }
    }

    public boolean hasListeners() {
        return !this.listeners.isEmpty();
    }

    public boolean isDispatchingListeners() {
        return this.dispatchingListeners;
    }

    public void postChange(AEKey what, long delta) {
        dispatchListeners(what, delta, false);
    }

    public void postListUpdate() {
        dispatchListeners(null, 0, true);
    }

    private void dispatchListeners(@Nullable AEKey what, long delta, boolean listUpdate) {
        if (this.dispatchingListeners) {
            AELog.error("Reentrant network storage listener notification; scheduling a full storage refresh.");
            if (!this.listUpdatePending && !this.dispatchingListUpdate) {
                this.listUpdatePending = true;
                this.legacyMutationCallback.run();
            }
            return;
        }

        this.dispatchingListeners = true;
        this.dispatchingListUpdate = listUpdate;
        this.listenerDispatchBuffer.clear();
        this.listenerDispatchBuffer.addAll(this.listeners);
        try {
            for (int i = 0; i < this.listenerDispatchBuffer.size(); i++) {
                var registration = this.listenerDispatchBuffer.get(i);
                if (!registration.active) {
                    continue;
                }
                if (!registration.listener.isValid(registration.verificationToken)) {
                    registration.active = false;
                    continue;
                }
                if (listUpdate) {
                    registration.listener.onListUpdate();
                } else {
                    registration.listener.onStackChange(what, delta);
                }
            }
        } finally {
            this.listenerDispatchBuffer.clear();
            this.dispatchingListUpdate = false;
            this.dispatchingListeners = false;
        }
        removeInactiveListeners();

        if (this.listUpdatePending) {
            this.listUpdatePending = false;
            postListUpdate();
        }
    }

    private void removeInactiveListeners() {
        for (int i = this.listeners.size() - 1; i >= 0; i--) {
            if (!this.listeners.get(i).active) {
                this.listeners.remove(i);
            }
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

        ObjectList<QueuedOperation> queued = this.queuedOperations;
        this.queuedOperations = null;
        this.queuedRemovals = null;
        for (int i = 0; i < queued.size(); i++) {
            var operation = queued.get(i);
            if (operation.mount()) {
                mount(operation.priority(), operation.inventory());
            } else {
                unmount(operation.inventory());
            }
        }
    }

    private boolean isQueuedForRemoval(MEStorage inventory) {
        return this.queuedRemovals != null && this.queuedRemovals.contains(inventory);
    }

    private void legacyStorageChanged(MEStorage inventory, long amount, Actionable mode) {
        if (amount > 0 && mode == Actionable.MODULATE && !(inventory instanceof MEStorageMonitor)) {
            this.legacyMutationCallback.run();
        }
    }

    private record QueuedOperation(boolean mount, int priority, MEStorage inventory) {
    }

    private static final class ListenerRegistration {
        private final MEStorageChangeListener listener;
        private final Object verificationToken;
        private boolean active = true;

        private ListenerRegistration(MEStorageChangeListener listener, Object verificationToken) {
            this.listener = listener;
            this.verificationToken = verificationToken;
        }
    }
}
