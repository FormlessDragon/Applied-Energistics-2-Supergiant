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
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class NetworkStorage implements MEStorageMonitor {
    private static final IntComparator PRIORITY_SORTER = (first, second) -> Integer.compare(second, first);
    private final Int2ObjectSortedMap<MountBucket> priorityInventory =
        new Int2ObjectRBTreeMap<>(PRIORITY_SORTER);
    private final Map<MEStorageMonitor, ObjectList<MountedStorage>> mountsByInventory = new Reference2ObjectOpenHashMap<>();
    private final ObjectList<MEStorageMonitor> secondPassInventories = new ObjectArrayList<>();
    private final Reference2ObjectLinkedOpenHashMap<MEStorageChangeListener, ListenerRegistration> listeners =
        new Reference2ObjectLinkedOpenHashMap<>();
    private final ObjectList<ListenerRegistration> listenerDispatchBuffer = new ObjectArrayList<>();
    private final Supplier<KeyCounter> cachedContents;
    private final Runnable invalidationCallback;
    private boolean mountsInUse;
    @Nullable
    private MEStorageMonitor enumeratingInventory;
    @Nullable
    private ObjectList<QueuedOperation> queuedOperations;
    @Nullable
    private Set<MEStorageMonitor> queuedRemovals;
    private boolean dispatchingListeners;
    private boolean dispatchingListUpdate;
    private boolean listUpdatePending;

    public NetworkStorage(Supplier<KeyCounter> cachedContents, Runnable invalidationCallback) {
        this.cachedContents = cachedContents;
        this.invalidationCallback = invalidationCallback;
    }

    public void mount(int priority, MEStorageMonitor inventory) {
        Objects.requireNonNull(inventory, "inventory");
        if (this.mountsInUse) {
            if (this.queuedOperations == null) {
                this.queuedOperations = new ObjectArrayList<>();
            }
            this.queuedOperations.add(new QueuedOperation(true, priority, inventory));
            return;
        }

        var bucket = this.priorityInventory.computeIfAbsent(priority, MountBucket::new);
        if (bucket.shouldCompactBeforeAppend()) {
            bucket.compact();
        }
        var mountedStorage = bucket.add(inventory);
        this.mountsByInventory.computeIfAbsent(inventory, ignored -> new ObjectArrayList<>()).add(mountedStorage);
    }

    public void unmount(MEStorageMonitor inventory) {
        Objects.requireNonNull(inventory, "inventory");
        if (this.mountsInUse) {
            if (this.queuedOperations == null) {
                this.queuedOperations = new ObjectArrayList<>();
            }
            this.queuedOperations.add(new QueuedOperation(false, 0, inventory));
            if (this.queuedRemovals == null) {
                this.queuedRemovals = new ReferenceOpenHashSet<>();
            }
            this.queuedRemovals.add(inventory);
            return;
        }

        var mountedStorages = this.mountsByInventory.remove(inventory);
        if (mountedStorages == null) {
            return;
        }

        int mountCount = mountedStorages.size();
        for (int i = 0; i < mountCount; i++) {
            var mountedStorage = mountedStorages.get(i);
            var bucket = mountedStorage.bucket;
            bucket.remove(mountedStorage);
            if (bucket.isEmpty()) {
                var removedBucket = this.priorityInventory.remove(bucket.priority);
                if (removedBucket != bucket) {
                    throw new IllegalStateException("Storage priority index and mount bucket diverged");
                }
            } else if (bucket.shouldCompactBeforeAppend()) {
                bucket.compact();
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
            for (var bucket : this.priorityInventory.values()) {
                if (remaining <= 0) {
                    break;
                }
                bucket.compact();
                var mounts = bucket.mounts;
                for (int i = 0; i < mounts.size(); i++) {
                    var inventory = mounts.get(i).inventory;
                    if (remaining <= 0) {
                        break;
                    }
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    if (inventory.isStickyStorageFor(what, source)) {
                        long inserted = inventory.insert(what, remaining, mode, source);
                        remaining -= inserted;
                        stickyStorageFound = true;
                    }
                }
            }

            if (!stickyStorageFound) {
                for (var bucket : this.priorityInventory.values()) {
                    if (remaining <= 0) {
                        break;
                    }
                    bucket.compact();
                    this.secondPassInventories.clear();
                    var mounts = bucket.mounts;
                    for (int i = 0; i < mounts.size(); i++) {
                        var inventory = mounts.get(i).inventory;
                        if (remaining <= 0) {
                            break;
                        }
                        if (isQueuedForRemoval(inventory)) {
                            continue;
                        }
                        if (inventory.isPreferredStorageFor(what, source)) {
                            long inserted = inventory.insert(what, remaining, mode, source);
                            remaining -= inserted;
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
                    }
                }
            }
        } finally {
            this.secondPassInventories.clear();
            this.mountsInUse = false;
            flushQueued();
        }
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
            for (var bucket : this.priorityInventory.values()) {
                if (extracted >= amount) {
                    break;
                }
                bucket.compact();
                var mounts = bucket.mounts;
                for (int i = 0; i < mounts.size(); i++) {
                    var inventory = mounts.get(i).inventory;
                    if (extracted >= amount) {
                        break;
                    }
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    long extractedNow = inventory.extract(what, amount - extracted, mode, source);
                    extracted += extractedNow;
                }
            }
        } finally {
            this.mountsInUse = false;
            flushQueued();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (this.mountsInUse) {
            return;
        }
        out.addAll(this.cachedContents.get());
    }

    /**
     * Enumerates mounted storage directly. Only the owning storage service uses this to rebuild its aggregate cache.
     *
     * @return false if an active operation prevented enumeration; the caller must keep its previous cache
     */
    public boolean getAvailableStacksRaw(KeyCounter out) {
        if (this.mountsInUse) {
            AELog.error("Reentrant raw network storage enumeration; invalidating the storage cache.");
            this.invalidationCallback.run();
            return false;
        }
        this.mountsInUse = true;
        try {
            for (var bucket : this.priorityInventory.values()) {
                bucket.compact();
                var mounts = bucket.mounts;
                for (int i = 0; i < mounts.size(); i++) {
                    var inventory = mounts.get(i).inventory;
                    if (isQueuedForRemoval(inventory)) {
                        continue;
                    }
                    this.enumeratingInventory = inventory;
                    inventory.getAvailableStacks(out);
                    this.enumeratingInventory = null;
                }
            }
        } finally {
            this.enumeratingInventory = null;
            this.mountsInUse = false;
            flushQueued();
        }
        return true;
    }

    /**
     * Identifies the source whose authoritative contents are currently being read by the owning service.
     */
    public boolean isEnumerating(MEStorageMonitor inventory) {
        return this.enumeratingInventory == inventory;
    }

    @Override
    public KeyCounter getAvailableStacks() {
        var result = KeyCounter.saturating();
        getAvailableStacks(result);
        return result;
    }

    @Override
    public void addListener(MEStorageChangeListener listener, Object verificationToken) {
        if (this.listeners.containsKey(listener)) {
            throw new IllegalStateException("The storage listener is already registered.");
        }
        this.listeners.put(listener, new ListenerRegistration(listener, verificationToken));
    }

    @Override
    public void removeListener(MEStorageChangeListener listener) {
        var registration = this.listeners.remove(listener);
        if (registration != null) {
            registration.active = false;
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
                this.invalidationCallback.run();
            }
            return;
        }

        this.dispatchingListeners = true;
        this.dispatchingListUpdate = listUpdate;
        this.listenerDispatchBuffer.clear();
        this.listenerDispatchBuffer.addAll(this.listeners.values());
        try {
            for (int i = 0; i < this.listenerDispatchBuffer.size(); i++) {
                var registration = this.listenerDispatchBuffer.get(i);
                if (!registration.active) {
                    continue;
                }
                String callback = "isValid";
                try {
                    if (!registration.listener.isValid(registration.verificationToken)) {
                        removeRegistration(registration);
                        continue;
                    }
                    // Validation itself may unregister or replace this registration.
                    if (!registration.active) {
                        continue;
                    }
                    if (listUpdate) {
                        callback = "onListUpdate";
                        registration.listener.onListUpdate();
                    } else {
                        callback = "onStackChange";
                        registration.listener.onStackChange(what, delta);
                    }
                } catch (RuntimeException exception) {
                    removeRegistration(registration);
                    AELog.error(exception, "Network storage listener " + registration.listener.getClass().getName()
                        + " failed in " + callback + "; removing the failed registration.");
                }
            }
        } finally {
            this.listenerDispatchBuffer.clear();
            this.dispatchingListUpdate = false;
            this.dispatchingListeners = false;
        }

        if (this.listUpdatePending) {
            this.listUpdatePending = false;
            postListUpdate();
        }
    }

    private void removeRegistration(ListenerRegistration registration) {
        registration.active = false;
        if (this.listeners.get(registration.listener) == registration) {
            this.listeners.remove(registration.listener);
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

    private boolean isQueuedForRemoval(MEStorageMonitor inventory) {
        return this.queuedRemovals != null && this.queuedRemovals.contains(inventory);
    }

    private record QueuedOperation(boolean mount, int priority, MEStorageMonitor inventory) {
    }

    private static final class MountBucket {
        private final int priority;
        private final ObjectList<MountedStorage> mounts = new ObjectArrayList<>();
        private int liveMountCount;
        private boolean dirty;

        private MountBucket(int priority) {
            this.priority = priority;
        }

        private MountedStorage add(MEStorageMonitor inventory) {
            var mountedStorage = new MountedStorage(this, inventory, this.mounts.size());
            this.mounts.add(mountedStorage);
            this.liveMountCount++;
            return mountedStorage;
        }

        private void remove(MountedStorage mountedStorage) {
            if (!mountedStorage.active || mountedStorage.bucket != this
                || this.mounts.get(mountedStorage.index) != mountedStorage) {
                throw new IllegalStateException("Storage identity index and mount position diverged");
            }

            this.mounts.set(mountedStorage.index, null);
            mountedStorage.active = false;
            this.liveMountCount--;
            this.dirty = true;
        }

        private boolean isEmpty() {
            return this.liveMountCount == 0;
        }

        private boolean shouldCompactBeforeAppend() {
            return this.dirty && this.liveMountCount * 2 <= this.mounts.size();
        }

        private void compact() {
            if (!this.dirty) {
                return;
            }

            int writeIndex = 0;
            int physicalSize = this.mounts.size();
            for (int readIndex = 0; readIndex < physicalSize; readIndex++) {
                var mountedStorage = this.mounts.get(readIndex);
                if (mountedStorage == null) {
                    continue;
                }

                if (writeIndex != readIndex) {
                    this.mounts.set(writeIndex, mountedStorage);
                }
                mountedStorage.index = writeIndex++;
            }

            if (physicalSize > writeIndex) {
                this.mounts.subList(writeIndex, physicalSize).clear();
            }
            if (writeIndex != this.liveMountCount) {
                throw new IllegalStateException("Storage mount bucket live count diverged during compaction");
            }
            this.dirty = false;
        }
    }

    private static final class MountedStorage {
        private final MountBucket bucket;
        private final MEStorageMonitor inventory;
        private int index;
        private boolean active = true;

        private MountedStorage(MountBucket bucket, MEStorageMonitor inventory, int index) {
            this.bucket = bucket;
            this.inventory = inventory;
            this.index = index;
        }
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
