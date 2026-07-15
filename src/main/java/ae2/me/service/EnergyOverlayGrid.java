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

import java.util.Comparator;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.energy.IAEPowerStorage;
import ae2.api.networking.energy.IPassiveEnergyGenerator;
import ae2.api.networking.energy.PowerStorageSnapshotBuilder;
import ae2.core.AELog;
import ae2.me.energy.StoredEnergyAmount;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/**
 * Shared lazy caches for all energy services connected through Quartz Fibers.
 * <p>
 * The topology cache records only connected services and owns passive-generator selection. The independently lazy
 * {@link EnergyStorageCache} contains routed storage entries and aggregate values, so storage structure and routing
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
     * Incrementally refreshes one storage if the storage cache has already been built.
     */
    void refreshStorageValues(EnergyStorageGroup service, IAEPowerStorage storage) {
        var cache = this.storageCache;
        if (cache != null && this.valid) {
            cache.refreshStorage(service, storage);
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
            throw new IllegalStateException("Power storage snapshot recursively requested the overlay storage cache");
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
        return this.valid && cache.isValid() && this.storageCache == cache;
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

/** One service's production storage registrations, identity lookup and immediate tick statistics. */
final class EnergyStorageGroup {
    private final ObjectArrayList<EnergyStorageRegistration> registeredStorages = new ObjectArrayList<>();
    private final Reference2ObjectMap<IAEPowerStorage, EnergyStorageRegistration> registeredStorageLookup =
        new Reference2ObjectOpenHashMap<>();
    private final EnergyPowerStatistics powerStatistics;
    private long nextStorageRegistrationSequence;

    EnergyStorageGroup(EnergyPowerStatistics powerStatistics) {
        this.powerStatistics = Objects.requireNonNull(powerStatistics, "powerStatistics");
    }

    void register(IAEPowerStorage storage) {
        var existingRegistration = this.registeredStorageLookup.get(storage);
        if (existingRegistration != null) {
            if (existingRegistration.referenceCount == Integer.MAX_VALUE) {
                throw new IllegalStateException("Power storage registration reference count overflow");
            }
            existingRegistration.referenceCount++;
            return;
        }

        if (this.nextStorageRegistrationSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Power storage registration sequence overflow");
        }
        var registration = new EnergyStorageRegistration(storage, this.nextStorageRegistrationSequence++);
        this.registeredStorageLookup.put(storage, registration);
        this.registeredStorages.add(registration);
    }

    void unregister(IAEPowerStorage storage) {
        var registration = this.registeredStorageLookup.get(storage);
        if (registration == null || registration.referenceCount <= 0) {
            throw new IllegalStateException("Cannot remove an unregistered power storage");
        }

        registration.referenceCount--;
        if (registration.referenceCount > 0) {
            return;
        }

        this.registeredStorageLookup.remove(storage);
        int registrationCount = this.registeredStorages.size();
        for (int i = 0; i < registrationCount; i++) {
            if (this.registeredStorages.get(i) == registration) {
                this.registeredStorages.remove(i);
                return;
            }
        }
        throw new IllegalStateException("Power storage identity lookup and registration order diverged");
    }

    boolean contains(IAEPowerStorage storage) {
        return this.registeredStorageLookup.containsKey(storage);
    }

    ObjectArrayList<EnergyStorageRegistration> registrations() {
        return this.registeredStorages;
    }

    void recordPowerExtraction(double amount) {
        this.powerStatistics.recordPowerExtraction(amount);
    }

    void recordPowerInjection(double amount) {
        this.powerStatistics.recordPowerInjection(amount);
    }
}

/** Identity registration retained across lazy storage-cache generations. */
final class EnergyStorageRegistration {
    /** Storage identity registered by a node service. */
    final IAEPowerStorage storage;
    /** Monotonic service-local sequence used to stabilize equal-priority ordering. */
    final long registrationSequence;
    /** Number of node registrations that expose the same storage identity. */
    int referenceCount = 1;

    EnergyStorageRegistration(IAEPowerStorage storage, long registrationSequence) {
        this.storage = Objects.requireNonNull(storage, "storage");
        if (registrationSequence < 0) {
            throw new IllegalArgumentException("registrationSequence must be non-negative");
        }
        this.registrationSequence = registrationSequence;
    }
}

/**
 * Directly constructible package-private core for storage snapshots, aggregate accounting and routed hot traversal.
 * <p>
 * A cache instance is one immutable routing generation. Snapshot values remain mutable and are corrected by absolute
 * reads after every real storage call. Invalidating a generation makes in-flight operations stop before touching
 * another entry.
 */
final class EnergyStorageCache {
    private static final Comparator<ServiceStorageCache> SERVICE_CAPACITY_ORDER = (left, right) -> {
        int capacityComparison = Double.compare(right.maximumPower.value(), left.maximumPower.value());
        return capacityComparison != 0
            ? capacityComparison
            : Integer.compare(left.topologyIndex, right.topologyIndex);
    };

    private static final Comparator<StorageEntry> EXTRACTION_ORDER = (left, right) -> {
        int priorityComparison = Integer.compare(right.priority, left.priority);
        return priorityComparison != 0
            ? priorityComparison
            : Long.compare(left.registrationSequence, right.registrationSequence);
    };

    private static final Comparator<StorageEntry> INSERTION_ORDER = Comparator.comparingInt((StorageEntry left) -> left.priority).thenComparingLong(left -> left.registrationSequence);

    private final ObjectArrayList<ServiceStorageCache> serviceGroups = new ObjectArrayList<>();
    private final Reference2ObjectMap<IAEPowerStorage, StorageEntry> entriesByStorage =
        new Reference2ObjectOpenHashMap<>();
    private final PowerStorageSnapshotBuilder snapshotBuilder = new PowerStorageSnapshotBuilder();

    @Nullable
    private StorageEntry activeEntry;
    private boolean valid = true;
    private boolean snapshotReadInProgress;
    private final CompensatedPowerSum storedPower = new CompensatedPowerSum();
    private final CompensatedPowerSum maximumPower = new CompensatedPowerSum();
    private final CompensatedPowerSum extractablePower = new CompensatedPowerSum();
    private final CompensatedPowerSum energyDemand = new CompensatedPowerSum();

    /**
     * Builds a routing generation from service groups in topology-discovery order.
     *
     * @param groups real storage groups; the constructor reads registrations immediately and does not retain this list
     */
    EnergyStorageCache(ObjectArrayList<EnergyStorageGroup> groups) {
        int serviceCount = groups.size();
        for (int i = 0; i < serviceCount; i++) {
            var group = new ServiceStorageCache(this, groups.get(i), i);
            this.serviceGroups.add(group);
            buildServiceGroup(group);
        }

        this.serviceGroups.sort(SERVICE_CAPACITY_ORDER);
    }

    boolean isValid() {
        return this.valid;
    }

    void invalidate() {
        this.valid = false;
    }

    double getStoredPower() {
        return this.valid ? this.storedPower.value() : 0;
    }

    double getMaximumPower() {
        return this.valid ? this.maximumPower.value() : 0;
    }

    double getEnergyDemand(double maximumDemand) {
        requirePowerAmount(maximumDemand, "maximumDemand");
        return this.valid ? Math.min(maximumDemand, this.energyDemand.value()) : 0;
    }

    double extractPower(double amount, Actionable mode) {
        requirePowerAmount(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        if (!this.valid || amount < StoredEnergyAmount.MIN_AMOUNT) {
            return 0;
        }

        double extractionTarget = Math.min(amount, this.extractablePower.value());
        if (mode == Actionable.SIMULATE || extractionTarget <= 0) {
            return extractionTarget;
        }

        double extractedPower = 0;
        int groupCount = this.serviceGroups.size();
        for (int groupIndex = 0; groupIndex < groupCount && extractedPower < extractionTarget; groupIndex++) {
            if (!this.valid) {
                break;
            }

            double remainingPower = extractionTarget - extractedPower;
            if (remainingPower < StoredEnergyAmount.MIN_AMOUNT) {
                break;
            }

            var group = this.serviceGroups.get(groupIndex);
            double groupTarget = Math.min(remainingPower, group.extractablePower.value());
            double groupExtracted = 0;
            int entryCount = group.extractionOrder.size();
            for (int entryIndex = 0; entryIndex < entryCount && groupExtracted < groupTarget; entryIndex++) {
                if (!this.valid) {
                    return extractedPower;
                }

                double groupRemainingPower = groupTarget - groupExtracted;
                if (groupRemainingPower < StoredEnergyAmount.MIN_AMOUNT) {
                    break;
                }

                var entry = group.extractionOrder.get(entryIndex);
                double entryTarget = Math.min(groupRemainingPower, entry.extractablePower);
                if (entryTarget < StoredEnergyAmount.MIN_AMOUNT) {
                    continue;
                }

                double extracted = callExtract(entry, entryTarget);
                groupExtracted += extracted;
                extractedPower += extracted;
            }
        }

        return Math.min(extractedPower, extractionTarget);
    }

    double injectPower(double amount, Actionable mode) {
        requirePowerAmount(amount, "amount");
        Objects.requireNonNull(mode, "mode");
        if (!this.valid || amount < StoredEnergyAmount.MIN_AMOUNT) {
            return amount;
        }

        double insertionTarget = Math.min(amount, this.energyDemand.value());
        if (mode == Actionable.SIMULATE || insertionTarget <= 0) {
            return amount - insertionTarget;
        }

        double injectedPower = 0;
        int groupCount = this.serviceGroups.size();
        for (int groupIndex = 0; groupIndex < groupCount && injectedPower < insertionTarget; groupIndex++) {
            if (!this.valid) {
                break;
            }

            double remainingPower = insertionTarget - injectedPower;
            if (remainingPower < StoredEnergyAmount.MIN_AMOUNT) {
                break;
            }

            var group = this.serviceGroups.get(groupIndex);
            double groupTarget = Math.min(remainingPower, group.energyDemand.value());
            double groupInjected = 0;
            int entryCount = group.insertionOrder.size();
            for (int entryIndex = 0; entryIndex < entryCount && groupInjected < groupTarget; entryIndex++) {
                if (!this.valid) {
                    return amount - injectedPower;
                }

                double groupRemainingPower = groupTarget - groupInjected;
                if (groupRemainingPower < StoredEnergyAmount.MIN_AMOUNT) {
                    break;
                }

                var entry = group.insertionOrder.get(entryIndex);
                double entryTarget = Math.min(groupRemainingPower, entry.energyDemand);
                if (entryTarget < StoredEnergyAmount.MIN_AMOUNT) {
                    continue;
                }

                double injected = callInject(entry, entryTarget);
                groupInjected += injected;
                injectedPower += injected;
            }
        }

        return amount - Math.min(injectedPower, insertionTarget);
    }

    void refreshStorage(EnergyStorageGroup service, IAEPowerStorage storage) {
        if (!this.valid) {
            return;
        }

        var entry = this.entriesByStorage.get(storage);
        if (entry == null) {
            if (service.contains(storage)) {
                invalidate();
            } else {
                AELog.warn("Ignoring a power-storage value event for unregistered storage %s.",
                    describeStorage(storage));
            }
            return;
        }

        if (entry.group.service != service) {
            AELog.error("Power-storage value event for %s was posted to a different energy service.",
                describeStorage(storage));
            return;
        }

        if (entry == this.activeEntry) {
            return;
        }

        readStorageSnapshot(entry);
    }

    private static void requirePowerAmount(double amount, String name) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }

    private void buildServiceGroup(ServiceStorageCache group) {
        var registrations = group.service.registrations();
        int registrationCount = registrations.size();
        for (int i = 0; i < registrationCount; i++) {
            addStorage(group, registrations.get(i));
        }

        group.extractionOrder.sort(EXTRACTION_ORDER);
        group.insertionOrder.sort(INSERTION_ORDER);
    }

    private void addStorage(ServiceStorageCache group, EnergyStorageRegistration registration) {
        var storage = registration.storage;
        var existingEntry = this.entriesByStorage.get(storage);
        if (existingEntry != null) {
            AELog.error("Power storage %s is registered with multiple services in one energy overlay; "
                + "the later registration is ignored.", describeStorage(storage));
            return;
        }

        if (!collectStorageSnapshot(storage)) {
            return;
        }

        var powerFlow = this.snapshotBuilder.getPowerFlow();
        var entry = new StorageEntry(group, storage, registration.registrationSequence,
            this.snapshotBuilder.getMaximumPower(), this.snapshotBuilder.getPriority(), powerFlow,
            this.snapshotBuilder.isPublicStorage());
        this.entriesByStorage.put(storage, entry);
        if (entry.publicStorage && entry.allowExtraction) {
            group.extractionOrder.add(entry);
        }
        if (entry.publicStorage && entry.allowInsertion) {
            group.insertionOrder.add(entry);
        }
        applyCollectedSnapshot(entry);
    }

    private void readStorageSnapshot(StorageEntry entry) {
        if (this.snapshotReadInProgress) {
            AELog.error("Power storage %s emitted a reentrant event while its snapshot was being read; "
                + "the reentrant refresh was ignored.", describeStorage(entry.storage));
            return;
        }

        if (!collectStorageSnapshot(entry.storage)) {
            entry.replaceSnapshot(0, 0, 0, 0);
            return;
        }

        if (Double.compare(this.snapshotBuilder.getMaximumPower(), entry.routingMaximumPower) != 0
            || this.snapshotBuilder.isPublicStorage() != entry.publicStorage
            || this.snapshotBuilder.getPowerFlow() != entry.powerFlow
            || this.snapshotBuilder.getPriority() != entry.priority) {
            AELog.error("Power storage %s changed maximum capacity, public visibility, access restrictions or "
                + "priority without a ROUTING_CHANGED event; the storage cache is invalidated.",
                describeStorage(entry.storage));
            invalidate();
            return;
        }

        applyCollectedSnapshot(entry);
    }

    private boolean collectStorageSnapshot(IAEPowerStorage storage) {
        this.snapshotReadInProgress = true;
        this.snapshotBuilder.reset();
        try {
            storage.getPowerSnapshot(this.snapshotBuilder);
            if (!this.snapshotBuilder.isComplete()) {
                throw new IllegalStateException("Power storage did not supply a complete snapshot");
            }
            return true;
        } catch (RuntimeException exception) {
            AELog.error(exception, "Invalid power snapshot from storage " + describeStorage(storage)
                + "; its cached contribution is isolated until a valid refresh.");
            return false;
        } finally {
            this.snapshotReadInProgress = false;
        }
    }

    private void applyCollectedSnapshot(StorageEntry entry) {
        if (!entry.publicStorage) {
            entry.replaceSnapshot(0, 0, 0, 0);
            return;
        }

        entry.replaceSnapshot(
            entry.allowExtraction ? this.snapshotBuilder.getCurrentPower() : 0,
            entry.allowExtraction ? this.snapshotBuilder.getMaximumPower() : 0,
            entry.allowExtraction ? usableOperationPower(this.snapshotBuilder.getExtractablePower()) : 0,
            entry.allowInsertion ? usableOperationPower(this.snapshotBuilder.getReceivablePower()) : 0);
    }

    private double callExtract(StorageEntry entry, double amount) {
        beginActiveOperation(entry);
        double extracted = 0;
        try {
            double result = entry.storage.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.ONE);
            extracted = validateExtractResult(entry.storage, result, amount);
        } catch (RuntimeException exception) {
            AELog.error(exception, "Power extraction failed for storage " + describeStorage(entry.storage));
        } finally {
            finishActiveOperation(entry);
        }

        if (extracted > 0) {
            entry.group.service.recordPowerExtraction(extracted);
        }
        return extracted;
    }

    private double callInject(StorageEntry entry, double amount) {
        beginActiveOperation(entry);
        double injected = 0;
        try {
            double overflow = entry.storage.injectAEPower(amount, Actionable.MODULATE);
            injected = amount - validateOverflowResult(entry.storage, overflow, amount);
        } catch (RuntimeException exception) {
            AELog.error(exception, "Power injection failed for storage " + describeStorage(entry.storage));
        } finally {
            finishActiveOperation(entry);
        }

        if (injected > 0) {
            entry.group.service.recordPowerInjection(injected);
        }
        return injected;
    }

    private void beginActiveOperation(StorageEntry entry) {
        if (this.activeEntry != null) {
            throw new IllegalStateException("Power storage operation reentered the overlay energy cache");
        }
        this.activeEntry = entry;
    }

    private void finishActiveOperation(StorageEntry entry) {
        if (this.activeEntry != entry) {
            throw new IllegalStateException("Power storage operation active-entry state diverged");
        }

        this.activeEntry = null;
        if (this.valid) {
            readStorageSnapshot(entry);
        }
    }

    private static double usableOperationPower(double amount) {
        return amount < StoredEnergyAmount.MIN_AMOUNT ? 0 : amount;
    }

    private static double validateExtractResult(IAEPowerStorage storage, double result, double requested) {
        if (Double.isNaN(result) || result < 0) {
            AELog.error("Power storage %s returned invalid extracted power %s.", describeStorage(storage), result);
            return 0;
        }
        if (!Double.isFinite(result) || result > requested) {
            AELog.error("Power storage %s returned extracted power %s above the requested %s.",
                describeStorage(storage), result, requested);
            return requested;
        }
        return result;
    }

    private static double validateOverflowResult(IAEPowerStorage storage, double result, double requested) {
        if (Double.isNaN(result) || !Double.isFinite(result) || result > requested) {
            AELog.error("Power storage %s returned invalid injection overflow %s for requested power %s.",
                describeStorage(storage), result, requested);
            return requested;
        }
        if (result < 0) {
            AELog.error("Power storage %s returned negative injection overflow %s.", describeStorage(storage), result);
            return 0;
        }
        return result;
    }

    private void replaceSnapshotContribution(StorageEntry entry, double storedPower, double maximumPower,
                                             double extractablePower, double energyDemand) {
        var group = entry.group;
        group.storedPower.replace(entry.storedPower, storedPower);
        group.maximumPower.replace(entry.maximumPower, maximumPower);
        group.extractablePower.replace(entry.extractablePower, extractablePower);
        group.energyDemand.replace(entry.energyDemand, energyDemand);

        this.storedPower.replace(entry.storedPower, storedPower);
        this.maximumPower.replace(entry.maximumPower, maximumPower);
        this.extractablePower.replace(entry.extractablePower, extractablePower);
        this.energyDemand.replace(entry.energyDemand, energyDemand);
    }

    private static String describeStorage(IAEPowerStorage storage) {
        return storage.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(storage));
    }

    /** Storage entries belonging to one service, kept contiguous in overlay operation order. */
    private static final class ServiceStorageCache {
        private final EnergyStorageCache owner;
        private final EnergyStorageGroup service;
        private final int topologyIndex;
        private final ObjectArrayList<StorageEntry> extractionOrder = new ObjectArrayList<>();
        private final ObjectArrayList<StorageEntry> insertionOrder = new ObjectArrayList<>();

        private final CompensatedPowerSum storedPower = new CompensatedPowerSum();
        private final CompensatedPowerSum maximumPower = new CompensatedPowerSum();
        private final CompensatedPowerSum extractablePower = new CompensatedPowerSum();
        private final CompensatedPowerSum energyDemand = new CompensatedPowerSum();

        private ServiceStorageCache(EnergyStorageCache owner, EnergyStorageGroup service, int topologyIndex) {
            this.owner = owner;
            this.service = service;
            this.topologyIndex = topologyIndex;
        }
    }

    /** One identity-addressed storage contribution in a storage-cache generation. */
    private static final class StorageEntry {
        private final ServiceStorageCache group;
        private final IAEPowerStorage storage;
        private final long registrationSequence;
        private final double routingMaximumPower;
        private final int priority;
        private final AccessRestriction powerFlow;
        private final boolean publicStorage;
        private final boolean allowExtraction;
        private final boolean allowInsertion;

        private double storedPower;
        private double maximumPower;
        private double extractablePower;
        private double energyDemand;

        private StorageEntry(ServiceStorageCache group, IAEPowerStorage storage, long registrationSequence,
                             double routingMaximumPower, int priority, AccessRestriction powerFlow,
                             boolean publicStorage) {
            this.group = group;
            this.storage = storage;
            this.registrationSequence = registrationSequence;
            this.routingMaximumPower = routingMaximumPower;
            this.priority = priority;
            this.powerFlow = powerFlow;
            this.publicStorage = publicStorage;
            this.allowExtraction = powerFlow.isAllowExtraction();
            this.allowInsertion = powerFlow.isAllowInsertion();
        }

        private void replaceSnapshot(double storedPower, double maximumPower, double extractablePower,
                                     double energyDemand) {
            this.group.owner.replaceSnapshotContribution(this, storedPower, maximumPower, extractablePower,
                energyDemand);
            this.storedPower = storedPower;
            this.maximumPower = maximumPower;
            this.extractablePower = extractablePower;
            this.energyDemand = energyDemand;
        }
    }

    /**
     * Allocation-free scaled double-double accumulator. Scaling keeps overflow recoverable while the low component
     * preserves contributions that would be rounded away by the high component.
     */
    private static final class CompensatedPowerSum {
        private boolean initialized;
        private int scaleExponent;
        private double high;
        private double low;

        private void replace(double previous, double current) {
            add(-previous);
            add(current);
        }

        private void add(double value) {
            if (value == 0) {
                return;
            }

            int valueExponent = Math.getExponent(Math.abs(value));
            if (!this.initialized) {
                this.initialized = true;
                this.scaleExponent = valueExponent;
                this.high = Math.scalb(value, -valueExponent);
                this.low = 0;
                return;
            }

            if (valueExponent > this.scaleExponent) {
                int shift = this.scaleExponent - valueExponent;
                this.high = Math.scalb(this.high, shift);
                this.low = Math.scalb(this.low, shift);
                this.scaleExponent = valueExponent;
            }

            addScaled(Math.scalb(value, -this.scaleExponent));
            normalizeScale();
        }

        private void addScaled(double value) {
            double sum = this.high + value;
            double virtualValue = sum - this.high;
            double error = (this.high - (sum - virtualValue)) + (value - virtualValue);
            double correctedLow = this.low + error;
            double correctedHigh = sum + correctedLow;
            this.low = correctedLow - (correctedHigh - sum);
            this.high = correctedHigh;
        }

        private void normalizeScale() {
            double magnitude = Math.max(Math.abs(this.high), Math.abs(this.low));
            if (magnitude == 0) {
                this.initialized = false;
                this.scaleExponent = 0;
                this.high = 0;
                this.low = 0;
                return;
            }

            int shift = Math.getExponent(magnitude);
            if (shift != 0) {
                this.high = Math.scalb(this.high, -shift);
                this.low = Math.scalb(this.low, -shift);
                this.scaleExponent += shift;
            }
        }

        private double value() {
            if (!this.initialized) {
                return 0;
            }

            double significand = this.high + this.low;
            if (significand <= 0) {
                return 0;
            }
            if (this.scaleExponent > Double.MAX_EXPONENT) {
                return Double.MAX_VALUE;
            }

            double result = Math.scalb(significand, this.scaleExponent);
            return Double.isFinite(result) ? result : Double.MAX_VALUE;
        }
    }
}
