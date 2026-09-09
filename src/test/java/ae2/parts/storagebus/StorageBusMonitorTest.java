package ae2.parts.storagebus;

import ae2.api.networking.GridFlags;
import ae2.api.networking.ticking.TickRateModulation;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.hooks.ticking.TickHandler;
import ae2.items.parts.PartItem;
import ae2.me.GridNode;
import ae2.me.service.StorageService;
import ae2.me.storage.NetworkStorage;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class StorageBusMonitorTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void overflowIsRejectedAndWrongThreadInvalidationReachesASleepingBus() throws InterruptedException {
        var key = AEItemKey.of(Items.APPLE);
        var source = new TestSource(key);
        var bus = new StorageBusPart.StorageBusInventory(source);
        var parent = new StorageService();
        parent.addGlobalStorageProvider(mounts -> mounts.mount(bus, 0));
        assertEquals(1, parent.getCachedInventory().get(key));
        source.listener.onStackChange(key, Long.MAX_VALUE);
        assertEquals(1, parent.getCachedInventory().get(key));
        assertEquals(2, source.scans);

        var notificationThread = new AtomicReference<Thread>();
        var notifications = new AtomicInteger();
        parent.getInventory().addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
            }

            public void onListUpdate() {
                notificationThread.set(Thread.currentThread());
                notifications.incrementAndGet();
            }
        }, null);
        var wrongThread = new Thread(() -> {
            source.amount = 8;
            source.listener.onStackChange(key, 7);
            source.listener.onListUpdate();
        });
        wrongThread.start();
        wrongThread.join();
        assertEquals(0, notifications.get());
        assertEquals(2, source.scans);
        TickHandler.instance().onServerEndTick(
            new TickEvent.ServerTickEvent(
                TickEvent.Phase.END));
        assertEquals(1, notifications.get());
        assertSame(Thread.currentThread(), notificationThread.get());
        assertEquals(8, parent.getCachedInventory().get(key));
        assertEquals(3, source.scans);
    }

    @Test
    void queuedOldDelegateCallbackDoesNotInvalidateReplacement() throws InterruptedException {
        var key = AEItemKey.of(Items.APPLE);
        var old = new TestSource(key);
        var replacement = new TestSource(key);
        var bus = new StorageBusPart.StorageBusInventory(old);
        var parent = new StorageService();
        parent.addGlobalStorageProvider(mounts -> mounts.mount(bus, 0));
        assertEquals(1, parent.getCachedInventory().get(key));
        var worker = new Thread(old.listener::onListUpdate);
        worker.start();
        worker.join();
        bus.setDelegate(replacement);
        assertEquals(1, parent.getCachedInventory().get(key));
        TickHandler.instance().onServerEndTick(
            new TickEvent.ServerTickEvent(
                TickEvent.Phase.END));
        assertEquals(1, parent.getCachedInventory().get(key));
        assertEquals(1, replacement.scans);
    }

    @Test
    void offlineTickDoesNotEvenResolveTheTarget() {
        var partItem = new PartItem<>(StorageBusPart.class, StorageBusPart::new);
        var targetReads = new AtomicInteger();
        var bus = new StorageBusPart(partItem) {
            @Override
            protected void updateTarget(boolean forceFullUpdate) {
                targetReads.incrementAndGet();
            }
        };
        var node = new GridNode(null, bus, (owner, changedNode) -> {
        },
            EnumSet.noneOf(GridFlags.class));
        for (int i = 0; i < 20; i++) {
            assertEquals(TickRateModulation.SLEEP, bus.tickingRequest(node, 1));
        }
        assertEquals(0, targetReads.get());
    }

    @Test
    void busBindingIsLazyAndParentFirstSubnetRefreshDoesNotInvalidateParentScan() {
        var key = AEItemKey.of(Items.APPLE);
        var scans = new AtomicInteger();
        var amount = new AtomicInteger(10);
        var child = new StorageService();
        child.addGlobalStorageProvider(mounts -> mounts.mount(new MEStorageMonitor() {
            public void getAvailableStacks(KeyCounter out) {
                scans.incrementAndGet();
                out.add(key, amount.get());
            }

            public ITextComponent getDescription() {
                return new TextComponentString("test");
            }

            public void addListener(MEStorageChangeListener listener, Object token) {
            }

            public void removeListener(MEStorageChangeListener listener) {
            }
        }, 0));
        var bus = new StorageBusPart.StorageBusInventory(child.getInventory());
        var parent = new StorageService();
        IStorageProvider provider = mounts -> mounts.mount(bus, 0);
        parent.addGlobalStorageProvider(provider);
        assertEquals(0, scans.get());
        assertEquals(10, parent.getCachedInventory().get(key));
        amount.set(20);
        child.invalidateCache();
        assertEquals(20, parent.getCachedInventory().get(key));
        parent.onServerEndTick();
        assertEquals(2, scans.get());
        parent.removeGlobalStorageProvider(provider);
        assertFalse(((NetworkStorage) child.getInventory()).hasListeners());
    }

    @Test
    void detachedBusRefreshesBeforeAStandaloneReadAndReplacementStaysLazy() {
        var key = AEItemKey.of(Items.APPLE);
        var amount = new AtomicInteger(10);
        var scans = new AtomicInteger();
        var sourceListener = new AtomicReference<MEStorageChangeListener>();
        var target = new MEStorageMonitor() {
            public void getAvailableStacks(KeyCounter out) {
                scans.incrementAndGet();
                out.add(key, amount.get());
            }

            public ITextComponent getDescription() {
                return new TextComponentString("test");
            }

            public void addListener(MEStorageChangeListener listener, Object token) {
                sourceListener.set(listener);
            }

            public void removeListener(MEStorageChangeListener listener) {
                sourceListener.set(null);
            }
        };
        var listener = new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
            }

            public void onListUpdate() {
            }
        };
        var bus = new StorageBusPart.StorageBusInventory(target);
        bus.addListener(listener, listener);
        assertEquals(10, bus.getAvailableStacks().get(key));
        var oldSourceListener = sourceListener.get();
        bus.removeListener(listener);
        amount.set(20);
        assertEquals(20, bus.getAvailableStacks().get(key));
        assertEquals(2, scans.get());
        bus.setDelegate(target);
        assertEquals(2, scans.get());
        assertEquals(20, bus.getAvailableStacks().get(key));
        assertEquals(3, scans.get());
        bus.addListener(listener, listener);
        assertEquals(20, bus.getAvailableStacks().get(key));
        oldSourceListener.onStackChange(key, 100);
        oldSourceListener.onListUpdate();
        assertEquals(20, bus.getAvailableStacks().get(key));
        assertEquals(4, scans.get());
    }

    private static final class TestSource implements MEStorageMonitor {
        private final AEKey key;
        private MEStorageChangeListener listener;
        private long amount = 1;
        private int scans;

        private TestSource(AEKey key) {
            this.key = key;
        }

        public void addListener(MEStorageChangeListener listener, Object token) {
            this.listener = listener;
        }

        public void removeListener(MEStorageChangeListener listener) {
            this.listener = null;
        }

        public void getAvailableStacks(KeyCounter out) {
            this.scans++;
            out.add(this.key, this.amount);
        }

        public ITextComponent getDescription() {
            return new TextComponentString("test");
        }
    }
}
