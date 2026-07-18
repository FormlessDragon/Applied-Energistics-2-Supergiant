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

package ae2.me.service;

import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridServiceProvider;
import ae2.api.networking.storage.IStorageService;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.core.AELog;
import ae2.me.helpers.InterestManager;
import ae2.me.helpers.StackWatcher;
import ae2.me.storage.NetworkStorage;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;

public class StorageService implements IStorageService, IGridServiceProvider {

    private final Reference2ObjectMap<IGridNode, ProviderState> nodeProviders = new Reference2ObjectOpenHashMap<>();
    private final ObjectList<ProviderState> globalProviders = new ObjectArrayList<>();
    private final SetMultimap<AEKey, StackWatcher<IStorageWatcherNode>> interests = HashMultimap.create();
    private final InterestManager<StackWatcher<IStorageWatcherNode>> interestManager = new InterestManager<>(this.interests);
    private final NetworkStorage storage = new NetworkStorage(this::getCachedInventory, this::invalidateCache);
    private KeyCounter cachedAvailableStacks = KeyCounter.saturating();
    private KeyCounter cachedAvailableScratch = KeyCounter.saturating();
    private final Reference2ObjectMap<IGridNode, StackWatcher<IStorageWatcherNode>> watchers =
        new Reference2ObjectOpenHashMap<>();

    private boolean cachedStacksNeedUpdate = true;
    private boolean cacheInitialized;
    private boolean processingMonitorCallback;
    private int legacyMountCount;

    @Override
    public void onServerEndTick() {
        boolean cacheIsObserved = !this.interestManager.isEmpty() || this.storage.hasListeners();
        if (cacheIsObserved && (this.cachedStacksNeedUpdate || this.legacyMountCount > 0)) {
            updateCachedStacks();
        } else if (!cacheIsObserved && this.legacyMountCount > 0) {
            this.cachedStacksNeedUpdate = true;
        }
    }

    private static long sanitizeAmount(long amount) {
        return Math.max(0, amount);
    }

    private void updateCachedStacks() {
        var previous = this.cachedAvailableStacks;
        var replacement = this.cachedAvailableScratch;

        replacement.reset();
        this.storage.getAvailableStacksRaw(replacement);
        replacement.removeZeros();
        this.cachedAvailableStacks = replacement;
        this.cachedAvailableScratch = previous;
        this.cachedStacksNeedUpdate = false;

        if (!this.cacheInitialized) {
            this.cacheInitialized = true;
            for (var entry : replacement) {
                var amount = sanitizeAmount(entry.getLongValue());
                if (amount > 0) {
                    postWatcherUpdate(entry.getKey(), amount);
                }
            }
            return;
        }

        for (var entry : replacement) {
            var what = entry.getKey();
            var newAmount = sanitizeAmount(entry.getLongValue());
            var oldAmount = sanitizeAmount(previous.get(what));
            if (newAmount != oldAmount) {
                postWatcherUpdate(what, newAmount);
                this.storage.postChange(what, newAmount - oldAmount);
            }
        }

        for (var entry : previous) {
            var what = entry.getKey();
            var oldAmount = sanitizeAmount(entry.getLongValue());
            if (oldAmount > 0 && replacement.get(what) == 0) {
                postWatcherUpdate(what, 0);
                this.storage.postChange(what, -oldAmount);
            }
        }
    }

    private void applyMonitorDelta(AEKey what, long delta, Object source) {
        if (what == null) {
            AELog.error("Storage monitor %s reported a null key; scheduling a full storage scan.", source);
            invalidateCache();
            return;
        }
        if (delta == 0) {
            return;
        }
        if (this.cachedStacksNeedUpdate || !this.cacheInitialized) {
            return;
        }

        long current = sanitizeAmount(this.cachedAvailableStacks.get(what));
        if ((current == Long.MAX_VALUE && delta < 0)
            || (delta < 0 && (delta == Long.MIN_VALUE || current < -delta))) {
            AELog.error("Storage monitor %s reported delta %d for %s with cached amount %d; scheduling a full storage scan.",
                source, delta, what, current);
            invalidateCache();
            return;
        }

        long updated = delta > 0 && current > Long.MAX_VALUE - delta ? Long.MAX_VALUE : current + delta;
        if (updated == 0) {
            this.cachedAvailableStacks.remove(what);
        } else {
            this.cachedAvailableStacks.set(what, updated);
        }
        postWatcherUpdate(what, updated);
        this.storage.postChange(what, delta);
    }

    private void postWatcherUpdate(AEKey what, long newAmount) {
        for (var watcher : this.interestManager.get(what)) {
            watcher.getHost().onStackChange(what, newAmount);
        }
        for (var watcher : this.interestManager.getAllStacksWatchers()) {
            watcher.getHost().onStackChange(what, newAmount);
        }
    }

    @Override
    public void addNode(IGridNode node, NBTTagCompound savedData) {
        var storageProvider = node.getService(IStorageProvider.class);
        if (storageProvider != null) {
            var state = new ProviderState(storageProvider);
            this.nodeProviders.put(node, state);
            state.mount();
        }

        var watcherNode = node.getService(IStorageWatcherNode.class);
        if (watcherNode != null) {
            var watcher = new StackWatcher<>(this.interestManager, watcherNode);
            this.watchers.put(node, watcher);
            watcherNode.updateWatcher(watcher);
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        var watcher = this.watchers.remove(node);
        if (watcher != null) {
            watcher.destroy();
        }

        var providerState = this.nodeProviders.remove(node);
        if (providerState != null) {
            providerState.unmount();
        }
    }

    @Override
    public MEStorage getInventory() {
        return this.storage;
    }

    @Override
    public KeyCounter getCachedInventory() {
        if (this.cachedStacksNeedUpdate) {
            updateCachedStacks();
        }
        return this.cachedAvailableStacks;
    }

    @Override
    public void addGlobalStorageProvider(IStorageProvider provider) {
        for (int i = 0; i < this.globalProviders.size(); i++) {
            var state = this.globalProviders.get(i);
            if (state.provider == provider) {
                throw new IllegalArgumentException("Duplicate storage provider registration for " + provider);
            }
        }

        var state = new ProviderState(provider);
        this.globalProviders.add(state);
        state.mount();
    }

    @Override
    public void removeGlobalStorageProvider(IStorageProvider provider) {
        for (int i = this.globalProviders.size() - 1; i >= 0; i--) {
            var state = this.globalProviders.get(i);
            if (state.provider == provider) {
                this.globalProviders.remove(i);
                state.unmount();
                return;
            }
        }
    }

    @Override
    public void refreshNodeStorageProvider(IGridNode node) {
        var state = this.nodeProviders.get(node);
        if (state == null) {
            throw new IllegalArgumentException("The given node is not part of this grid or has no storage provider.");
        }
        state.update();
    }

    @Override
    public void refreshGlobalStorageProvider(IStorageProvider provider) {
        for (int i = 0; i < this.globalProviders.size(); i++) {
            var state = this.globalProviders.get(i);
            if (state.provider == provider) {
                state.update();
                return;
            }
        }

        throw new IllegalArgumentException("Storage provider " + provider + " is not part of this grid.");
    }

    @Override
    public void invalidateCache() {
        if (!this.cachedStacksNeedUpdate) {
            this.cachedStacksNeedUpdate = true;
            this.storage.postListUpdate();
        }
    }

    @Override
    public void debugDump(JsonWriter writer) throws IOException {
        writer.name("cachedAvailableStacks");
        writer.beginArray();
        for (var entry : this.cachedAvailableStacks) {
            writer.beginObject();
            writer.name("key");
            writer.value(String.valueOf(entry.getKey()));
            writer.name("amount");
            writer.value(entry.getLongValue());
            writer.endObject();
        }
        writer.endArray();
    }

    private class ProviderState implements IStorageMounts {
        private final IStorageProvider provider;
        private final ReferenceSet<MEStorage> inventories = new ReferenceOpenHashSet<>();
        private final Reference2ObjectMap<MEStorage, SourceListener> monitorListeners =
            new Reference2ObjectOpenHashMap<>();
        private boolean mounted;

        ProviderState(IStorageProvider provider) {
            this.provider = provider;
        }

        private void mount() {
            Preconditions.checkState(!this.mounted, "Can't mount a provider's inventories when it's already mounted");

            this.mounted = true;
            this.provider.mountInventories(this);
            invalidateCache();
        }

        @Override
        public void mount(MEStorage inventory, int priority) {
            Preconditions.checkState(this.mounted, "Cannot use StorageMounts after the storage has been unmounted.");

            if (!this.inventories.add(inventory)) {
                throw new IllegalStateException("Cannot mount the same inventory twice.");
            }

            storage.mount(priority, inventory);
            if (inventory instanceof MEStorageMonitor monitor) {
                var listener = new SourceListener(this, monitor, Thread.currentThread());
                this.monitorListeners.put(inventory, listener);
                monitor.addListener(listener, inventory);
            } else {
                legacyMountCount++;
            }
            invalidateCache();
        }

        void update() {
            unmount();
            mount();
        }

        void unmount() {
            if (!this.mounted) {
                return;
            }

            this.mounted = false;
            for (var inventory : this.inventories) {
                var listener = this.monitorListeners.remove(inventory);
                if (listener != null) {
                    listener.monitor.removeListener(listener);
                } else {
                    legacyMountCount--;
                }
                storage.unmount(inventory);
            }
            this.inventories.clear();
            invalidateCache();
        }
    }

    private final class SourceListener implements MEStorageChangeListener {
        private final ProviderState provider;
        private final MEStorageMonitor monitor;
        private final Thread ownerThread;

        private SourceListener(ProviderState provider, MEStorageMonitor monitor, Thread ownerThread) {
            this.provider = provider;
            this.monitor = monitor;
            this.ownerThread = ownerThread;
        }

        @Override
        public boolean isValid(Object verificationToken) {
            return verificationToken == this.monitor
                && this.provider.mounted
                && this.provider.monitorListeners.get(this.monitor) == this;
        }

        @Override
        public void onStackChange(AEKey what, long delta) {
            if (Thread.currentThread() != this.ownerThread) {
                AELog.error("Storage monitor %s invoked a callback from the wrong thread; scheduling a full storage scan.",
                    this.monitor);
                cachedStacksNeedUpdate = true;
                return;
            }
            if (processingMonitorCallback || storage.isDispatchingListeners()) {
                AELog.error("Reentrant storage monitor callback from %s; scheduling a full storage scan.", this.monitor);
                invalidateCache();
                return;
            }
            processingMonitorCallback = true;
            try {
                applyMonitorDelta(what, delta, this.monitor);
            } finally {
                processingMonitorCallback = false;
            }
        }

        @Override
        public void onListUpdate() {
            if (Thread.currentThread() != this.ownerThread) {
                AELog.error("Storage monitor %s invalidated its list from the wrong thread.", this.monitor);
                cachedStacksNeedUpdate = true;
                return;
            }
            if (processingMonitorCallback || storage.isDispatchingListeners()) {
                AELog.error("Reentrant storage list update from %s; scheduling a full storage scan.", this.monitor);
                invalidateCache();
                return;
            }
            processingMonitorCallback = true;
            try {
                invalidateCache();
            } finally {
                processingMonitorCallback = false;
            }
        }
    }
}
