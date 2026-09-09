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

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.api.networking.energy.IPassiveEnergyGenerator;
import ae2.core.AELog;
import ae2.me.energy.StoredEnergyAmount;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Shared lazy caches for all energy services connected through Quartz Fibers.
 * <p>
 * The topology cache records only connected services and owns passive-generator selection. The independently lazy
 * {@link EnergyStorageCache} contains storage references and aggregate values, so storage structure and routing
 * changes do not require another Quartz Fiber topology walk.
 */
class EnergyOverlayGrid {
    /** Services in deterministic topology-discovery order. */
    private final ObjectArrayList<EnergyService> energyServices;

    /** Storage-group implementations in the same order as {@link #energyServices}. */
    private final ObjectArrayList<EnergyStorageGroup> energyStorageGroups = new ObjectArrayList<>();

    /** Which passive energy generator is currently active. */
    @Nullable
    private IPassiveEnergyGenerator currentPassiveGenerator;

    /** Null until a non-creative energy operation requests storage values or routing. */
    @Nullable
    private EnergyStorageCache storageCache;

    /** Number of connected services that currently contain at least one creative energy cell. */
    private int creativePowerServiceCount;

    /** Changes whenever an in-flight storage-cache build must not be installed. */
    private long storageCacheRevision;

    /** Guards against storage callbacks recursively requesting another storage-cache build. */
    private boolean storageCacheBuildInProgress;

    /** Cleared before this topology is detached from its services. */
    private boolean valid = true;

    private EnergyOverlayGrid(ObjectArrayList<EnergyService> energyServices) {
        this.energyServices = energyServices;
        int serviceCount = energyServices.size();
        for (int i = 0; i < serviceCount; i++) {
            var service = energyServices.get(i);
            this.energyStorageGroups.add(service.getEnergyStorageGroup());
            if (service.hasCreativeEnergyCell()) {
                this.creativePowerServiceCount++;
            }
        }
    }

    /**
     * Builds and shares a topology cache by discovering all services reachable from {@code startingService}.
     */
    static void buildCache(EnergyService startingService) {
        var connectedServices = new ReferenceOpenHashSet<EnergyService>();
        var pendingServices = new ObjectArrayList<EnergyService>();
        var discoveredServices = new ObjectArrayList<EnergyService>();
        pendingServices.add(startingService);

        while (!pendingServices.isEmpty()) {
            var service = pendingServices.pop();
            if (!connectedServices.add(service)) {
                continue;
            }

            discoveredServices.add(service);
            for (var connection : service.getOverlayGridConnections()) {
                pendingServices.addAll(connection.connectedEnergyServices());
            }
        }

        var overlayGrid = new EnergyOverlayGrid(discoveredServices);
        int serviceCount = discoveredServices.size();
        for (int i = 0; i < serviceCount; i++) {
            var service = discoveredServices.get(i);
            var previousOverlay = service.overlayGrid;
            if (previousOverlay != null) {
                AELog.error("Grid %s energy service already has a power graph assigned to it!", service.grid);
                previousOverlay.invalidate();
            }
        }

        for (int i = 0; i < serviceCount; i++) {
            discoveredServices.get(i).overlayGrid = overlayGrid;
        }
    }

    /**
     * Invalidates this topology and detaches only services that still point at this instance.
     */
    void invalidate() {
        if (!this.valid) {
            return;
        }

        this.valid = false;
        this.currentPassiveGenerator = null;
        invalidateStorageCache();

        int serviceCount = this.energyServices.size();
        for (int i = 0; i < serviceCount; i++) {
            var service = this.energyServices.get(i);
            if (service.overlayGrid == this) {
                service.overlayGrid = null;
            }
        }
    }

    /**
     * @return whether this topology is still the cache attached to {@code service}
     */
    boolean isAttachedTo(EnergyService service) {
        return this.valid && service.overlayGrid == this;
    }

    /**
     * Discards storage values and routing while retaining topology and passive-generator state.
     */
    void invalidateStorageCache() {
        this.storageCacheRevision++;
        var cache = this.storageCache;
        this.storageCache = null;
        if (cache != null) {
            cache.invalidate();
        }
    }

    /**
     * Marks values dirty without constructing an unused cache or invalidating the Quartz Fiber topology.
     */
    void refreshStorageValues(EnergyStorageGroup service, IAEPowerStorage storage) {
        var cache = this.storageCache;
        if (cache != null && this.valid) {
            cache.refreshStorage(service, storage);
        } else if (this.storageCacheBuildInProgress) {
            this.storageCacheRevision++;
        }
    }

    /**
     * Refreshes the inherent storage of one grid after its node count changed without rebuilding unrelated providers.
     */
    void refreshLocalStorage(EnergyStorageGroup service, IAEPowerStorage storage) {
        var cache = this.storageCache;
        if (cache != null && this.valid) {
            if (!cache.refreshStorageCapacity(service, storage)) {
                invalidateStorageCache();
            }
        } else if (this.storageCacheBuildInProgress) {
            this.storageCacheRevision++;
        }
    }

    double getStoredPower(EnergyService requestingService) {
        var cache = getStorageCache(requestingService);
        return isCurrent(cache) ? cache.getStoredPower() : 0;
    }

    double getMaximumPower(EnergyService requestingService) {
        var cache = getStorageCache(requestingService);
        return isCurrent(cache) ? cache.getMaximumPower() : 0;
    }

    double getEnergyDemand(EnergyService requestingService, double maximumDemand) {
        var cache = getStorageCache(requestingService);
        return isCurrent(cache) ? cache.getEnergyDemand(maximumDemand) : 0;
    }

    double extractPower(EnergyService requestingService, double amount, Actionable mode) {
        if (amount < StoredEnergyAmount.MIN_AMOUNT) {
            return 0;
        }
        var cache = getStorageCache(requestingService);
        return isCurrent(cache) ? cache.extractPower(amount, mode) : 0;
    }

    double injectPower(EnergyService requestingService, double amount, Actionable mode) {
        if (amount < StoredEnergyAmount.MIN_AMOUNT) {
            return amount;
        }
        var cache = getStorageCache(requestingService);
        return isCurrent(cache) ? cache.injectPower(amount, mode) : amount;
    }

    @Nullable
    IPassiveEnergyGenerator getCurrentPassiveGenerator() {
        return this.currentPassiveGenerator;
    }

    void setCurrentPassiveGenerator(@Nullable IPassiveEnergyGenerator currentPassiveGenerator) {
        this.currentPassiveGenerator = currentPassiveGenerator;
    }

    boolean hasCreativePowerSource() {
        return this.creativePowerServiceCount > 0;
    }

    void addCreativePowerService() {
        if (this.creativePowerServiceCount == Integer.MAX_VALUE) {
            throw new IllegalStateException("Creative power service count overflow");
        }
        this.creativePowerServiceCount++;
    }

    void removeCreativePowerService() {
        if (this.creativePowerServiceCount <= 0) {
            throw new IllegalStateException("Cannot remove a creative power service from an empty topology count");
        }
        this.creativePowerServiceCount--;
    }

    private EnergyStorageCache getStorageCache(EnergyService requestingService) {
        var cache = this.storageCache;
        if (cache != null && cache.isValid()) {
            return cache;
        }
        this.storageCache = null;

        if (this.storageCacheBuildInProgress) {
            AELog.error("Power storage recursively requested the overlay storage cache while it was being built.");
            this.storageCacheRevision++;
            return null;
        }

        long buildRevision = this.storageCacheRevision;
        this.storageCacheBuildInProgress = true;
        try {
            cache = new EnergyStorageCache(this.energyStorageGroups);
        } finally {
            this.storageCacheBuildInProgress = false;
        }

        if (isAttachedTo(requestingService) && this.storageCacheRevision == buildRevision) {
            this.storageCache = cache;
        } else {
            cache.invalidate();
        }
        return cache;
    }

    private boolean isCurrent(EnergyStorageCache cache) {
        return this.valid && cache != null && cache.isValid() && this.storageCache == cache;
    }
}

/** Per-service power transferred by routed storage operations since the last statistics sample. */
final class EnergyPowerStatistics {
    private double powerExtraction;
    private double powerInjection;

    void recordPowerExtraction(double amount) {
        this.powerExtraction = addFinite(this.powerExtraction, amount);
    }

    void recordPowerInjection(double amount) {
        this.powerInjection = addFinite(this.powerInjection, amount);
    }

    double getPowerExtraction() {
        return this.powerExtraction;
    }

    double getPowerInjection() {
        return this.powerInjection;
    }

    void reset() {
        this.powerExtraction = 0;
        this.powerInjection = 0;
    }

    private static double addFinite(double current, double amount) {
        double result = current + amount;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }
}

/**
 * Node registrations in one grid; no amounts or per-storage snapshots are retained here.
 */
final class EnergyStorageGroup {
    private final ObjectArrayList<IAEPowerStorage> storages = new ObjectArrayList<>();
    private final Reference2IntOpenHashMap<IAEPowerStorage> counts = new Reference2IntOpenHashMap<>();
    private final Reference2IntOpenHashMap<IAEPowerStorage> positions = new Reference2IntOpenHashMap<>();
    private final EnergyPowerStatistics powerStatistics;
    private int holes;

    EnergyStorageGroup(EnergyPowerStatistics powerStatistics) {
        this.powerStatistics = Objects.requireNonNull(powerStatistics, "powerStatistics");
    }

    void register(IAEPowerStorage storage) {
        Objects.requireNonNull(storage, "storage");
        int count = this.counts.getInt(storage);
        if (count == Integer.MAX_VALUE) {
            throw new IllegalStateException("Power storage registration count overflow");
        }
        this.counts.put(storage, count + 1);
        if (count == 0) {
            this.positions.put(storage, this.storages.size());
            this.storages.add(storage);
        }
    }

    void unregister(IAEPowerStorage storage) {
        int count = this.counts.getInt(storage);
        if (count == 0) {
            throw new IllegalStateException("Cannot remove an unregistered power storage");
        }
        if (count > 1) {
            this.counts.put(storage, count - 1);
            return;
        }
        this.counts.removeInt(storage);
        this.storages.set(this.positions.removeInt(storage), null);
        if (++this.holes >= (this.storages.size() + 1) / 2) {
            compact();
        }
    }

    private void compact() {
        int write = 0;
        for (int i = 0; i < this.storages.size(); i++) {
            var storage = this.storages.get(i);
            if (storage != null) {
                this.positions.put(storage, write);
                this.storages.set(write++, storage);
            }
        }
        this.storages.size(write);
        this.holes = 0;
    }

    boolean contains(IAEPowerStorage storage) {
        return this.counts.containsKey(storage);
    }

    void appendStorages(ObjectArrayList<IAEPowerStorage> destination) {
        for (int i = 0; i < this.storages.size(); i++) {
            var storage = this.storages.get(i);
            if (storage != null) {
                destination.add(storage);
            }
        }
    }

    void recordPowerExtraction(double amount) {
        this.powerStatistics.recordPowerExtraction(amount);
    }

    void recordPowerInjection(double amount) {
        this.powerStatistics.recordPowerInjection(amount);
    }
}

/**
 * One overlay's lazy storage list and shared totals. Four primitive contributions per registered
 * identity allow a value event to refresh only its source, without per-storage snapshot objects.
 */
final class EnergyStorageCache {
    private final ObjectArrayList<IAEPowerStorage> storages = new ObjectArrayList<>();
    private final Reference2ObjectMap<IAEPowerStorage, EnergyStorageGroup> owners = new Reference2ObjectOpenHashMap<>();
    private final Reference2IntOpenHashMap<IAEPowerStorage> indices = new Reference2IntOpenHashMap<>();
    private final Reference2IntOpenHashMap<EnergyStorageGroup> groupOrder = new Reference2IntOpenHashMap<>();
    private final Reference2DoubleOpenHashMap<EnergyStorageGroup> capacities = new Reference2DoubleOpenHashMap<>();
    private final IntArrayList extractionOrder = new IntArrayList();
    private final IntArrayList insertionOrder = new IntArrayList();
    private final IntArrayList pendingReads = new IntArrayList();
    private final double[] storedContributions;
    private final double[] maximumContributions;
    private final double[] extractableContributions;
    private final double[] receivableContributions;
    private final int[] priorities;
    private final int[] extractionPositions;
    private final int[] insertionPositions;
    private final boolean[] dirty;
    private final boolean[] quarantined;
    private final int[] capacitySources;
    private int storedSources;
    private int maximumSources;
    private int extractableSources;
    private int receivableSources;
    private int totalsRebuildCount;
    private double storedPower;
    private double maximumPower;
    private double extractablePower;
    private double receivablePower;
    private boolean valid = true;
    private boolean totalsDirty;
    private boolean orderDirty = true;
    private boolean reading;
    private boolean operating;
    @Nullable
    private IAEPowerStorage activeStorage;
    private int firstExtractable;
    private int firstReceivable;

    EnergyStorageCache(ObjectArrayList<EnergyStorageGroup> groups) {
        this.capacitySources = new int[groups.size()];
        this.indices.defaultReturnValue(-1);
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            this.groupOrder.put(group, i);
            int start = this.storages.size();
            group.appendStorages(this.storages);
            for (int j = start; j < this.storages.size(); j++) {
                var storage = this.storages.get(j);
                if (this.owners.put(storage, group) != null) {
                    AELog.error("Power storage %s is registered in multiple grids of one overlay.", describeStorage(storage));
                    invalidate();
                }
                this.indices.put(storage, j);
                this.extractionOrder.add(j);
                this.pendingReads.add(j);
            }
        }
        int size = this.storages.size();
        this.storedContributions = new double[size];
        this.maximumContributions = new double[size];
        this.extractableContributions = new double[size];
        this.receivableContributions = new double[size];
        this.priorities = new int[size];
        this.extractionPositions = new int[size];
        this.insertionPositions = new int[size];
        this.dirty = new boolean[size];
        this.quarantined = new boolean[size];
        Arrays.fill(this.dirty, true);
        ensureValues();
    }

    boolean isValid() {
        return this.valid;
    }

    void invalidate() {
        this.valid = false;
    }

    private static double usablePower(double amount) {
        return amount < StoredEnergyAmount.MIN_AMOUNT ? 0 : amount;
    }

    private static int sourceCountChange(double previous, double current) {
        return (current > 0 ? 1 : 0) - (previous > 0 ? 1 : 0);
    }

    private static double addAmount(double current, double amount) {
        double result = current + amount;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double requirePowerAmount(double amount, String name) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return amount;
    }

    private static String describeStorage(@Nullable IAEPowerStorage storage) {
        return storage == null ? "<none>" : storage.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(storage));
    }

    double getStoredPower() {
        ensureValues();
        return this.valid ? this.storedPower : 0;
    }

    double getMaximumPower() {
        ensureValues();
        return this.valid ? this.maximumPower : 0;
    }

    /**
     * Diagnostic count of precision-recovery scans over the cached contributions.
     */
    int getTotalsRebuildCount() {
        return this.totalsRebuildCount;
    }

    double getEnergyDemand(double maximumDemand) {
        return transfer(maximumDemand, Actionable.SIMULATE, true);
    }

    double extractPower(double amount, Actionable mode) {
        return transfer(amount, mode, false);
    }

    double injectPower(double amount, Actionable mode) {
        return amount - transfer(amount, mode, true);
    }

    void refreshStorage(EnergyStorageGroup group, IAEPowerStorage storage) {
        if (!this.valid) {
            return;
        }
        if (this.owners.get(storage) != group) {
            AELog.error("Power-storage value event for %s was posted to the wrong grid.", describeStorage(storage));
            return;
        }
        if (storage == this.activeStorage && !this.reading) {
            return;
        }
        if (this.reading || this.operating) {
            AELog.error("Power storage changed while the overlay was reading or routing energy; invalidating the route.");
            invalidate();
            return;
        }
        int index = this.indices.getInt(storage);
        if (!this.dirty[index]) {
            this.dirty[index] = true;
            this.pendingReads.add(index);
        }
    }

    /**
     * Refreshes the inherent buffer immediately, without querying any other registered storage.
     */
    boolean refreshStorageCapacity(EnergyStorageGroup group, IAEPowerStorage storage) {
        if (!this.valid) {
            return false;
        }
        if (this.reading || this.operating) {
            AELog.error("Grid capacity changed while the overlay was reading or routing energy; invalidating the route.");
            invalidate();
            return false;
        }
        if (this.owners.get(storage) != group) {
            AELog.error("Cannot refresh inherent power storage %s in a different grid.", describeStorage(storage));
            invalidate();
            return false;
        }
        int index = this.indices.getInt(storage);
        this.dirty[index] = false;
        readContribution(index);
        return this.valid;
    }

    private void ensureValues() {
        if (this.reading || this.operating) {
            AELog.error("Reentrant request for overlay energy values.");
            invalidate();
            return;
        }
        if (!this.valid) {
            return;
        }
        for (int i = 0; i < this.pendingReads.size() && this.valid; i++) {
            int index = this.pendingReads.getInt(i);
            if (this.dirty[index]) {
                this.dirty[index] = false;
                readContribution(index);
            }
        }
        this.pendingReads.clear();
        if (this.totalsDirty && this.valid) {
            rebuildTotals();
        }
    }

    private void readContribution(int index) {
        var storage = this.storages.get(index);
        this.reading = true;
        try {
            double current = requirePowerAmount(storage.getAECurrentPower(), "current power");
            double maximum = requirePowerAmount(storage.getAEMaxPower(), "maximum power");
            int priority = storage.getPriority();
            double stored = 0;
            double capacity = 0;
            double extractable = 0;
            double receivable = 0;
            if (storage.isAEPublicPowerStorage()) {
                var flow = Objects.requireNonNull(storage.getPowerFlow(), "power flow");
                extractable = availablePower(storage, current, maximum, flow, false);
                receivable = availablePower(storage, current, maximum, flow, true);
                if (flow.isAllowExtraction()) {
                    stored = current;
                    capacity = maximum;
                }
            }
            if (this.valid) {
                this.orderDirty |= this.priorities[index] != priority;
                this.priorities[index] = priority;
                this.quarantined[index] = false;
                replaceContribution(index, stored, capacity, extractable, receivable);
            }
        } catch (RuntimeException exception) {
            quarantine(index, exception);
        } finally {
            this.reading = false;
        }
    }

    private void replaceContribution(int index, double stored, double maximum, double extractable, double receivable) {
        this.storedPower = replaceAmount(this.storedPower, this.storedContributions[index], stored, this.storedSources);
        this.maximumPower = replaceAmount(this.maximumPower, this.maximumContributions[index], maximum, this.maximumSources);
        this.extractablePower = replaceAmount(this.extractablePower, this.extractableContributions[index], extractable,
            this.extractableSources);
        this.receivablePower = replaceAmount(this.receivablePower, this.receivableContributions[index], receivable,
            this.receivableSources);
        this.storedSources += sourceCountChange(this.storedContributions[index], stored);
        this.maximumSources += sourceCountChange(this.maximumContributions[index], maximum);
        this.extractableSources += sourceCountChange(this.extractableContributions[index], extractable);
        this.receivableSources += sourceCountChange(this.receivableContributions[index], receivable);
        if (this.maximumContributions[index] != maximum) {
            var group = this.owners.get(this.storages.get(index));
            int groupIndex = this.groupOrder.getInt(group);
            this.capacities.put(group, replaceAmount(this.capacities.getDouble(group),
                this.maximumContributions[index], maximum, this.capacitySources[groupIndex]));
            this.capacitySources[groupIndex] += sourceCountChange(this.maximumContributions[index], maximum);
            this.orderDirty = true;
        }
        if (extractable > this.extractableContributions[index]) {
            this.firstExtractable = Math.min(this.firstExtractable, this.extractionPositions[index]);
        }
        if (receivable > this.receivableContributions[index]) {
            this.firstReceivable = Math.min(this.firstReceivable, this.insertionPositions[index]);
        }
        this.storedContributions[index] = stored;
        this.maximumContributions[index] = maximum;
        this.extractableContributions[index] = extractable;
        this.receivableContributions[index] = receivable;
    }

    private void quarantine(int index, RuntimeException exception) {
        if (!this.quarantined[index]) {
            AELog.error(exception, "Ignoring invalid power storage " + describeStorage(this.storages.get(index))
                + " until its next value or routing event.");
        }
        this.quarantined[index] = true;
        replaceContribution(index, 0, 0, 0, 0);
    }

    private void rebuildTotals() {
        // Saturation/cancellation can lose a small contribution. Sum cached primitives, never re-read providers.
        this.totalsRebuildCount++;
        this.storedPower = 0;
        this.maximumPower = 0;
        this.extractablePower = 0;
        this.receivablePower = 0;
        this.capacities.clear();
        for (int i = 0; i < this.storages.size(); i++) {
            this.storedPower = addAmount(this.storedPower, this.storedContributions[i]);
            this.maximumPower = addAmount(this.maximumPower, this.maximumContributions[i]);
            this.extractablePower = addAmount(this.extractablePower, this.extractableContributions[i]);
            this.receivablePower = addAmount(this.receivablePower, this.receivableContributions[i]);
            var group = this.owners.get(this.storages.get(i));
            this.capacities.put(group, addAmount(this.capacities.getDouble(group), this.maximumContributions[i]));
        }
        this.totalsDirty = false;
    }

    private void ensureOrder() {
        if (!this.orderDirty || !this.valid) {
            return;
        }
        // Contributions use permanent registration indices. Only these routing indices are sorted.
        this.extractionOrder.sort((left, right) -> {
            var leftGroup = this.owners.get(this.storages.get(left));
            var rightGroup = this.owners.get(this.storages.get(right));
            if (leftGroup != rightGroup) {
                int capacity = Double.compare(this.capacities.getDouble(rightGroup), this.capacities.getDouble(leftGroup));
                return capacity != 0 ? capacity : Integer.compare(this.groupOrder.getInt(leftGroup), this.groupOrder.getInt(rightGroup));
            }
            int priority = Integer.compare(this.priorities[right], this.priorities[left]);
            return priority != 0 ? priority : Integer.compare(left, right);
        });
        this.insertionOrder.clear();
        int start = 0;
        while (start < this.extractionOrder.size()) {
            var group = this.owners.get(this.storages.get(this.extractionOrder.getInt(start)));
            int end = start + 1;
            while (end < this.extractionOrder.size()
                && this.owners.get(this.storages.get(this.extractionOrder.getInt(end))) == group) {
                end++;
            }
            int bandEnd = end;
            while (bandEnd > start) {
                int bandStart = bandEnd - 1;
                int priority = this.priorities[this.extractionOrder.getInt(bandStart)];
                while (bandStart > start && this.priorities[this.extractionOrder.getInt(bandStart - 1)] == priority) {
                    bandStart--;
                }
                for (int i = bandStart; i < bandEnd; i++) {
                    int index = this.extractionOrder.getInt(i);
                    this.extractionPositions[index] = i;
                    this.insertionPositions[index] = this.insertionOrder.size();
                    this.insertionOrder.add(index);
                }
                bandEnd = bandStart;
            }
            start = end;
        }
        this.firstExtractable = 0;
        this.firstReceivable = 0;
        this.orderDirty = false;
    }

    private double transfer(double amount, Actionable mode, boolean injection) {
        requirePowerAmount(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        if (this.reading || this.operating) {
            AELog.error("Reentrant overlay energy operation.");
            invalidate();
            return 0;
        }
        if (!this.valid || amount < StoredEnergyAmount.MIN_AMOUNT) {
            return 0;
        }
        ensureValues();
        if (!this.valid) {
            return 0;
        }
        double target = Math.min(amount, injection ? this.receivablePower : this.extractablePower);
        if (mode == Actionable.SIMULATE || target < StoredEnergyAmount.MIN_AMOUNT) {
            return target;
        }
        ensureOrder();
        this.operating = true;
        double transferred = 0;
        int first = injection ? this.firstReceivable : this.firstExtractable;
        try {
            for (int position = first; position < this.storages.size() && this.valid; position++) {
                double remaining = target - transferred;
                if (remaining < StoredEnergyAmount.MIN_AMOUNT) {
                    break;
                }
                int index = (injection ? this.insertionOrder : this.extractionOrder).getInt(position);
                double available = injection ? this.receivableContributions[index] : this.extractableContributions[index];
                if (available < StoredEnergyAmount.MIN_AMOUNT) {
                    advanceEmptyPrefix(position, injection);
                    continue;
                }
                var storage = this.storages.get(index);
                this.activeStorage = storage;
                try {
                    double request = Math.min(remaining, available);
                    double result = injection ? storage.injectAEPower(request, mode)
                        : storage.extractAEPower(request, mode, PowerMultiplier.ONE);
                    if (!Double.isFinite(result) || result < 0 || result > request) {
                        throw new IllegalArgumentException("Invalid " + (injection ? "injection overflow" : "extraction")
                            + " " + result + " for requested " + request);
                    }
                    double moved = injection ? request - result : result;
                    transferred += moved;
                    var owner = this.owners.get(storage);
                    if (injection) {
                        owner.recordPowerInjection(moved);
                    } else {
                        owner.recordPowerExtraction(moved);
                    }
                    if (!this.valid) {
                        break;
                    }
                    readContribution(index);
                } catch (RuntimeException exception) {
                    quarantine(index, exception);
                } finally {
                    this.activeStorage = null;
                }
                if ((injection ? this.receivableContributions[index] : this.extractableContributions[index])
                    < StoredEnergyAmount.MIN_AMOUNT) {
                    advanceEmptyPrefix(position, injection);
                }
            }
        } finally {
            this.activeStorage = null;
            this.operating = false;
        }
        return transferred;
    }

    private void advanceEmptyPrefix(int position, boolean injection) {
        if (injection && position == this.firstReceivable) {
            this.firstReceivable++;
        } else if (!injection && position == this.firstExtractable) {
            this.firstExtractable++;
        }
    }

    private double availablePower(IAEPowerStorage storage, double current, double maximum,
                                  AccessRestriction flow, boolean injection) {
        if (!this.valid || !(injection ? flow.isAllowInsertion() : flow.isAllowExtraction())) {
            return 0;
        }
        double requested = usablePower(injection ? Math.max(0, maximum - current) : current);
        if (requested == 0) {
            return 0;
        }
        double result = injection ? storage.getReceivableAEPower(requested) : storage.getExtractableAEPower(requested);
        if (!Double.isFinite(result) || result < 0 || result > requested) {
            throw new IllegalArgumentException("Invalid available " + (injection ? "insertion" : "extraction")
                + " " + result + " for requested " + requested);
        }
        return usablePower(result);
    }

    private double replaceAmount(double total, double previous, double current, int sources) {
        if (previous == current) {
            return total;
        }
        // With no other nonzero source, the exact total is known, including normal drain/refill to zero.
        // Otherwise, cancellation may hide a smaller source and must still trigger precision recovery.
        if (sources == (previous > 0 ? 1 : 0)) {
            return current;
        }
        double delta = current - previous;
        double result = total + delta;
        if (!Double.isFinite(result) || result < 0 || total == Double.MAX_VALUE
            || result == total || previous > current && result < Math.ulp(total)) {
            this.totalsDirty = true;
        }
        return Math.max(0, Double.isFinite(result) ? result : Double.MAX_VALUE);
    }
}
