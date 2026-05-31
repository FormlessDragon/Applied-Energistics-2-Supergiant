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
import ae2.api.stacks.AEKey2LongMap;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageMounts;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorage;
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

import java.io.IOException;

public class StorageService implements IStorageService, IGridServiceProvider {

    private final Reference2ObjectMap<IGridNode, ProviderState> nodeProviders = new Reference2ObjectOpenHashMap<>();
    private final ObjectList<ProviderState> globalProviders = new ObjectArrayList<>();
    private final SetMultimap<AEKey, StackWatcher<IStorageWatcherNode>> interests = HashMultimap.create();
    private final InterestManager<StackWatcher<IStorageWatcherNode>> interestManager = new InterestManager<>(this.interests);
    private final NetworkStorage storage = new NetworkStorage();
    private final KeyCounter cachedAvailableStacks = new KeyCounter();
    private final AEKey2LongMap cachedAvailableAmounts = new AEKey2LongMap.OpenHashMap();
    private final Reference2ObjectMap<IGridNode, StackWatcher<IStorageWatcherNode>> watchers =
        new Reference2ObjectOpenHashMap<>();

    private boolean cachedStacksNeedUpdate = true;

    @Override
    public void onServerEndTick() {
        if (this.interestManager.isEmpty()) {
            this.cachedStacksNeedUpdate = true;
        } else {
            updateCachedStacks();
        }
    }

    private void updateCachedStacks() {
        this.cachedStacksNeedUpdate = false;

        this.cachedAvailableStacks.clear();
        this.storage.getAvailableStacks(this.cachedAvailableStacks);
        this.cachedAvailableStacks.removeEmptySubmaps();

        for (var entry : this.cachedAvailableStacks) {
            var what = entry.getKey();
            var newAmount = entry.getLongValue();
            if (newAmount != this.cachedAvailableAmounts.getLong(what)) {
                postWatcherUpdate(what, newAmount);
            }
        }

        for (var what : new ReferenceOpenHashSet<>(this.cachedAvailableAmounts.keySet())) {
            if (this.cachedAvailableStacks.get(what) == 0) {
                postWatcherUpdate(what, 0);
            }
        }

        this.cachedAvailableAmounts.clear();
        for (var entry : this.cachedAvailableStacks) {
            this.cachedAvailableAmounts.put(entry.getKey(), entry.getLongValue());
        }
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
    public void addNode(IGridNode node, net.minecraft.nbt.NBTTagCompound savedData) {
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
        for (var state : this.globalProviders) {
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
        var iterator = this.globalProviders.iterator();
        while (iterator.hasNext()) {
            var state = iterator.next();
            if (state.provider == provider) {
                iterator.remove();
                state.unmount();
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
        for (var state : this.globalProviders) {
            if (state.provider == provider) {
                state.update();
                return;
            }
        }

        throw new IllegalArgumentException("Storage provider " + provider + " is not part of this grid.");
    }

    @Override
    public void invalidateCache() {
        this.cachedStacksNeedUpdate = true;
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
                storage.unmount(inventory);
            }
            this.inventories.clear();
            invalidateCache();
        }
    }
}
