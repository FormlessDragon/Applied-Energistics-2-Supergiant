package ae2.me.storage;

import ae2.api.config.Actionable;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.me.service.StorageService;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkStorageTest {
    private static final AEKey KEY = new TestKey();
    private static final IActionSource SOURCE = IActionSource.empty();

    private static NetworkStorage createNetworkStorage() {
        return new NetworkStorage(KeyCounter::saturating, () -> {
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void failedListenerDoesNotInterruptAnOperationOrLaterParentNotifications(int failure) {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        target.available = 10;
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        var failures = new AtomicInteger();
        service.getInventory().addListener(new MEStorageChangeListener() {
            private void failAt(int stage) {
                if (failure == stage) {
                    failures.incrementAndGet();
                    throw new IllegalStateException("Broken listener stage " + stage);
                }
            }

            public boolean isValid(Object token) {
                failAt(0);
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                failAt(1);
            }

            public void onListUpdate() {
                failAt(2);
            }
        }, null);
        var parent = new StorageService();
        parent.addGlobalStorageProvider(mounts -> mounts.mount(service.getInventory(), 0));
        assertEquals(10, parent.getCachedInventory().get(KEY));
        var changes = new ArrayList<Long>();
        var lists = new AtomicInteger();
        service.getInventory().addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                changes.add(delta);
            }

            public void onListUpdate() {
                lists.incrementAndGet();
            }
        }, null);
        for (int i = 0; i < 2; i++) {
            target.onFirstExtract = () -> {
                target.available--;
                target.listener.onStackChange(KEY, -1);
                if (failure == 2) {
                    target.listener.onListUpdate();
                }
            };
            target.extracted = 1;
            assertEquals(1, service.getInventory().extract(KEY, 1, Actionable.MODULATE, SOURCE));
            assertEquals(9 - i, service.getCachedInventory().get(KEY));
            assertEquals(9 - i, parent.getCachedInventory().get(KEY));
        }
        assertEquals(List.of(-1L, -1L), changes);
        assertEquals(1, failures.get());
        if (failure == 2) {
            Assertions.assertTrue(lists.get() >= 2);
        }
    }

    @Test
    void insertionKeepsItsResultWhenAListenerThrowsAfterTheInventoryChanged() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        assertEquals(0, service.getCachedInventory().get(KEY));
        var observed = new AtomicInteger();
        service.getInventory().addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                throw new IllegalStateException("Broken observer");
            }

            public void onListUpdate() {
            }
        }, null);
        service.getInventory().addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                observed.addAndGet((int) delta);
            }

            public void onListUpdate() {
            }
        }, null);
        assertEquals(3, service.getInventory().insert(KEY, 3, Actionable.MODULATE, SOURCE));
        assertEquals(3, target.available);
        assertEquals(3, service.getCachedInventory().get(KEY));
        assertEquals(3, observed.get());
    }

    @Test
    void validationMayRemoveItselfWithoutReceivingTheCurrentNotification() {
        var network = createNetworkStorage();
        network.addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                network.removeListener(this);
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                throw new AssertionError("Removed registration");
            }

            public void onListUpdate() {
                throw new AssertionError("Removed registration");
            }
        }, null);
        network.postChange(KEY, 1);
        Assertions.assertFalse(network.hasListeners());
    }

    @Test
    void failedOldRegistrationDoesNotRemoveItsReplacementOrLosePendingListUpdate() {
        var invalidations = new AtomicInteger();
        var network = new NetworkStorage(KeyCounter::saturating, invalidations::incrementAndGet);
        var calls = new ArrayList<String>();
        network.addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                network.removeListener(this);
                network.addListener(this, null);
                network.postListUpdate();
                throw new IllegalStateException("Old registration failed after being replaced");
            }

            public void onListUpdate() {
                calls.add("replacement:list");
            }
        }, null);
        network.addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                calls.add("healthy:delta");
            }

            public void onListUpdate() {
                calls.add("healthy:list");
            }
        }, null);
        network.postChange(KEY, 1);
        assertEquals(List.of("healthy:delta", "healthy:list", "replacement:list"), calls);
        assertEquals(1, invalidations.get());
    }

    @Test
    void unmountsEveryIdentityOccurrenceWithoutChangingRemainingOrder() {
        var calls = new ArrayList<String>();
        var storage = createNetworkStorage();
        var repeated = new RecordingStorage("repeated", calls);
        var high = new RecordingStorage("high", calls);
        var appended = new RecordingStorage("appended", calls);
        var low = new RecordingStorage("low", calls);

        storage.mount(10, repeated);
        storage.mount(10, high);
        storage.mount(10, repeated);
        storage.mount(0, repeated);
        storage.mount(0, low);
        storage.unmount(repeated);
        storage.mount(10, appended);

        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);

        assertEquals(List.of("high", "appended", "low"), calls);
    }

    @Test
    void rawEnumerationCompactsTombstonesAndSkipsRemovedStorage() {
        var calls = new ArrayList<String>();
        var storage = createNetworkStorage();
        var first = new RecordingStorage("first", calls);
        var removed = new RecordingStorage("removed", calls);
        var last = new RecordingStorage("last", calls);

        storage.mount(0, first);
        storage.mount(0, removed);
        storage.mount(0, last);
        storage.unmount(removed);
        storage.getAvailableStacksRaw(KeyCounter.saturating());

        assertEquals(List.of("first:scan", "last:scan"), calls);
    }

    @Test
    void queuedMountThenUnmountRemovesTheQueuedOccurrence() {
        var calls = new ArrayList<String>();
        var storage = createNetworkStorage();
        var trigger = new RecordingStorage("trigger", calls);
        var tail = new RecordingStorage("tail", calls);
        var queued = new RecordingStorage("queued", calls);
        trigger.onFirstExtract = () -> {
            storage.mount(0, queued);
            storage.unmount(queued);
        };

        storage.mount(0, trigger);
        storage.mount(0, tail);
        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);
        calls.clear();
        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);

        assertEquals(List.of("trigger", "tail"), calls);
    }

    @Test
    void queuedUnmountThenMountLeavesTheNewOccurrenceAtTheEnd() {
        var calls = new ArrayList<String>();
        var storage = createNetworkStorage();
        var trigger = new RecordingStorage("trigger", calls);
        var tail = new RecordingStorage("tail", calls);
        trigger.onFirstExtract = () -> {
            storage.unmount(trigger);
            storage.mount(0, trigger);
        };

        storage.mount(0, trigger);
        storage.mount(0, tail);
        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);
        calls.clear();
        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);

        assertEquals(List.of("tail", "trigger"), calls);
    }

    @Test
    void compactionKeepsOrderAndUpdatesIdentityPositions() {
        var calls = new ArrayList<String>();
        var storage = createNetworkStorage();
        var first = new RecordingStorage("first", calls);
        var removedFirst = new RecordingStorage("removed-first", calls);
        var middle = new RecordingStorage("middle", calls);
        var removedSecond = new RecordingStorage("removed-second", calls);
        var appended = new RecordingStorage("appended", calls);

        storage.mount(0, first);
        storage.mount(0, removedFirst);
        storage.mount(0, middle);
        storage.mount(0, removedSecond);
        storage.unmount(removedFirst);
        storage.unmount(removedSecond);
        storage.mount(0, appended);
        storage.unmount(middle);

        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);

        assertEquals(List.of("first", "appended"), calls);
    }

    @Test
    void queuedUnmountRemovesAllIdentityOccurrencesAcrossPriorities() {
        var calls = new ArrayList<String>();
        var storage = createNetworkStorage();
        var repeated = new RecordingStorage("repeated", calls);
        var tail = new RecordingStorage("tail", calls);
        repeated.onFirstExtract = () -> storage.unmount(repeated);

        storage.mount(10, repeated);
        storage.mount(10, repeated);
        storage.mount(0, repeated);
        storage.mount(0, tail);
        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);
        calls.clear();
        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);

        assertEquals(List.of("tail"), calls);
    }

    @Test
    void queuedChangesAreFlushedWhenMountedStorageThrows() {
        var calls = new ArrayList<String>();
        var storage = createNetworkStorage();
        var throwing = new RecordingStorage("throwing", calls);
        var replacement = new RecordingStorage("replacement", calls);
        throwing.onFirstExtract = () -> {
            storage.unmount(throwing);
            storage.mount(20, replacement);
            throw new IllegalStateException("test");
        };

        storage.mount(0, throwing);
        assertThrows(IllegalStateException.class,
            () -> storage.extract(KEY, 1, Actionable.MODULATE, SOURCE));
        calls.clear();
        storage.extract(KEY, 1, Actionable.MODULATE, SOURCE);

        assertEquals(List.of("replacement"), calls);
    }

    @Test
    void cachedEnumerationDoesNotVisitMountedStorage() {
        var calls = new ArrayList<String>();
        var supplierCalls = new AtomicInteger();
        var storage = new NetworkStorage(() -> {
            supplierCalls.incrementAndGet();
            return KeyCounter.saturating();
        }, () -> {
        });
        storage.mount(0, new RecordingStorage("mounted", calls));

        storage.getAvailableStacks(KeyCounter.saturating());

        assertEquals(1, supplierCalls.get());
        assertEquals(List.of(), calls);
    }

    @Test
    void parentFirstRebuildReadsThreeLevelSubnetOnlyOnce() {
        var leaf = new StorageService();
        var middle = new StorageService();
        var root = new StorageService();
        var target = new RecordingStorage("leaf", new ArrayList<>());
        target.available = 10;
        leaf.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        middle.addGlobalStorageProvider(mounts -> mounts.mount(leaf.getInventory(), 0));
        root.addGlobalStorageProvider(mounts -> mounts.mount(middle.getInventory(), 0));
        assertEquals(10, root.getCachedInventory().get(KEY));
        target.available = 25;
        leaf.invalidateCache();
        assertEquals(25, root.getCachedInventory().get(KEY));
        root.onServerEndTick();
        middle.onServerEndTick();
        leaf.onServerEndTick();
        assertEquals(25, root.getCachedInventory().get(KEY));
        assertEquals(2, target.scans);
        target.available = 0;
        target.listener.onStackChange(KEY, -25);
        assertEquals(0, root.getCachedInventory().get(KEY));
        assertEquals(2, target.scans);
    }

    @Test
    void removedSourceCannotChangeCacheAfterTheSameStorageIsRemounted() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        target.available = 10;
        IStorageProvider provider = mounts -> mounts.mount(target, 0);
        service.addGlobalStorageProvider(provider);
        assertEquals(10, service.getCachedInventory().get(KEY));
        var previousListener = target.listener;
        service.removeGlobalStorageProvider(provider);
        service.addGlobalStorageProvider(provider);
        assertEquals(10, service.getCachedInventory().get(KEY));
        previousListener.onStackChange(KEY, 100);
        previousListener.onListUpdate();
        service.onServerEndTick();
        assertEquals(10, service.getCachedInventory().get(KEY));
        assertEquals(2, target.scans);
    }

    @Test
    void listenerRemovalDuringDispatchSkipsOldRegistrationAndDefersNewOne() {
        var network = createNetworkStorage();
        var calls = new ArrayList<String>();
        MEStorageChangeListener second = new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onListUpdate() {
                calls.add("second");
            }

            public void onStackChange(AEKey what, long delta) {
            }
        };
        MEStorageChangeListener first = new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onListUpdate() {
                calls.add("first");
                network.removeListener(second);
                network.addListener(second, null);
                network.removeListener(this);
            }

            public void onStackChange(AEKey what, long delta) {
            }
        };
        network.addListener(first, null);
        network.addListener(second, null);
        network.postListUpdate();
        assertEquals(List.of("first"), calls);
        network.postListUpdate();
        assertEquals(List.of("first", "second"), calls);
        network.removeListener(second);
        Assertions.assertFalse(network.hasListeners());
    }

    @Test
    void serviceRetainsInvalidationRaisedDuringEnumeration() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        target.available = 10;
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        assertEquals(10, service.getCachedInventory().get(KEY));

        target.available = 20;
        target.onFirstScan = service::invalidateCache;
        service.invalidateCache();
        assertEquals(10, service.getCachedInventory().get(KEY));
        assertEquals(20, service.getCachedInventory().get(KEY));
        service.getCachedInventory();
        assertEquals(3, target.scans);
    }

    @Test
    void serviceRebuildIncludesMountQueuedDuringEnumeration() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        var added = new RecordingStorage("added", new ArrayList<>());
        target.available = 10;
        added.available = 7;
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        assertEquals(10, service.getCachedInventory().get(KEY));
        target.onFirstScan = () -> service.addGlobalStorageProvider(mounts -> mounts.mount(added, 0));
        service.invalidateCache();
        assertEquals(10, service.getCachedInventory().get(KEY));
        assertEquals(17, service.getCachedInventory().get(KEY));
        assertEquals(1, added.scans);
    }

    @Test
    void synchronousOperationDeltaUpdatesCacheWithoutRescanOrDoubleCounting() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        target.available = 10;
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        assertEquals(10, service.getCachedInventory().get(KEY));
        target.onFirstExtract = () -> {
            target.available--;
            Assertions.assertTrue(target.listener.isValid(target.token));
            target.listener.onStackChange(KEY, -1);
            assertEquals(9, service.getCachedInventory().get(KEY));
        };
        target.extracted = 1;
        assertEquals(1, service.getInventory().extract(KEY, 1, Actionable.MODULATE, SOURCE));
        service.onServerEndTick();
        assertEquals(9, service.getCachedInventory().get(KEY));
        assertEquals(1, target.scans);
    }

    @Test
    void rejectedReentrantScanNeverReplacesTheServiceCache() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        target.available = 10;
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        assertEquals(10, service.getCachedInventory().get(KEY));
        target.onFirstExtract = () -> {
            target.available = 9;
            service.invalidateCache();
            assertEquals(10, service.getCachedInventory().get(KEY));
        };
        service.getInventory().extract(KEY, 1, Actionable.MODULATE, SOURCE);
        assertEquals(9, service.getCachedInventory().get(KEY));
        assertEquals(2, target.scans);
    }

    @Test
    void invalidationListenerCanReadWithoutRecursivelyRebuilding() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        target.available = 10;
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        assertEquals(10, service.getCachedInventory().get(KEY));
        var notifications = new AtomicInteger();
        service.getInventory().addListener(new MEStorageChangeListener() {
            @Override
            public boolean isValid(Object token) {
                return token == service;
            }

            @Override
            public void onStackChange(AEKey what, long delta) {
            }

            @Override
            public void onListUpdate() {
                notifications.incrementAndGet();
                service.getCachedInventory();
            }
        }, service);
        target.onFirstScan = service::invalidateCache;
        service.invalidateCache();
        service.getCachedInventory();
        assertEquals(3, target.scans);
        Assertions.assertTrue(notifications.get() > 0);
        assertEquals(10, service.getCachedInventory().get(KEY));
    }

    @Test
    void failedScanKeepsPreviousContentsAndCanRetry() {
        var service = new StorageService();
        var target = new RecordingStorage("target", new ArrayList<>());
        target.available = 10;
        service.addGlobalStorageProvider(mounts -> mounts.mount(target, 0));
        assertEquals(10, service.getCachedInventory().get(KEY));
        target.available = 12;
        target.onFirstScan = () -> {
            throw new IllegalStateException("Test scan failure");
        };
        service.invalidateCache();
        assertEquals(10, service.getCachedInventory().get(KEY));
        assertEquals(12, service.getCachedInventory().get(KEY));
    }

    private static final class RecordingStorage implements MEStorageMonitor {
        private final String name;
        private final List<String> calls;
        private Runnable onFirstExtract;
        private Runnable onFirstScan;
        private long available;
        private int scans;
        private long extracted;
        private MEStorageChangeListener listener;
        private Object token;

        private RecordingStorage(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.MODULATE) {
                this.available += amount;
                this.listener.onStackChange(what, amount);
            }
            return amount;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            this.calls.add(this.name);
            var callback = this.onFirstExtract;
            this.onFirstExtract = null;
            if (callback != null) {
                callback.run();
            }
            return this.extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            this.calls.add(this.name + ":scan");
            this.scans++;
            out.add(KEY, this.available);
            var callback = this.onFirstScan;
            this.onFirstScan = null;
            if (callback != null) {
                callback.run();
            }
        }

        @Override
        public ITextComponent getDescription() {
            return new TextComponentString(this.name);
        }

        @Override
        public void addListener(MEStorageChangeListener listener, Object verificationToken) {
            this.listener = listener;
            this.token = verificationToken;
        }

        @Override
        public void removeListener(MEStorageChangeListener listener) {
            if (this.listener == listener) {
                this.listener = null;
                this.token = null;
            }
        }
    }

    private static final class TestKey extends AEKey {
        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public NBTTagCompound toTag() {
            return new NBTTagCompound();
        }

        @Override
        public Object getPrimaryKey() {
            return this;
        }

        @Override
        public ResourceLocation getId() {
            return null;
        }

        @Override
        public void writeToPacket(PacketBuffer data) {
        }

        @Override
        public Object getReadOnlyStack() {
            return this;
        }

        @Override
        protected ITextComponent computeDisplayName() {
            return new TextComponentString("test");
        }

        @Override
        public NBTBase get(String componentId) {
            return null;
        }
    }

}
