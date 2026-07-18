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

import ae2.api.behaviors.ExternalStorageMonitor;
import ae2.api.config.Actionable;
import ae2.api.config.StorageFilter;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.MEStorage;
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.core.AELog;
import ae2.core.localization.GuiText;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.Map;
import java.util.Objects;

/**
 * Combines several external storages that each handle a given key-space. External monitors are only subscribed while
 * this storage itself has listeners; standalone users retain the original polling behavior.
 */
public class CompositeStorage implements MEStorageMonitor, ITickingMonitor {
    private final ObjectList<ListenerRegistration> listeners = new ObjectArrayList<>();
    private final ObjectList<ListenerRegistration> listenerDispatchBuffer = new ObjectArrayList<>();
    private final Reference2ObjectMap<MEStorage, SourceGroup> sourceGroupsByStorage =
        new Reference2ObjectOpenHashMap<>();
    private final Reference2ObjectMap<ExternalStorageMonitor, SourceGroup> sourceGroups =
        new Reference2ObjectOpenHashMap<>();

    private Map<AEKeyType, MEStorage> storages;
    private KeyCounter cache = KeyCounter.saturating();
    private KeyCounter cacheScratch = KeyCounter.saturating();
    private KeyCounter eventCache = KeyCounter.saturating();
    private KeyCounter eventScratch = KeyCounter.saturating();
    private KeyCounter legacyCache = KeyCounter.saturating();
    private KeyCounter legacyScratch = KeyCounter.saturating();
    private boolean cacheInitialized;
    private boolean standaloneDirty = true;
    private boolean eventDirty = true;
    private boolean legacyDirty = true;
    private boolean hasLegacyStorage;
    private boolean sourcesBound;
    private boolean processingSourceCallback;
    private boolean dispatchingListeners;
    private boolean dispatchingListUpdate;
    private boolean listUpdatePending;

    public CompositeStorage(Map<AEKeyType, MEStorage> storages) {
        this.storages = Objects.requireNonNull(storages);
    }

    @SuppressWarnings("unused")
    public CompositeStorage() {
        this(new Object2ObjectOpenHashMap<>());
    }

    public void setStorages(Map<AEKeyType, MEStorage> storages) {
        if (this.sourcesBound) {
            unbindSources();
        }
        this.storages = Objects.requireNonNull(storages);
        if (!this.listeners.isEmpty()) {
            bindSources();
        }
        invalidateLocalCaches();
        requestListUpdate();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        var storage = this.storages.get(what.getType());
        return storage != null && storage.isPreferredStorageFor(what, source);
    }

    @Override
    public boolean isStickyStorageFor(AEKey what, IActionSource source) {
        var storage = this.storages.get(what.getType());
        return storage != null && storage.isStickyStorageFor(what, source);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = this.storages.get(what.getType());
        var inserted = storage != null ? storage.insert(what, amount, mode, source) : 0;
        if (inserted > 0 && mode == Actionable.MODULATE && !isEventDriven(storage)) {
            markLegacyDirty();
        }
        return inserted;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var storage = this.storages.get(what.getType());
        var extracted = storage != null ? storage.extract(what, amount, mode, source) : 0;
        if (extracted > 0 && mode == Actionable.MODULATE && !isEventDriven(storage)) {
            markLegacyDirty();
        }
        return extracted;
    }

    @Override
    public ITextComponent getDescription() {
        ITextComponent types = new TextComponentString("");
        boolean first = true;
        for (var keyType : this.storages.keySet()) {
            if (!first) {
                types.appendText(", ");
            } else {
                first = false;
            }
            types.appendSibling(keyType.getDescription());
        }
        return GuiText.ExternalStorage.text(types);
    }

    @Override
    public TickRateModulation onTick() {
        if (this.listeners.isEmpty()) {
            if (this.storages.isEmpty()) {
                return TickRateModulation.SLEEP;
            }
            return refreshStandalone() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
        }

        if (!this.hasLegacyStorage) {
            return TickRateModulation.SLEEP;
        }
        if (!this.cacheInitialized) {
            return TickRateModulation.SLOWER;
        }

        boolean changed = refreshLegacyStorages(!this.legacyDirty);
        return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (this.listeners.isEmpty()) {
            if (!this.cacheInitialized || this.standaloneDirty) {
                refreshStandalone();
            }
        } else {
            ensureMonitoredCache();
        }
        out.addAll(this.cache);
    }

    @Override
    public void addListener(MEStorageChangeListener listener, Object verificationToken) {
        for (int i = 0; i < this.listeners.size(); i++) {
            if (this.listeners.get(i).listener == listener) {
                throw new IllegalStateException("The storage listener is already registered.");
            }
        }

        boolean firstListener = this.listeners.isEmpty();
        this.listeners.add(new ListenerRegistration(listener, verificationToken));
        if (firstListener) {
            bindSources();
            invalidateLocalCaches();
        }
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
        if (this.listeners.isEmpty() && this.sourcesBound) {
            unbindSources();
            invalidateLocalCaches();
        }
    }

    private void bindSources() {
        if (this.sourcesBound) {
            throw new IllegalStateException("External storage sources are already bound.");
        }

        this.hasLegacyStorage = false;
        for (var storage : this.storages.values()) {
            if (storage instanceof ExternalStorageFacade facade) {
                facade.setChangeListener(null);
                var externalMonitor = facade.getExternalMonitor();
                if (externalMonitor != null) {
                    var group = this.sourceGroups.get(externalMonitor);
                    if (group == null) {
                        group = new SourceGroup(externalMonitor, facade.getStorageFilter(), Thread.currentThread());
                        this.sourceGroups.put(externalMonitor, group);
                    }
                    group.addFacade(facade);
                    this.sourceGroupsByStorage.put(storage, group);
                    continue;
                }
            }
            this.hasLegacyStorage = true;
        }
        this.sourcesBound = true;
        for (var group : this.sourceGroups.values()) {
            group.monitor.addListener(group.storageFilter, group, group);
        }
    }

    private void unbindSources() {
        for (var group : this.sourceGroups.values()) {
            group.monitor.removeListener(group);
        }
        this.sourceGroups.clear();
        this.sourceGroupsByStorage.clear();
        this.sourcesBound = false;
        this.hasLegacyStorage = false;
    }

    private boolean isEventDriven(MEStorage storage) {
        return storage != null && this.sourceGroupsByStorage.containsKey(storage);
    }

    private void invalidateLocalCaches() {
        this.cacheInitialized = false;
        this.standaloneDirty = true;
        this.eventDirty = true;
        this.legacyDirty = true;
    }

    private void ensureMonitoredCache() {
        if (!this.cacheInitialized) {
            scanEventStorages();
            scanLegacyStorages();
            rebuildTotalCache();
            this.cacheInitialized = true;
            this.eventDirty = false;
            this.legacyDirty = false;
            return;
        }

        boolean rebuilt = false;
        if (this.eventDirty) {
            scanEventStorages();
            this.eventDirty = false;
            rebuilt = true;
        }
        if (this.legacyDirty) {
            scanLegacyStorages();
            this.legacyDirty = false;
            rebuilt = true;
        }
        if (rebuilt) {
            rebuildTotalCache();
        }
    }

    private boolean refreshStandalone() {
        this.cacheScratch.reset();
        for (var storage : this.storages.values()) {
            storage.getAvailableStacks(this.cacheScratch);
        }
        this.cacheScratch.removeZeros();
        boolean changed = hasDifference(this.cache, this.cacheScratch);
        var previous = this.cache;
        this.cache = this.cacheScratch;
        this.cacheScratch = previous;
        this.cacheInitialized = true;
        this.standaloneDirty = false;
        return changed;
    }

    private void scanEventStorages() {
        this.eventScratch.reset();
        for (var storage : this.storages.values()) {
            if (isEventDriven(storage)) {
                storage.getAvailableStacks(this.eventScratch);
            }
        }
        this.eventScratch.removeZeros();
        var previous = this.eventCache;
        this.eventCache = this.eventScratch;
        this.eventScratch = previous;
    }

    private void scanLegacyStorages() {
        this.legacyScratch.reset();
        for (var storage : this.storages.values()) {
            if (!isEventDriven(storage)) {
                storage.getAvailableStacks(this.legacyScratch);
            }
        }
        this.legacyScratch.removeZeros();
        var previous = this.legacyCache;
        this.legacyCache = this.legacyScratch;
        this.legacyScratch = previous;
    }

    private boolean refreshLegacyStorages(boolean publishChanges) {
        var previous = this.legacyCache;
        scanLegacyStorages();
        boolean changed = hasDifference(previous, this.legacyCache);
        boolean publishedExactly = !publishChanges || publishDifference(previous, this.legacyCache);
        this.legacyDirty = false;
        if (!publishChanges || !publishedExactly) {
            rebuildTotalCache();
        }
        if (!publishedExactly) {
            requestListUpdate();
        }
        return changed;
    }

    private void rebuildTotalCache() {
        this.cacheScratch.reset();
        this.cacheScratch.addAll(this.eventCache);
        this.cacheScratch.addAll(this.legacyCache);
        this.cacheScratch.removeZeros();
        var previous = this.cache;
        this.cache = this.cacheScratch;
        this.cacheScratch = previous;
    }

    private boolean hasDifference(KeyCounter previous, KeyCounter replacement) {
        for (var entry : replacement) {
            if (entry.getLongValue() != previous.get(entry.getKey())) {
                return true;
            }
        }
        for (var entry : previous) {
            if (entry.getLongValue() > 0 && replacement.get(entry.getKey()) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean publishDifference(KeyCounter previous, KeyCounter replacement) {
        boolean exact = true;
        for (var entry : replacement) {
            long oldAmount = previous.get(entry.getKey());
            long delta = entry.getLongValue() - oldAmount;
            if (delta != 0 && !applyKnownDelta(this.cache, entry.getKey(), delta)) {
                exact = false;
            }
        }
        for (var entry : previous) {
            if (entry.getLongValue() > 0 && replacement.get(entry.getKey()) == 0
                && !applyKnownDelta(this.cache, entry.getKey(), -entry.getLongValue())) {
                exact = false;
            }
        }
        return exact;
    }

    private void applyEventDelta(AEKey what, long delta, Object source) {
        if (!this.cacheInitialized || this.eventDirty) {
            this.eventDirty = true;
            requestListUpdate();
            return;
        }
        if (!canApplyDelta(this.eventCache, what, delta, source)
            || !canApplyDelta(this.cache, what, delta, source)) {
            this.eventDirty = true;
            requestListUpdate();
            return;
        }
        applyDelta(this.eventCache, what, delta);
        applyDelta(this.cache, what, delta);
        notifyDelta(what, delta);
    }

    private boolean applyKnownDelta(KeyCounter target, AEKey what, long delta) {
        if (!canApplyDelta(target, what, delta, "Legacy external storage scan")) {
            return false;
        }
        applyDelta(target, what, delta);
        notifyDelta(what, delta);
        return true;
    }

    private boolean canApplyDelta(KeyCounter target, AEKey what, long delta, Object source) {
        if (what == null || delta == 0) {
            if (what == null) {
                AELog.error("%s reported a null storage key.", source);
            }
            return what != null;
        }
        long current = Math.max(0, target.get(what));
        if ((current == Long.MAX_VALUE && delta < 0)
            || (delta < 0 && (delta == Long.MIN_VALUE || current < -delta))) {
            AELog.error("%s reported delta %d for %s with cached amount %d.", source, delta, what, current);
            return false;
        }
        return true;
    }

    private void applyDelta(KeyCounter target, AEKey what, long delta) {
        long current = Math.max(0, target.get(what));
        long updated = delta > 0 && current > Long.MAX_VALUE - delta ? Long.MAX_VALUE : current + delta;
        if (updated == 0) {
            target.remove(what);
        } else {
            target.set(what, updated);
        }
    }

    private void markLegacyDirty() {
        if (this.listeners.isEmpty()) {
            this.standaloneDirty = true;
        } else if (!this.legacyDirty) {
            this.legacyDirty = true;
            requestListUpdate();
        }
    }

    private void notifyDelta(AEKey what, long delta) {
        dispatchListeners(what, delta, false);
    }

    private void notifyListUpdate() {
        dispatchListeners(null, 0, true);
    }

    private void requestListUpdate() {
        if (this.listeners.isEmpty()) {
            return;
        }
        if (this.dispatchingListeners || this.processingSourceCallback) {
            if (!this.dispatchingListUpdate) {
                this.listUpdatePending = true;
            }
        } else {
            notifyListUpdate();
        }
    }

    private void dispatchListeners(AEKey what, long delta, boolean listUpdate) {
        if (this.dispatchingListeners) {
            AELog.error("Reentrant composite storage listener notification; scheduling a list update.");
            if (!this.dispatchingListUpdate) {
                this.listUpdatePending = true;
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
        flushPendingListUpdate();
    }

    private void removeInactiveListeners() {
        for (int i = this.listeners.size() - 1; i >= 0; i--) {
            if (!this.listeners.get(i).active) {
                this.listeners.remove(i);
            }
        }
        if (this.listeners.isEmpty() && this.sourcesBound) {
            unbindSources();
            invalidateLocalCaches();
        }
    }

    private void flushPendingListUpdate() {
        if (this.listUpdatePending && !this.processingSourceCallback && !this.dispatchingListeners) {
            this.listUpdatePending = false;
            notifyListUpdate();
        }
    }

    private final class SourceGroup implements MEStorageChangeListener {
        private final ExternalStorageMonitor monitor;
        private final StorageFilter storageFilter;
        private final Thread ownerThread;
        private final ObjectList<ExternalStorageFacade> facades = new ObjectArrayList<>();

        private SourceGroup(ExternalStorageMonitor monitor, StorageFilter storageFilter, Thread ownerThread) {
            this.monitor = monitor;
            this.storageFilter = storageFilter;
            this.ownerThread = ownerThread;
        }

        private void addFacade(ExternalStorageFacade facade) {
            if (facade.getStorageFilter() != this.storageFilter) {
                throw new IllegalStateException("One external storage monitor cannot expose different filtered views.");
            }
            this.facades.add(facade);
        }

        private boolean accepts(AEKey what) {
            for (int i = 0; i < this.facades.size(); i++) {
                if (this.facades.get(i).acceptsMonitorKey(what)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isValid(Object verificationToken) {
            return verificationToken == this && sourcesBound && sourceGroups.get(this.monitor) == this;
        }

        @Override
        public void onStackChange(AEKey what, long delta) {
            if (Thread.currentThread() != this.ownerThread) {
                AELog.error("External storage %s invoked a callback from the wrong thread.", this.monitor);
                eventDirty = true;
                requestListUpdate();
                return;
            }
            if (processingSourceCallback || dispatchingListeners) {
                AELog.error("Reentrant external storage callback from %s; scheduling a list update.", this.monitor);
                eventDirty = true;
                requestListUpdate();
                return;
            }
            if (!accepts(what)) {
                AELog.error("External storage %s reported an incompatible resource %s.", this.monitor, what);
                eventDirty = true;
                requestListUpdate();
                return;
            }
            processingSourceCallback = true;
            try {
                applyEventDelta(what, delta, this.monitor);
            } finally {
                processingSourceCallback = false;
                flushPendingListUpdate();
            }
        }

        @Override
        public void onListUpdate() {
            if (Thread.currentThread() != this.ownerThread) {
                AELog.error("External storage %s invalidated its list from the wrong thread.", this.monitor);
            }
            eventDirty = true;
            requestListUpdate();
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
