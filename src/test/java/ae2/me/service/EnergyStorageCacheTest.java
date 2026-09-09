package ae2.me.service;

import ae2.api.config.AccessRestriction;
import ae2.api.config.Actionable;
import ae2.api.config.PowerMultiplier;
import ae2.api.networking.energy.IAEPowerStorage;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyStorageCacheTest {
    private static EnergyStorageGroup groupWith(IAEPowerStorage storage) {
        var group = new EnergyStorageGroup(new EnergyPowerStatistics());
        group.register(storage);
        return group;
    }

    @Test
    void ordinaryDrainAndRefillDoNotRebuildTotalsWithManyFullStorages() {
        var group = new EnergyStorageGroup(new EnergyPowerStatistics());
        var sources = new ObjectArrayList<TestPowerStorage>();
        for (int i = 0; i < 512; i++) {
            var storage = new TestPowerStorage("source", 100, 100, new ArrayList<>());
            sources.add(storage);
            group.register(storage);
        }
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        for (int tick = 0; tick < 100; tick++) {
            assertEquals(10, cache.extractPower(10, Actionable.MODULATE));
            assertEquals(10, cache.getEnergyDemand(1000));
            assertEquals(0, cache.injectPower(10, Actionable.MODULATE));
            assertEquals(0, cache.getEnergyDemand(1000));
            assertEquals(51200, cache.getStoredPower());
            assertEquals(51200, cache.getMaximumPower());
        }
        assertEquals(0, cache.getTotalsRebuildCount());
        for (int i = 1; i < sources.size(); i++) {
            assertEquals(1, sources.get(i).readCount);
        }
    }

    @Test
    void lastContributionResetsExactlyWithoutPrecisionRecovery() {
        var storage = new TestPowerStorage("only", 0, 0.3, new ArrayList<>());
        var group = groupWith(storage);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        for (int i = 0; i < 50; i++) {
            assertEquals(0, cache.injectPower(0.1, Actionable.MODULATE));
            assertEquals(0, cache.injectPower(cache.getEnergyDemand(1), Actionable.MODULATE));
            assertEquals(0, cache.getEnergyDemand(1));
            assertEquals(0.3, cache.extractPower(1, Actionable.MODULATE));
            assertEquals(0, cache.getStoredPower());
        }
        storage.maximum = 0;
        cache.refreshStorageCapacity(group, storage);
        assertEquals(0, cache.getMaximumPower());
        assertEquals(0, cache.getTotalsRebuildCount());
    }

    @Test
    void directCapabilitiesAvoidSimulationsAndKeepLimitsAndExternalChanges() {
        var storage = new DirectPowerStorage();
        storage.limit = 4;
        var group = groupWith(storage);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        for (int i = 0; i < 100; i++) {
            assertEquals(4, cache.extractPower(100, Actionable.MODULATE));
            assertEquals(96, cache.injectPower(100, Actionable.MODULATE));
            assertEquals(20, cache.getStoredPower());
        }
        storage.limit = 2;
        cache.refreshStorage(group, storage);
        assertEquals(2, cache.getEnergyDemand(100));
        assertEquals(2, cache.extractPower(100, Actionable.MODULATE));
        assertEquals(0, storage.simulations);
    }

    @Test
    void invalidDirectCapabilitiesAreIsolatedLikeInvalidSimulations() {
        double[] invalid = {Double.NaN, Double.POSITIVE_INFINITY, -1, 101};
        for (double result : invalid) {
            var bad = new TestPowerStorage("bad", 20, 100, new ArrayList<>()) {
                @Override
                public double getReceivableAEPower(double maximum) {
                    return result;
                }
            };
            var good = new TestPowerStorage("good", 40, 100, new ArrayList<>());
            var group = groupWith(bad);
            group.register(good);
            var cache = new EnergyStorageCache(ObjectArrayList.of(group));
            assertTrue(cache.isValid());
            assertEquals(40, cache.getStoredPower());
            assertEquals(60, cache.getEnergyDemand(1000));
            assertEquals(40, cache.extractPower(1000, Actionable.MODULATE));
        }
    }

    @Test
    void sharedReadsAndOperationsDoNotRescanOtherProviders() {
        var first = new TestPowerStorage("first", 50, 100, new ArrayList<>());
        var second = new TestPowerStorage("second", 50, 100, new ArrayList<>());
        var group = groupWith(first);
        group.register(second);
        assertEquals(0, first.readCount + second.readCount);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertEquals(2, first.readCount + second.readCount);
        for (int i = 0; i < 20; i++) {
            assertEquals(100, cache.getStoredPower());
            assertEquals(200, cache.getMaximumPower());
        }
        assertEquals(2, first.readCount + second.readCount);
        first.onExtract = () -> cache.refreshStorage(group, first);
        for (int i = 0; i < 20; i++) {
            assertEquals(1, cache.extractPower(1, Actionable.MODULATE));
        }
        assertEquals(80, cache.getStoredPower());
        assertEquals(1, second.readCount);
        assertEquals(21, first.readCount);
    }

    @Test
    void demandWithUnboundedRequestAndPerOperationLimitsStaysExact() {
        var storage = new TestPowerStorage("limited", 20, 100, new ArrayList<>());
        storage.limit = 4;
        var cache = new EnergyStorageCache(ObjectArrayList.of(groupWith(storage)));
        assertEquals(4, cache.getEnergyDemand(Double.MAX_VALUE));
        assertEquals(4, cache.extractPower(100, Actionable.SIMULATE));
        assertEquals(20, storage.current);
        assertEquals(96, cache.injectPower(100, Actionable.MODULATE));
        assertEquals(24, cache.getStoredPower());
        assertTrue(storage.simulations > 0);
    }

    @Test
    void invalidOperationResultsNeverInventTransferredPower() {
        double[] invalidResults = {Double.POSITIVE_INFINITY, Double.NaN, -1, 101};
        for (double invalidResult : invalidResults) {
            var storage = new TestPowerStorage("invalid", 100, 200, new ArrayList<>());
            var group = groupWith(storage);
            var extraction = new EnergyStorageCache(ObjectArrayList.of(group));
            storage.extractionResult = invalidResult;
            assertEquals(0, extraction.extractPower(100, Actionable.MODULATE));
            assertTrue(extraction.isValid());
            assertEquals(0, extraction.getStoredPower());
            storage.extractionResult = null;
            var injection = new EnergyStorageCache(ObjectArrayList.of(group));
            storage.injectionResult = invalidResult;
            assertEquals(100, injection.injectPower(100, Actionable.MODULATE));
            assertTrue(injection.isValid());
            assertEquals(0, injection.getEnergyDemand(100));
        }
    }

    @Test
    void unknownExternalChangesAreLazyAndRepeatedInvalidationsCoalesce() {
        var storage = new TestPowerStorage("external", 20, 100, new ArrayList<>());
        var group = groupWith(storage);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        storage.current = 30;
        for (int i = 0; i < 100; i++) {
            cache.refreshStorage(group, storage);
        }
        assertEquals(1, storage.readCount);
        assertEquals(30, cache.getStoredPower());
        assertEquals(2, storage.readCount);
        assertEquals(100, cache.getMaximumPower());
        assertEquals(2, storage.readCount);
    }

    @Test
    void interleavedExternalChangesReadOnlyTheirSourceAndPreserveContributionsAfterSorting() {
        var first = new TestPowerStorage("first", 20, 100, new ArrayList<>());
        var second = new TestPowerStorage("second", 30, 100, new ArrayList<>());
        second.priority = 10;
        var group = groupWith(first);
        group.register(second);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        cache.extractPower(1, Actionable.MODULATE);
        int secondReads = second.readCount;
        for (int i = 0; i < 20; i++) {
            first.current++;
            cache.refreshStorage(group, first);
            cache.refreshStorage(group, first);
            assertEquals(first.current + second.current, cache.getStoredPower());
            assertEquals(secondReads, second.readCount);
        }
        assertEquals(21, first.readCount);
        assertEquals(200, cache.getMaximumPower());
    }

    @Test
    void excessiveRequestsStopAtCachedAvailabilityForBothDirections() {
        var first = new TestPowerStorage("first", 10, 10, new ArrayList<>());
        var tail = new TestPowerStorage("tail", 0, 10, new ArrayList<>());
        var group = groupWith(first);
        group.register(tail);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        tail.onRead = () -> {
            throw new AssertionError("Exhausted tail must not be read");
        };
        assertEquals(10, cache.extractPower(Double.MAX_VALUE, Actionable.MODULATE));
        assertEquals(0, cache.getStoredPower());
        assertEquals(1, tail.readCount);

        first = new TestPowerStorage("first", 0, 10, new ArrayList<>());
        tail = new TestPowerStorage("tail", 10, 10, new ArrayList<>());
        group = groupWith(first);
        group.register(tail);
        cache = new EnergyStorageCache(ObjectArrayList.of(group));
        tail.onRead = () -> {
            throw new AssertionError("Full tail must not be read");
        };
        assertEquals(990, cache.injectPower(1000, Actionable.MODULATE));
        assertEquals(20, cache.getStoredPower());
        assertEquals(1, tail.readCount);
    }

    @Test
    void badReadIsIsolatedAndRetriedOnlyAfterItsOwnEvent() {
        var bad = new TestPowerStorage("bad", Double.NaN, 100, new ArrayList<>());
        var good = new TestPowerStorage("good", 40, 100, new ArrayList<>());
        var group = groupWith(bad);
        group.register(good);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertTrue(cache.isValid());
        assertEquals(40, cache.getStoredPower());
        assertEquals(100, cache.getMaximumPower());
        for (int i = 0; i < 10; i++) {
            assertEquals(1, cache.extractPower(1, Actionable.MODULATE));
        }
        assertEquals(1, bad.readCount);
        bad.current = 20;
        assertEquals(30, cache.getStoredPower());
        cache.refreshStorage(group, bad);
        assertEquals(50, cache.getStoredPower());
        assertEquals(2, bad.readCount);
        assertEquals(200, cache.getMaximumPower());
    }

    @Test
    void failedRefreshRemovesOnlyTheSourcesPreviousContribution() {
        var bad = new TestPowerStorage("bad", 30, 100, new ArrayList<>());
        var good = new TestPowerStorage("good", 40, 100, new ArrayList<>());
        var group = groupWith(bad);
        group.register(good);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        bad.onRead = () -> {
            throw new IllegalStateException("broken provider");
        };
        cache.refreshStorage(group, bad);
        assertEquals(40, cache.getStoredPower());
        assertEquals(100, cache.getMaximumPower());
        assertEquals(60, cache.getEnergyDemand(1000));
        assertEquals(1, good.readCount);
        assertEquals(40, cache.extractPower(1000, Actionable.MODULATE));
        assertEquals(0, cache.getStoredPower());
        assertEquals(2, bad.readCount);
    }

    @Test
    void invalidSimulationsAndOperationsDoNotBlockHealthySources() {
        for (int failure = 0; failure < 4; failure++) {
            var bad = new TestPowerStorage("bad", 30, 100, new ArrayList<>());
            var good = new TestPowerStorage("good", 40, 100, new ArrayList<>());
            var group = groupWith(bad);
            group.register(good);
            if (failure == 0) {
                bad.simulationResult = Double.POSITIVE_INFINITY;
            } else if (failure == 1) {
                bad.onRead = () -> {
                    throw new IllegalStateException("broken read");
                };
            } else if (failure == 2) {
                bad.extractionResult = Double.POSITIVE_INFINITY;
            } else {
                bad.onExtract = () -> {
                    throw new IllegalStateException("broken extraction");
                };
            }
            var cache = new EnergyStorageCache(ObjectArrayList.of(group));
            assertEquals(40, cache.extractPower(1000, Actionable.MODULATE));
            assertTrue(cache.isValid());
            assertEquals(0, cache.getStoredPower());
            assertEquals(100, cache.getMaximumPower());
            assertEquals(1, bad.readCount);
            assertEquals(100, cache.getEnergyDemand(1000));
        }
        var bad = new TestPowerStorage("bad", 0, 100, new ArrayList<>());
        bad.injectionResult = Double.NaN;
        var good = new TestPowerStorage("good", 0, 100, new ArrayList<>());
        var group = groupWith(bad);
        group.register(good);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertEquals(900, cache.injectPower(1000, Actionable.MODULATE));
        assertEquals(100, cache.getStoredPower());
        assertTrue(cache.isValid());
    }

    @Test
    void otherStorageEventDuringOperationStopsOldRouteButKeepsCompletedAmount() {
        var first = new TestPowerStorage("first", 10, 100, new ArrayList<>());
        var second = new TestPowerStorage("second", 40, 100, new ArrayList<>());
        var group = groupWith(first);
        group.register(second);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        first.onExtract = () -> cache.refreshStorage(group, second);
        assertEquals(10, cache.extractPower(50, Actionable.MODULATE));
        assertFalse(cache.isValid());
        assertEquals(40, second.current);
    }

    @Test
    void saturatedSharedTotalRecoversWhenOneStorageIsDrained() {
        var first = new TestPowerStorage("first", Double.MAX_VALUE, Double.MAX_VALUE, new ArrayList<>());
        var second = new TestPowerStorage("second", Double.MAX_VALUE, Double.MAX_VALUE, new ArrayList<>());
        var group = groupWith(first);
        group.register(second);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertEquals(Double.MAX_VALUE, cache.getStoredPower());
        assertEquals(Double.MAX_VALUE, cache.extractPower(Double.MAX_VALUE, Actionable.MODULATE));
        assertEquals(0, first.current);
        assertEquals(Double.MAX_VALUE, cache.getStoredPower());
        assertEquals(1, second.readCount);
    }

    @Test
    void cancellationRecoversSmallContributionsWithoutReadingOtherStorages() {
        var first = new TestPowerStorage("large", 1.0e16, 1.0e16, new ArrayList<>());
        var second = new TestPowerStorage("small", 1, 1, new ArrayList<>());
        var group = groupWith(first);
        group.register(second);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertEquals(1.0e16, cache.extractPower(1.0e16, Actionable.MODULATE));
        assertEquals(1, cache.getStoredPower());
        assertEquals(1, second.readCount);
        assertEquals(1, cache.extractPower(10, Actionable.SIMULATE));
        assertEquals(1, cache.extractPower(10, Actionable.MODULATE));
        assertTrue(cache.getTotalsRebuildCount() > 0);
    }

    @Test
    void failedPostOperationReadKeepsAcknowledgedPowerAndIsolatesOnlyItsSource() {
        var first = new TestPowerStorage("first", 10, 10, new ArrayList<>());
        var second = new TestPowerStorage("second", 20, 20, new ArrayList<>());
        var group = groupWith(first);
        group.register(second);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        first.onExtract = () -> first.onRead = () -> {
            throw new IllegalStateException("broken after extraction");
        };
        assertEquals(30, cache.extractPower(100, Actionable.MODULATE));
        assertTrue(cache.isValid());
        assertEquals(0, cache.getStoredPower());
        assertEquals(20, cache.getMaximumPower());
    }

    @Test
    void priorityAndAccessRestrictionsApplyToBothRoutingDirections() {
        var high = new TestPowerStorage("high", 10, 20, new ArrayList<>());
        var low = new TestPowerStorage("low", 10, 20, new ArrayList<>());
        var hidden = new TestPowerStorage("hidden", 100, 200, new ArrayList<>());
        high.priority = 5;
        low.priority = -5;
        hidden.publicStorage = false;
        var group = groupWith(low);
        group.register(high);
        group.register(hidden);
        var cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertEquals(20, cache.getStoredPower());
        assertEquals(40, cache.getMaximumPower());
        assertEquals(5, cache.extractPower(5, Actionable.MODULATE));
        assertEquals(5, high.current);
        assertEquals(0, cache.injectPower(5, Actionable.MODULATE));
        assertEquals(15, low.current);
        high.flow = AccessRestriction.WRITE;
        low.flow = AccessRestriction.READ;
        cache = new EnergyStorageCache(ObjectArrayList.of(group));
        assertEquals(15, cache.getStoredPower());
        assertEquals(5, cache.extractPower(5, Actionable.MODULATE));
        assertEquals(10, low.current);
        assertEquals(0, cache.injectPower(5, Actionable.MODULATE));
        assertEquals(10, high.current);
        assertEquals(10, cache.getStoredPower());
        assertEquals(100, hidden.current);
    }

    @Test
    void unregisterRetainsOtherOccurrencesAndRegistrationOrder() {
        var calls = new ArrayList<String>();
        var first = new TestPowerStorage("first", 10, 10, calls);
        var second = new TestPowerStorage("second", 10, 10, calls);
        var group = groupWith(first);
        group.register(second);
        group.register(first);
        group.unregister(first);
        var old = new ObjectArrayList<IAEPowerStorage>();
        group.appendStorages(old);
        group.unregister(first);
        group.register(first);
        var groups = new ObjectArrayList<EnergyStorageGroup>();
        groups.add(group);
        var cache = new EnergyStorageCache(groups);
        assertEquals(20, cache.extractPower(20, Actionable.MODULATE));
        assertEquals(List.of("second", "first"), calls);
        assertEquals(2, old.size());
        Assertions.assertSame(first, old.getFirst());
    }

    @Test
    void capacityRefreshReadsOnlyTargetAndLazilyChangesServiceRoutingOrder() {
        var extractionOrder = new ArrayList<String>();
        var firstStorage = new TestPowerStorage("first", 100, 100, extractionOrder);
        var secondStorage = new TestPowerStorage("second", 200, 200, extractionOrder);
        var firstGroup = groupWith(firstStorage);
        var secondGroup = groupWith(secondStorage);
        var groups = new ObjectArrayList<EnergyStorageGroup>();
        groups.add(firstGroup);
        groups.add(secondGroup);
        var cache = new EnergyStorageCache(groups);

        assertEquals(300, cache.getMaximumPower());
        cache.extractPower(1, Actionable.MODULATE);
        assertEquals(List.of("second"), extractionOrder);

        extractionOrder.clear();
        int secondReads = secondStorage.readCount;
        firstStorage.maximum = 300;
        assertTrue(cache.refreshStorageCapacity(firstGroup, firstStorage));
        assertEquals(500, cache.getMaximumPower());
        assertEquals(secondReads, secondStorage.readCount);

        assertEquals(1, cache.extractPower(1, Actionable.SIMULATE));
        assertEquals(List.of(), extractionOrder);
        cache.extractPower(1, Actionable.MODULATE);
        assertEquals(List.of("first"), extractionOrder);
    }

    @Test
    void capacityRefreshDuringStorageOperationInvalidatesTheGeneration() {
        var extractionOrder = new ArrayList<String>();
        var firstStorage = new TestPowerStorage("first", 100, 200, extractionOrder);
        var secondStorage = new TestPowerStorage("second", 100, 100, extractionOrder);
        var firstGroup = groupWith(firstStorage);
        var secondGroup = groupWith(secondStorage);
        var groups = new ObjectArrayList<EnergyStorageGroup>();
        groups.add(firstGroup);
        groups.add(secondGroup);
        var cache = new EnergyStorageCache(groups);
        var refreshAccepted = new AtomicBoolean(true);
        firstStorage.onExtract = () -> refreshAccepted.set(cache.refreshStorageCapacity(firstGroup, firstStorage));

        assertEquals(10, cache.extractPower(10, Actionable.MODULATE));

        assertFalse(refreshAccepted.get());
        assertFalse(cache.isValid());
        assertEquals(List.of("first"), extractionOrder);
    }

    @Test
    void capacityRefreshDuringValueReadInvalidatesTheRoute() {
        var storage = new TestPowerStorage("storage", 100, 100, new ArrayList<>());
        var group = groupWith(storage);
        var groups = new ObjectArrayList<EnergyStorageGroup>();
        groups.add(group);
        var cache = new EnergyStorageCache(groups);
        var nestedRefreshAccepted = new AtomicBoolean(true);
        storage.onRead = () -> nestedRefreshAccepted.set(cache.refreshStorageCapacity(group, storage));

        assertFalse(cache.refreshStorageCapacity(group, storage));

        assertFalse(nestedRefreshAccepted.get());
        assertFalse(cache.isValid());
    }

    @Test
    void replenishedPrefixRegainsItsOriginalExtractionAndInsertionOrder() {
        var calls = new ArrayList<String>();
        var first = new TestPowerStorage("first", 0, 200, calls);
        var second = new TestPowerStorage("second", 100, 100, calls);
        var firstGroup = groupWith(first);
        var secondGroup = groupWith(second);
        var groups = new ObjectArrayList<EnergyStorageGroup>();
        groups.add(firstGroup);
        groups.add(secondGroup);
        var cache = new EnergyStorageCache(groups);
        assertEquals(10, cache.extractPower(10, Actionable.MODULATE));
        assertEquals(List.of("second"), calls);
        first.current = 20;
        cache.refreshStorage(firstGroup, first);
        assertEquals(10, cache.extractPower(10, Actionable.MODULATE));
        assertEquals(List.of("second", "first"), calls);
        first.current = first.maximum;
        cache.refreshStorage(firstGroup, first);
        assertEquals(0, cache.injectPower(5, Actionable.MODULATE));
        assertEquals(95, second.current);
        first.current = 190;
        cache.refreshStorage(firstGroup, first);
        assertEquals(0, cache.injectPower(5, Actionable.MODULATE));
        assertEquals(195, first.current);
        assertEquals(95, second.current);
    }

    @Test
    void replenishedEntryWithinOneGroupRestoresPriority() {
        var calls = new ArrayList<String>();
        var first = new TestPowerStorage("first", 0, 100, calls);
        var second = new TestPowerStorage("second", 100, 100, calls);
        var group = groupWith(first);
        group.register(second);
        var groups = new ObjectArrayList<EnergyStorageGroup>();
        groups.add(group);
        var cache = new EnergyStorageCache(groups);
        cache.extractPower(10, Actionable.MODULATE);
        first.current = 10;
        cache.refreshStorage(group, first);
        cache.extractPower(5, Actionable.MODULATE);
        assertEquals(List.of("second", "first"), calls);
    }

    private static class TestPowerStorage implements IAEPowerStorage {
        private final String name;
        private final List<String> extractionOrder;
        int simulations;
        double limit = Double.MAX_VALUE;
        private double current;
        private double maximum;
        private int readCount;
        private Runnable onExtract;
        private Runnable onRead;
        private Double extractionResult;
        private Double injectionResult;
        private Double simulationResult;
        private int priority;
        private AccessRestriction flow = AccessRestriction.READ_WRITE;
        private boolean publicStorage = true;

        private TestPowerStorage(String name, double current, double maximum, List<String> extractionOrder) {
            this.name = name;
            this.current = current;
            this.maximum = maximum;
            this.extractionOrder = extractionOrder;
        }

        @Override
        public double injectAEPower(double amount, Actionable mode) {
            if (mode == Actionable.SIMULATE) {
                this.simulations++;
            }
            if (mode == Actionable.SIMULATE && this.simulationResult != null) {
                return this.simulationResult;
            }
            if (mode == Actionable.MODULATE && this.injectionResult != null) {
                return this.injectionResult;
            }
            double inserted = Math.min(this.limit, Math.min(amount, this.maximum - this.current));
            if (mode == Actionable.MODULATE) {
                this.current += inserted;
            }
            return amount - inserted;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier usePowerMultiplier) {
            if (mode == Actionable.SIMULATE) {
                this.simulations++;
            }
            if (mode == Actionable.SIMULATE && this.simulationResult != null) {
                return this.simulationResult;
            }
            if (mode == Actionable.MODULATE && this.extractionResult != null) {
                return this.extractionResult;
            }
            if (mode == Actionable.MODULATE) {
                this.extractionOrder.add(this.name);
            }
            if (mode == Actionable.MODULATE && this.onExtract != null) {
                this.onExtract.run();
            }
            double extracted = Math.min(this.limit, Math.min(amount, this.current));
            if (mode == Actionable.MODULATE) {
                this.current -= extracted;
            }
            return extracted;
        }

        @Override
        public double getAEMaxPower() {
            return this.maximum;
        }

        @Override
        public double getAECurrentPower() {
            this.readCount++;
            if (this.onRead != null) {
                this.onRead.run();
            }
            return this.current;
        }

        @Override
        public boolean isAEPublicPowerStorage() {
            return this.publicStorage;
        }

        @Override
        public AccessRestriction getPowerFlow() {
            return this.flow;
        }

        @Override
        public int getPriority() {
            return this.priority;
        }
    }

    private static final class DirectPowerStorage extends TestPowerStorage {
        private DirectPowerStorage() {
            super("direct", 20, 100, new ArrayList<>());
        }

        @Override
        public double getExtractableAEPower(double maximum) {
            return Math.min(maximum, this.limit);
        }

        @Override
        public double getReceivableAEPower(double maximum) {
            return Math.min(maximum, this.limit);
        }
    }
}
