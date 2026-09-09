package ae2.me.storage;

import ae2.api.behaviors.ExternalStorageMonitor;
import ae2.api.config.Actionable;
import ae2.api.config.StorageFilter;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.AEKeyType;
import ae2.api.storage.MEStorageChangeListener;
import ae2.hooks.ticking.TickHandler;
import ae2.me.service.StorageService;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CompositeStorageTest {
    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void removalFailuresDoNotSkipOtherSourcesAndDetachedCallbacksCannotReachComposite(boolean throwBeforeRemoval) {
        var items = new MonitoredHandler();
        var fluids = new MonitoredTank();
        var composite = new CompositeStorage(Map.of(
            AEKeyType.items(), ExternalStorageFacade.of(items),
            AEKeyType.fluids(), ExternalStorageFacade.of(fluids)));
        var notifications = new AtomicInteger();
        MEStorageChangeListener listener = new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                notifications.incrementAndGet();
            }

            public void onListUpdate() {
                notifications.incrementAndGet();
            }
        };
        composite.addListener(listener, null);
        var oldItems = items.listener;
        var oldFluids = fluids.listener;
        items.throwBeforeRemoval = throwBeforeRemoval;
        items.failRemoval = true;
        fluids.onRemove = () -> {
            assertFalse(oldItems.isValid(oldItems));
            assertFalse(oldFluids.isValid(oldFluids));
            throw new IllegalStateException("Fluid monitor failed during removal");
        };

        assertDoesNotThrow(() -> composite.removeListener(listener));
        assertEquals(1, items.removals);
        assertEquals(1, fluids.removals);
        assertFalse(oldItems.isValid(oldItems));
        assertFalse(oldFluids.isValid(oldFluids));
        assertNull(fluids.listener);
        if (!throwBeforeRemoval) {
            assertNull(items.listener);
        }
        composite.removeListener(listener);
        assertEquals(1, items.removals);
        assertEquals(1, fluids.removals);

        var replacement = new MonitoredHandler();
        composite.setStorages(Map.of(AEKeyType.items(), ExternalStorageFacade.of(replacement)));
        composite.addListener(listener, null);
        var key = AEItemKey.of(Items.APPLE);
        assertEquals(1, composite.getAvailableStacks().get(key));
        oldItems.onStackChange(key, 100);
        oldFluids.onListUpdate();
        assertEquals(0, notifications.get());
        assertEquals(1, composite.getAvailableStacks().get(key));
        replacement.listener.onStackChange(key, 2);
        assertEquals(3, composite.getAvailableStacks().get(key));
        assertEquals(1, notifications.get());
        composite.removeListener(listener);
        assertNull(replacement.listener);
    }

    @Test
    void replacingSourcesSurvivesRemovalFailureAndReentrantBindingIsRejected() {
        var items = new MonitoredHandler();
        var fluids = new MonitoredTank();
        var composite = new CompositeStorage(Map.of(
            AEKeyType.items(), ExternalStorageFacade.of(items),
            AEKeyType.fluids(), ExternalStorageFacade.of(fluids)));
        var updates = new AtomicInteger();
        MEStorageChangeListener listener = new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
            }

            public void onListUpdate() {
                updates.incrementAndGet();
            }
        };
        composite.addListener(listener, null);
        items.failRemoval = true;
        fluids.onRemove = () -> assertThrows(IllegalStateException.class,
            () -> composite.setStorages(Map.of()));
        var replacement = new MonitoredHandler();
        assertDoesNotThrow(() -> composite.setStorages(Map.of(AEKeyType.items(), ExternalStorageFacade.of(replacement))));
        assertEquals(1, items.removals);
        assertEquals(1, fluids.removals);
        assertEquals(1, replacement.registrations);
        assertEquals(1, updates.get());
        composite.removeListener(listener);
        assertNull(replacement.listener);
    }

    @Test
    void sameHandlerViewDoesNotRebindOrScanAndContentEventsStayIncremental() {
        var handler = new MonitoredHandler();
        var composite = new CompositeStorage(Map.of(AEKeyType.items(), ExternalStorageFacade.of(handler)));
        var notifications = new AtomicInteger();
        MEStorageChangeListener listener = new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                notifications.addAndGet((int) delta);
            }

            public void onListUpdate() {
                fail("Unchanged view must not invalidate content");
            }
        };
        assertEquals(0, handler.registrations);
        assertEquals(0, handler.reads);
        composite.addListener(listener, null);
        assertEquals(1, handler.registrations);
        assertEquals(0, handler.reads);
        assertEquals(1, composite.getAvailableStacks().get(AEItemKey.of(Items.APPLE)));
        int reads = handler.reads;
        composite.setStorages(Map.of(AEKeyType.items(), ExternalStorageFacade.of(handler)));
        composite.onTick();
        assertEquals(1, handler.registrations);
        assertEquals(reads, handler.reads);
        handler.listener.onStackChange(AEItemKey.of(Items.APPLE), 2);
        assertEquals(3, composite.getAvailableStacks().get(AEItemKey.of(Items.APPLE)));
        assertEquals(2, notifications.get());
        assertEquals(reads, handler.reads);
        var oldListener = handler.listener;
        composite.removeListener(listener);
        assertNull(handler.listener);
        assertEquals(1, handler.removals);
        oldListener.onStackChange(AEItemKey.of(Items.APPLE), 100);
        composite.addListener(listener, null);
        assertEquals(1, composite.getAvailableStacks().get(AEItemKey.of(Items.APPLE)));
        oldListener.onListUpdate();
        assertEquals(1, composite.getAvailableStacks().get(AEItemKey.of(Items.APPLE)));
    }

    @Test
    void standaloneReadsDoNotReuseAStaleEventCache() {
        var handler = new MonitoredHandler();
        var composite = new CompositeStorage(Map.of(AEKeyType.items(), ExternalStorageFacade.of(handler)));
        var key = AEItemKey.of(Items.APPLE);

        assertEquals(1, composite.getAvailableStacks().get(key));
        handler.setStackInSlot(0, new ItemStack(Items.APPLE, 2));
        assertEquals(2, composite.getAvailableStacks().get(key));
        var source = IActionSource.empty();
        assertEquals(1, composite.extract(key, 1, Actionable.MODULATE, source));
        assertEquals(1, composite.getAvailableStacks().get(key));
        assertEquals(2, composite.insert(key, 2, Actionable.MODULATE, source));
        assertEquals(3, composite.getAvailableStacks().get(key));
        assertEquals(0, handler.registrations);
    }

    @Test
    void asynchronousExternalInvalidationReachesTheNetworkOnTheServerQueue() throws InterruptedException {
        var handler = new MonitoredHandler();
        var composite = new CompositeStorage(Map.of(AEKeyType.items(), ExternalStorageFacade.of(handler)));
        var parent = new StorageService();
        parent.addGlobalStorageProvider(mounts -> mounts.mount(composite, 0));
        var key = AEItemKey.of(Items.APPLE);
        assertEquals(1, parent.getCachedInventory().get(key));
        int reads = handler.reads;
        var worker = new Thread(() -> {
            handler.setStackInSlot(0, new ItemStack(Items.APPLE, 4));
            handler.listener.onListUpdate();
            handler.listener.onStackChange(key, 3);
        });
        worker.start();
        worker.join();
        assertEquals(reads, handler.reads);
        TickHandler.instance().onServerEndTick(
            new TickEvent.ServerTickEvent(
                TickEvent.Phase.END));
        assertEquals(4, parent.getCachedInventory().get(key));
        int refreshedReads = handler.reads;
        assertTrue(refreshedReads > reads);
        assertEquals(4, parent.getCachedInventory().get(key));
        assertEquals(refreshedReads, handler.reads);
    }

    @Test
    void positiveDeltaOverflowInvalidatesInsteadOfSaturating() {
        var handler = new MonitoredHandler();
        var composite = new CompositeStorage(Map.of(AEKeyType.items(), ExternalStorageFacade.of(handler)));
        var key = AEItemKey.of(Items.APPLE);
        var listUpdates = new AtomicInteger();
        MEStorageChangeListener listener = new MEStorageChangeListener() {
            public boolean isValid(Object token) {
                return true;
            }

            public void onStackChange(AEKey what, long delta) {
                fail("Invalid delta must not be published");
            }

            public void onListUpdate() {
                listUpdates.incrementAndGet();
            }
        };

        composite.addListener(listener, listener);
        assertEquals(1, composite.getAvailableStacks().get(key));
        handler.listener.onStackChange(key, Long.MAX_VALUE);

        assertEquals(1, listUpdates.get());
        assertEquals(1, composite.getAvailableStacks().get(key));
    }

    private static final class MonitoredTank extends FluidTank implements ExternalStorageMonitor {
        private MEStorageChangeListener listener;
        private int removals;
        private Runnable onRemove;

        private MonitoredTank() {
            super(1000);
        }

        public void addListener(StorageFilter filter, MEStorageChangeListener listener, Object token) {
            assertNull(this.listener);
            this.listener = listener;
        }

        public void removeListener(MEStorageChangeListener listener) {
            assertSame(this.listener, listener);
            this.removals++;
            this.listener = null;
            if (this.onRemove != null) {
                this.onRemove.run();
            }
        }
    }

    private static final class MonitoredHandler extends ItemStackHandler implements ExternalStorageMonitor {
        private int reads;
        private int registrations;
        private int removals;
        private MEStorageChangeListener listener;
        private boolean throwBeforeRemoval;
        private boolean failRemoval;

        private MonitoredHandler() {
            super(1);
            setStackInSlot(0, new ItemStack(Items.APPLE));
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            this.reads++;
            return super.getStackInSlot(slot);
        }

        public void addListener(StorageFilter filter, MEStorageChangeListener listener, Object token) {
            assertNull(this.listener);
            this.listener = listener;
            this.registrations++;
        }

        public void removeListener(MEStorageChangeListener listener) {
            assertSame(this.listener, listener);
            this.removals++;
            if (this.failRemoval && this.throwBeforeRemoval) {
                throw new IllegalStateException("Item monitor refused removal");
            }
            this.listener = null;
            if (this.failRemoval) {
                throw new IllegalStateException("Item monitor failed after removal");
            }
        }
    }
}
