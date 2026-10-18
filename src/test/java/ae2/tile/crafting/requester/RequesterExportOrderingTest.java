package ae2.tile.crafting.requester;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.core.AEConfig;
import ae2.tile.crafting.TileRequester;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.IFMLSidedHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequesterExportOrderingTest {
    private static Object previousSide;
    private static AEItemKey apple;

    @BeforeAll
    static void setup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        previousSide = f.get(FMLCommonHandler.instance());
        if (previousSide == null) f.set(FMLCommonHandler.instance(), Proxy.newProxyInstance(
            IFMLSidedHandler.class.getClassLoader(), new Class<?>[]{IFMLSidedHandler.class},
            (p, m, a) -> m.getName().equals("getSide") ? Side.SERVER : null));
        Bootstrap.register();
        AEConfig.init();
        apple = AEItemKey.of(Items.APPLE);
    }

    @AfterAll
    static void cleanup() throws Exception {
        var f = FMLCommonHandler.class.getDeclaredField("sidedDelegate");
        f.setAccessible(true);
        f.set(FMLCommonHandler.instance(), previousSide);
    }

    @Test
    void synchronousMonitorNotificationDoesNotReaddPendingInsertion() {
        var tracker = new RequesterStorageTracker(1, ignored -> {
        });
        var requester = new TestRequester(tracker, true);
        tracker.addPending(0, apple, 10);
        StatusState.EXPORT.handle(requester, 0);
        assertEquals(0, tracker.getBufferedAmount(0, apple));
        assertEquals(0, tracker.getPendingInsertionAmount(0, apple));
        assertEquals(10, tracker.getKnownAmount(0, apple));
    }

    @Test
    void delayedNotificationStillUsesPendingGuardUntilAuthorityArrives() {
        var tracker = new RequesterStorageTracker(1, ignored -> {
        });
        var requester = new TestRequester(tracker, false);
        tracker.addPending(0, apple, 10);
        StatusState.EXPORT.handle(requester, 0);
        assertEquals(10, tracker.getPendingInsertionAmount(0, apple));
        tracker.onStackChange(apple, 10);
        assertEquals(0, tracker.getPendingInsertionAmount(0, apple));
        assertEquals(10, tracker.getKnownAmount(0, apple));
    }

    @Test
    void unrelatedNotificationAfterMatchingObservationDoesNotRestorePending() {
        var tracker = new RequesterStorageTracker(1, ignored -> {
        });
        var carrot = AEItemKey.of(Items.CARROT);
        tracker.addPending(0, apple, 10);
        tracker.setKnownAmount(0, carrot, 1);
        var requester = new TileRequester() {
            @Override
            public ItemStack getItemFromTile() {
                return ItemStack.EMPTY;
            }

            @Override
            public RequesterStorageTracker getStorageTracker() {
                return tracker;
            }

            @Override
            public long insert(AEKey key, long amount) {
                tracker.onStackChange(key, 4);
                tracker.onStackChange(carrot, 2);
                return 4;
            }
        };
        StatusState.EXPORT.handle(requester, 0);
        assertEquals(6, tracker.getBufferedAmount(0, apple));
        assertEquals(4, tracker.getKnownAmount(0, apple));
        assertEquals(0, tracker.getPendingInsertionAmount(0, apple));
    }

    @Test
    void failedInsertReleasesObservationScope() throws Exception {
        var tracker = new RequesterStorageTracker(1, ignored -> {
        });
        tracker.addPending(0, apple, 10);
        var requester = new TileRequester() {
            @Override
            public ItemStack getItemFromTile() {
                return ItemStack.EMPTY;
            }

            @Override
            public RequesterStorageTracker getStorageTracker() {
                return tracker;
            }

            @Override
            public long insert(AEKey key, long amount) {
                throw new IllegalStateException("insert fixture");
            }
        };
        assertThrows(IllegalStateException.class, () -> StatusState.EXPORT.handle(requester, 0));
        var field = RequesterStorageTracker.class.getDeclaredField("exportObservations");
        field.setAccessible(true);
        assertNull(((Object[]) field.get(tracker))[0]);
        assertEquals(10, tracker.getBufferedAmount(0, apple));
    }

    private static final class TestRequester extends TileRequester {
        private final RequesterStorageTracker tracker;
        private final boolean synchronous;

        TestRequester(RequesterStorageTracker tracker, boolean synchronous) {
            this.tracker = tracker;
            this.synchronous = synchronous;
        }

        @Override
        public ItemStack getItemFromTile() {
            return ItemStack.EMPTY;
        }

        @Override
        public RequesterStorageTracker getStorageTracker() {
            return tracker;
        }

        @Override
        public long insert(AEKey key, long amount) {
            if (synchronous) tracker.onStackChange(key, amount);
            return amount;
        }
    }
}
