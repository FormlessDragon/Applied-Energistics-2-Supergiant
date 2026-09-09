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
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.core.AELog;
import ae2.hooks.ticking.TickHandler;
import ae2.me.helpers.InterestManager;
import ae2.me.helpers.StackWatcher;
import ae2.me.storage.NetworkStorage;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class StorageService implements IStorageService, IGridServiceProvider {

    private final Reference2ObjectMap<IGridNode, ProviderState> nodeProviders = new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<IStorageProvider, ProviderState> globalProviders = new Reference2ObjectOpenHashMap<>();
    private final SetMultimap<AEKey, StackWatcher<IStorageWatcherNode>> interests = HashMultimap.create();
    private final InterestManager<StackWatcher<IStorageWatcherNode>> interestManager = new InterestManager<>(this.interests);
    private final NetworkStorage storage = new NetworkStorage(this::getCachedInventory, this::invalidateCache);
    private KeyCounter cachedAvailableStacks = KeyCounter.saturating();
    private KeyCounter cachedAvailableScratch = KeyCounter.saturating();
    private final Reference2ObjectMap<IGridNode, StackWatcher<IStorageWatcherNode>> watchers =
        new Reference2ObjectOpenHashMap<>();

    private boolean cachedStacksNeedUpdate = true;
    private boolean cacheInitialized;
    private final ObjectArrayList<StackWatcher<IStorageWatcherNode>> watcherDispatch = new ObjectArrayList<>();
    private boolean processingMonitorCallback;
    private boolean rebuildingCache;

    @Override
    public void onServerEndTick() {
        boolean cacheIsObserved = !this.interestManager.isEmpty() || this.storage.hasListeners();
        if (cacheIsObserved && this.cachedStacksNeedUpdate) {
            updateCachedStacks();
        }
    }

    private static long sanitizeAmount(long amount) {
        return Math.max(0, amount);
    }

    private void updateCachedStacks() {
        if (this.rebuildingCache) {
            return;
        }
        this.rebuildingCache = true;
        this.cachedStacksNeedUpdate = false;
        try {
            rebuildCachedStacks();
        } catch (RuntimeException exception) {
            this.cachedStacksNeedUpdate = true;
            AELog.error(exception, "Network storage cache rebuild or notification failed; scheduling another refresh.");
        } finally {
            try {
                if (this.cachedStacksNeedUpdate) {
                    this.storage.postListUpdate();
                }
            } finally {
                this.rebuildingCache = false;
            }
        }
    }

    private void rebuildCachedStacks() {
        var previous = this.cachedAvailableStacks;
        var replacement = this.cachedAvailableScratch;

        replacement.reset();
        if (!this.storage.getAvailableStacksRaw(replacement) || this.cachedStacksNeedUpdate) {
            this.cachedStacksNeedUpdate = true;
            return;
        }
        replacement.removeZeros();
        this.cachedAvailableStacks = replacement;
        this.cachedAvailableScratch = previous;

        if (!this.cacheInitialized) {
            this.cacheInitialized = true;
            for (var entry : replacement) {
                var amount = sanitizeAmount(entry.getLongValue());
                if (amount > 0) {
                    postWatcherUpdate(entry.getKey(), amount, 0);
                }
            }
            return;
        }

        for (var entry : replacement) {
            var what = entry.getKey();
            var newAmount = sanitizeAmount(entry.getLongValue());
            var oldAmount = sanitizeAmount(previous.get(what));
            if (newAmount != oldAmount) {
                postWatcherUpdate(what, newAmount, oldAmount);
                this.storage.postChange(what, newAmount - oldAmount);
            }
        }

        for (var entry : previous) {
            var what = entry.getKey();
            var oldAmount = sanitizeAmount(entry.getLongValue());
            if (oldAmount > 0 && replacement.get(what) == 0) {
                postWatcherUpdate(what, 0, oldAmount);
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
        if (this.rebuildingCache) {
            // A child rebuild publishes its difference before copying its authoritative cache into our scan.
            if (source instanceof NetworkStorage network && this.storage.isEnumerating(network)) {
                return;
            }
            invalidateCache();
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

        if (delta > 0 && current > Long.MAX_VALUE - delta) {
            AELog.error(
                "Storage monitor %s reported delta %d for %s with cached amount %d, which saturates the aggregate; "
                    + "scheduling a full storage scan.",
                source, delta, what, current);
            invalidateCache();
            return;
        }

        long updated = current + delta;
        if (updated == 0) {
            this.cachedAvailableStacks.remove(what);
        } else {
            this.cachedAvailableStacks.set(what, updated);
        }
        postWatcherUpdate(what, updated, current);
        this.storage.postChange(what, updated - current);
    }

    private void postWatcherUpdate(AEKey what, long newAmount, long previousAmount) {
        int start = this.watcherDispatch.size();
        this.watcherDispatch.addAll(this.interestManager.get(what));
        for (var watcher : this.interestManager.getAllStacksWatchers()) {
            if (!this.interestManager.get(what).contains(watcher)) {
                this.watcherDispatch.add(watcher);
            }
        }
        int end = this.watcherDispatch.size();
        try {
            for (int i = start; i < end; i++) {
                var watcher = this.watcherDispatch.get(i);
                if (watcher.isWatching(what)) {
                    try {
                        watcher.getHost().onStackChange(what, newAmount, previousAmount);
                    } catch (RuntimeException exception) {
                        AELog.error(exception, "Storage watcher failed for " + what);
                    }
                }
            }
        } finally {
            this.watcherDispatch.size(start);
        }
    }

    @Override
    public void addNode(IGridNode node, NBTTagCompound savedData) {
        var storageProvider = node.getService(IStorageProvider.class);
        if (storageProvider != null) {
            var state = new ProviderState(storageProvider);
            this.nodeProviders.put(node, state);
            try {
                state.mount();
            } catch (RuntimeException exception) {
                this.nodeProviders.remove(node, state);
                throw exception;
            }
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
    public MEStorageMonitor getInventory() {
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
        if (this.globalProviders.containsKey(provider)) {
            throw new IllegalArgumentException("Duplicate storage provider registration for " + provider);
        }

        var state = new ProviderState(provider);
        this.globalProviders.put(provider, state);
        try {
            state.mount();
        } catch (RuntimeException exception) {
            this.globalProviders.remove(provider, state);
            throw exception;
        }
    }

    @Override
    public void removeGlobalStorageProvider(IStorageProvider provider) {
        var state = this.globalProviders.remove(provider);
        if (state != null) {
            state.unmount();
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
        var state = this.globalProviders.get(provider);
        if (state != null) {
            state.update();
            return;
        }

        throw new IllegalArgumentException("Storage provider " + provider + " is not part of this grid.");
    }

    @Override
    public void invalidateCache() {
        if (!this.cachedStacksNeedUpdate) {
            this.cachedStacksNeedUpdate = true;
            if (!this.rebuildingCache) {
                this.storage.postListUpdate();
            }
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
        private final ReferenceSet<MEStorageMonitor> inventories = new ReferenceOpenHashSet<>();
        private final Reference2ObjectMap<MEStorageMonitor, SourceListener> monitorListeners =
            new Reference2ObjectOpenHashMap<>();
        private boolean mounted;

        ProviderState(IStorageProvider provider) {
            this.provider = provider;
        }

        private void mount() {
            Preconditions.checkState(!this.mounted, "Can't mount a provider's inventories when it's already mounted");

            this.mounted = true;
            try {
                this.provider.mountInventories(this);
                invalidateCache();
            } catch (RuntimeException exception) {
                AELog.error(exception, String.format("Storage provider mount failed; rolling back provider %s.",
                    this.provider));
                try {
                    this.unmount();
                } catch (RuntimeException cleanupException) {
                    AELog.error(cleanupException, String.format(
                        "Storage provider rollback encountered a cleanup failure for %s.", this.provider));
                    exception.addSuppressed(cleanupException);
                }
                throw exception;
            }
        }

        @Override
        public void mount(MEStorageMonitor inventory, int priority) {
            Preconditions.checkState(this.mounted, "Cannot use StorageMounts after the storage has been unmounted.");

            if (!this.inventories.add(inventory)) {
                throw new IllegalStateException("Cannot mount the same inventory twice.");
            }

            SourceListener listener = null;
            boolean networkMounted = false;
            try {
                storage.mount(priority, inventory);
                networkMounted = true;
                listener = new SourceListener(this, inventory, Thread.currentThread());
                this.monitorListeners.put(inventory, listener);
                inventory.addListener(listener, inventory);
                invalidateCache();
            } catch (RuntimeException exception) {
                AELog.error(exception, String.format("Storage inventory mount failed; rolling back inventory %s.",
                    inventory));
                if (listener != null) {
                    this.monitorListeners.remove(inventory);
                    try {
                        inventory.removeListener(listener);
                    } catch (RuntimeException cleanupException) {
                        AELog.error(cleanupException, String.format(
                            "Failed to remove listener from rolled-back inventory %s.", inventory));
                    }
                }
                if (networkMounted) {
                    storage.unmount(inventory);
                }
                this.inventories.remove(inventory);
                throw exception;
            }
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
            RuntimeException failure = null;
            for (var inventory : this.inventories) {
                var listener = this.monitorListeners.remove(inventory);
                if (listener == null) {
                    AELog.error("Mounted storage %s has no monitor listener during unmount.", inventory);
                } else {
                    try {
                        listener.monitor.removeListener(listener);
                    } catch (RuntimeException exception) {
                        AELog.error(exception, String.format("Failed to remove storage monitor listener from %s.",
                            inventory));
                        failure = failure == null ? exception : failure;
                    }
                }
                try {
                    storage.unmount(inventory);
                } catch (RuntimeException exception) {
                    AELog.error(exception, String.format("Failed to unmount storage %s from the network.", inventory));
                    failure = failure == null ? exception : failure;
                }
            }
            this.inventories.clear();
            this.monitorListeners.clear();
            invalidateCache();
            if (failure != null) {
                throw failure;
            }
        }
    }

    private final class SourceListener implements MEStorageChangeListener {
        private final ProviderState provider;
        private final MEStorageMonitor monitor;
        private final Thread ownerThread;
        private final AtomicBoolean invalidationQueued = new AtomicBoolean();

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
                queueThreadInvalidation();
                return;
            }
            if (!isValid(this.monitor)) {
                AELog.error("Detached storage monitor %s invoked a content callback.", this.monitor);
                return;
            }
            if (this.invalidationQueued.get()) {
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
                queueThreadInvalidation();
                return;
            }
            if (!isValid(this.monitor)) {
                AELog.error("Detached storage monitor %s invalidated its list.", this.monitor);
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

        private void queueThreadInvalidation() {
            if (this.invalidationQueued.compareAndSet(false, true)) {
                AELog.error("Storage monitor %s invoked a callback from the wrong thread; queuing a server-thread invalidation.",
                    this.monitor);
                TickHandler.instance().addCallable(null, () -> {
                    this.invalidationQueued.set(false);
                    if (isValid(this.monitor)) {
                        invalidateCache();
                    }
                });
            }
        }
    }
}
