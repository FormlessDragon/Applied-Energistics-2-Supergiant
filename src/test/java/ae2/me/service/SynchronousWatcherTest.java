package ae2.me.service;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IGridService;
import ae2.api.networking.IStackWatcher;
import ae2.api.networking.crafting.ICraftingWatcherNode;
import ae2.api.networking.energy.IEnergyService;
import ae2.api.networking.energy.IEnergyWatcher;
import ae2.api.networking.energy.IEnergyWatcherNode;
import ae2.api.networking.events.GridEvent;
import ae2.api.networking.storage.IStorageWatcherNode;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.KeyCounter;
import ae2.api.storage.IStorageProvider;
import ae2.api.storage.MEStorageChangeListener;
import ae2.api.storage.MEStorageMonitor;
import ae2.hooks.ticking.TickHandler;
import ae2.me.GridNode;
import ae2.me.energy.EnergyThreshold;
import ae2.me.energy.EnergyWatcher;
import com.google.gson.stream.JsonWriter;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SynchronousWatcherTest {
    private static AEKey key;

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
        key = AEItemKey.of(Items.APPLE);
    }

    @Test
    void wrongThreadStorageEventsNotifyOnServerQueueAndDetachedSourcesAreIgnored() throws InterruptedException {
        var service = new StorageService();
        var source = new Source();
        IStorageProvider provider = mounts -> mounts.mount(source, 0);
        service.addGlobalStorageProvider(provider);
        service.getCachedInventory();
        var notifications = new AtomicInteger();
        Thread ownerThread = Thread.currentThread();
        service.getInventory().addListener(new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                assertSame(ownerThread, Thread.currentThread());
            }

            public void onListUpdate() {
                assertSame(ownerThread, Thread.currentThread());
                notifications.incrementAndGet();
            }
        }, null);
        var worker = new Thread(() -> source.change(5));
        worker.start();
        worker.join();
        assertEquals(0, notifications.get());
        TickHandler.instance().onServerEndTick(
            new TickEvent.ServerTickEvent(
                TickEvent.Phase.END));
        assertEquals(1, notifications.get());
        assertEquals(5, service.getCachedInventory().get(key));
        worker = new Thread(source.listener::onListUpdate);
        worker.start();
        worker.join();
        service.removeGlobalStorageProvider(provider);
        assertEquals(0, service.getCachedInventory().get(key));
        int beforeDrain = notifications.get();
        TickHandler.instance().onServerEndTick(
            new TickEvent.ServerTickEvent(
                TickEvent.Phase.END));
        assertEquals(beforeDrain, notifications.get());
    }

    @Test
    void storageUnsubscriptionDuringDispatchSkipsRemainingOldRegistrations() {
        var storage = new StorageService();
        var source = new Source();
        storage.addGlobalStorageProvider(mounts -> mounts.mount(source, 0));
        storage.getCachedInventory();
        var hosts = new ArrayList<Host>();
        var calls = new ArrayList<AEKey>();
        for (int i = 0; i < 3; i++) {
            var host = new Host();
            host.callback = () -> {
                calls.add(key);
                for (int j = 0; j < hosts.size(); j++) {
                    storage.removeNode(hosts.get(j).node);
                }
            };
            hosts.add(host);
            host.node.addService(IStorageWatcherNode.class, host);
            storage.addNode(host.node, null);
        }
        source.change(1);
        assertEquals(List.of(key), calls);
        assertEquals(1, storage.getCachedInventory().get(key));
        source.change(1);
        assertEquals(1, calls.size());
    }

    @Test
    void nestedCraftingDispatchPreservesOuterBufferAndChecksNewSubscriptions() {
        var crafting = new CraftingService(new EmptyGrid(), new StorageService(), null);
        var calls = new ArrayList<String>();
        var first = new Host();
        var second = new Host();
        first.node.addService(ICraftingWatcherNode.class, first);
        second.node.addService(ICraftingWatcherNode.class, second);
        crafting.addNode(first.node, null);
        crafting.addNode(second.node, null);
        // Targeted listeners are collected before watch-all listeners.
        second.watcher.remove(key);
        second.watcher.setWatchAll(true);
        first.callback = () -> {
            calls.add("outer");
            first.watcher.reset();
            crafting.notifyCraftingWatchers(key, false);
            crafting.removeNode(second.node);
        };
        second.callback = () -> calls.add("nested");
        crafting.notifyCraftingWatchers(key, true);
        assertEquals(List.of("outer", "nested"), calls);
        crafting.notifyCraftingWatchers(key, false);
        assertEquals(2, calls.size());
        crafting.removeNode(first.node);
    }

    @Test
    void energyThresholdRemovalAndNestedDispatchDoNotReuseOuterEntries() {
        var energy = new EnergyService(null, null);
        var calls = new ArrayList<String>();
        var watchers = new EnergyWatcher[2];
        watchers[0] = new EnergyWatcher(energy, new IEnergyWatcherNode() {
            public void updateWatcher(IEnergyWatcher watcher) {
            }

            public void onThresholdPass(IEnergyService service) {
                calls.add("outer");
                watchers[0].reset();
                energy.notifyThresholds(new EnergyThreshold(0, Integer.MIN_VALUE),
                    new EnergyThreshold(20, Integer.MAX_VALUE));
                watchers[1].destroy();
            }
        });
        watchers[1] = new EnergyWatcher(energy, new IEnergyWatcherNode() {
            public void updateWatcher(IEnergyWatcher watcher) {
            }

            public void onThresholdPass(IEnergyService service) {
                calls.add("nested");
            }
        });
        watchers[0].add(1);
        watchers[1].add(2);
        energy.notifyThresholds(new EnergyThreshold(0, Integer.MIN_VALUE),
            new EnergyThreshold(20, Integer.MAX_VALUE));
        assertEquals(List.of("outer", "nested"), calls);
    }

    private static final class Host implements IStorageWatcherNode, ICraftingWatcherNode {
        private final GridNode node = new GridNode(null, this, (owner, gridNode) -> {
        },
            EnumSet.noneOf(GridFlags.class));
        private IStackWatcher watcher;
        private Runnable callback;

        public void updateWatcher(IStackWatcher watcher) {
            this.watcher = watcher;
            watcher.add(key);
        }

        public void onStackChange(AEKey what, long amount) {
            this.callback.run();
        }

        public void onRequestChange(AEKey what) {
            this.callback.run();
        }

        public void onCraftableChange(AEKey what) {
            this.callback.run();
        }
    }

    private static final class Source implements MEStorageMonitor {
        private MEStorageChangeListener listener;
        private long amount;

        private void change(long delta) {
            this.amount += delta;
            this.listener.onStackChange(key, delta);
        }

        public void addListener(MEStorageChangeListener listener, Object token) {
            this.listener = listener;
        }

        public void removeListener(MEStorageChangeListener listener) {
            this.listener = null;
        }

        public void getAvailableStacks(KeyCounter out) {
            out.add(key, this.amount);
        }

        public ITextComponent getDescription() {
            return new TextComponentString("test");
        }
    }

    private static final class EmptyGrid implements IGrid {
        public <C extends IGridService> C getService(Class<C> type) {
            throw new UnsupportedOperationException();
        }

        public <T extends GridEvent> T postEvent(T event) {
            return event;
        }

        public Iterable<Class<?>> getMachineClasses() {
            return List.of();
        }

        public Iterable<IGridNode> getMachineNodes(Class<?> type) {
            return List.of();
        }

        public <T> Set<T> getMachines(Class<T> type) {
            return Set.of();
        }

        public <T> Set<T> getActiveMachines(Class<T> type) {
            return Set.of();
        }

        public Iterable<IGridNode> getNodes() {
            return List.of();
        }

        public boolean isEmpty() {
            return true;
        }

        public IGridNode getPivot() {
            throw new UnsupportedOperationException();
        }

        public int size() {
            return 0;
        }

        public void export(JsonWriter writer) {
        }
    }
}
